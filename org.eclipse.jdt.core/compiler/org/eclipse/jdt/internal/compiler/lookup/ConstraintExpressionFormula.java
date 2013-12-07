/*******************************************************************************
 * Copyright (c) 2013 GK Software AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * This is an implementation of an early-draft specification developed under the Java
 * Community Process (JCP) and is made available for testing and evaluation purposes
 * only. The code is not compatible with any specification of the JCP.
 *
 * Contributors:
 *     Stephan Herrmann - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.compiler.lookup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.ConditionalExpression;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.Invocation;
import org.eclipse.jdt.internal.compiler.ast.LambdaExpression;
import org.eclipse.jdt.internal.compiler.ast.ReferenceExpression;
import org.eclipse.jdt.internal.compiler.ast.ReturnStatement;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.lookup.InferenceContext18.InvocationRecord;

/**
 * Implementation of 18.1.2 in JLS8, case:
 * <ul>
 * <li>Expression -> T</li>
 * </ul>
 */
class ConstraintExpressionFormula extends ConstraintFormula {
	Expression left;

	ConstraintExpressionFormula(Expression expression, TypeBinding type, int relation) {
		this.left = expression;
		this.right = type;
		this.relation = relation;
	}
	
	public Object reduce(InferenceContext18 inferenceContext) throws InferenceFailureException {
		// JLS 18.2.1
		if (this.right.isProperType(true)) {
			TypeBinding exprType = this.left.resolvedType;
			if (exprType == null || !exprType.isValidBinding())
				return FALSE;
			if (isCompatibleWithInLooseInvocationContext(exprType, this.right, inferenceContext))
				return TRUE;
			return FALSE;
		}
		if (!this.left.isPolyExpression()) {
			TypeBinding exprType = this.left.resolvedType;
			if (exprType == null || !exprType.isValidBinding())
				return FALSE;
			return new ConstraintTypeFormula(exprType, this.right, COMPATIBLE);
		} else {
			// shapes of poly expressions (18.2.1)
			// - parenthesized expression : these are transparent in our AST
			if (this.left instanceof Invocation) {
				Invocation invocation = (Invocation) this.left;
				// ignore previous (inner) inference result and do a fresh start:
				MethodBinding method = invocation.binding().original();
				InvocationRecord prevInvocation = inferenceContext.enterPolyInvocation(invocation, invocation.arguments());

				// Invocation Applicability Inference: 18.5.1
				try {
					Expression[] arguments = invocation.arguments();
					TypeBinding[] argumentTypes = arguments == null ? Binding.NO_PARAMETERS : new TypeBinding[arguments.length];
					for (int i = 0; i < argumentTypes.length; i++)
						argumentTypes[i] = arguments[i].resolvedType;
					int checkType = (invocation.inferenceKind() != 0) ? invocation.inferenceKind() : InferenceContext18.CHECK_LOOSE;
					inferInvocationApplicability(inferenceContext, method, argumentTypes, checkType); // FIXME 3 phases?
					
					if (!inferPolyInvocationType(inferenceContext, invocation, this.right, method))
						return FALSE;
					return null; // already incorporated
				} finally {
					inferenceContext.leavePolyInvocation(prevInvocation);
				}
			} else if (this.left instanceof ConditionalExpression) {
				ConditionalExpression conditional = (ConditionalExpression) this.left;
				return new ConstraintFormula[] {
					new ConstraintExpressionFormula(conditional.valueIfTrue, this.right, this.relation),
					new ConstraintExpressionFormula(conditional.valueIfFalse, this.right, this.relation)
				};
			} else if (this.left instanceof LambdaExpression) {
				LambdaExpression lambda = (LambdaExpression) this.left;
				Scope scope = inferenceContext.scope;
				TypeBinding t = this.right;
				if (!t.isFunctionalInterface(scope))
					return FALSE;
				MethodBinding functionType = t.getSingleAbstractMethod(scope);
				if (functionType == null)
					return FALSE;
				TypeBinding[] parameters = functionType.parameters;
				if (parameters.length != lambda.arguments().length)
					return FALSE;
				if (lambda.argumentsTypeElided())
					for (int i = 0; i < parameters.length; i++)
						if (!parameters[i].isProperType(true))
							return FALSE;
				// FIXME: force shape analysis:
				lambda.isCompatibleWith(t, scope);
				if (functionType.returnType == TypeBinding.VOID) {
					if (!lambda.isVoidCompatible())
						return FALSE;
				} else {
					if (!lambda.isValueCompatible())
						return FALSE;
				}
				List result = new ArrayList();
				if (!lambda.argumentsTypeElided()) {
					Argument[] arguments = lambda.arguments();
					for (int i = 0; i < parameters.length; i++)
						result.add(new ConstraintTypeFormula(parameters[i], arguments[i].type.resolvedType, SAME));
				}
				if (functionType.returnType != TypeBinding.VOID) {
					TypeBinding r = functionType.returnType;
					if (lambda.body() instanceof Expression) {
						result.add(new ConstraintExpressionFormula((Expression)lambda.body(), r, COMPATIBLE));
					} else {
						Expression[] exprs = lambda.resultExpressions();
						for (int i = 0; i < exprs.length; i++)
							result.add(new ConstraintExpressionFormula(exprs[i], r, COMPATIBLE));
					}
				}
				if (result.size() == 0)
					return TRUE;
				return result.toArray(new ConstraintFormula[result.size()]);
			} else if (this.left instanceof ReferenceExpression) {
				return reduceReferenceExpressionCompatibility((ReferenceExpression) this.left, inferenceContext);
			}
		}
		return FALSE;
	}

	private Object reduceReferenceExpressionCompatibility(ReferenceExpression reference, InferenceContext18 inferenceContext) {
		TypeBinding t = this.right;
		if (t.isProperType(true))
			throw new IllegalStateException("Should not reach here with T being a proper type");
		if (!t.isFunctionalInterface(inferenceContext.scope))
			return FALSE;
		MethodBinding functionType = t.getSingleAbstractMethod(inferenceContext.scope);
		if (functionType == null)
			return FALSE;
		// TODO: check strategy for: potentially-applicable method for the method reference when targeting T (15.28.1),
		reference.resolveTypeExpecting(reference.enclosingScope, t);
		MethodBinding potentiallyApplicable = reference.binding;
		if (potentiallyApplicable == null)
			return FALSE;
		if (reference.isExactMethodReference()) {
			List /*<ConstraintFormula>*/ newConstraints = new ArrayList();
			TypeBinding[] p = functionType.parameters;
			int n = p.length;
			TypeBinding[] pPrime = potentiallyApplicable.parameters;
			int k = pPrime.length;
			int offset = 0;
			if (n == k+1) {
				newConstraints.add(new ConstraintTypeFormula(p[0], reference.receiverType, COMPATIBLE)); // 2nd arg: "ReferenceType"
				offset = 1;
			}
			for (int i = offset; i < n; i++)
				newConstraints.add(new ConstraintTypeFormula(p[i], pPrime[i-offset], COMPATIBLE));
			TypeBinding r = functionType.returnType;
			if (r != TypeBinding.VOID) {
				TypeBinding rAppl = potentiallyApplicable.returnType;
				if (rAppl == TypeBinding.VOID)
					return FALSE;
				TypeBinding rPrime = rAppl.capture(inferenceContext.scope, 14); // FIXME capture position??
				newConstraints.add(new ConstraintTypeFormula(rPrime, r, COMPATIBLE));
			}
			return newConstraints.toArray(new ConstraintFormula[newConstraints.size()]);
		} else { // inexact
			int n = functionType.parameters.length;
			for (int i = 0; i < n; i++)
				if (!functionType.parameters[i].isProperType(true))
					return FALSE;
			InferenceContext18.missingImplementation("NYI: inexact method reference");
			// FIXME: Otherwise, a search for a compile-time declaration is performed, as defined in 15.28.1 .....
		}
		return FALSE;
	}

	static void inferInvocationApplicability(InferenceContext18 inferenceContext, MethodBinding method, TypeBinding[] arguments, int checkType) 
	{
		// 18.5.1
		TypeVariableBinding[] typeVariables = method.typeVariables;
		TypeBinding[] parameters = method.parameters;
		InferenceVariable[] inferenceVariables = inferenceContext.createInitialBoundSet(typeVariables); // creates initial bound set B

		// check if varargs need special treatment:
		int paramLength = method.parameters.length;
		TypeBinding varArgsType = null;
		if (method.isVarargs()) {
			int varArgPos = paramLength-1;
			varArgsType = method.parameters[varArgPos];
		}
		inferenceContext.createInitialConstraintsForParameters(parameters, checkType==InferenceContext18.CHECK_VARARG, varArgsType, method);
		inferenceContext.addThrowsContraints(typeVariables, inferenceVariables, method.thrownExceptions);
	}

	static boolean inferPolyInvocationType(InferenceContext18 inferenceContext, InvocationSite invocationSite, TypeBinding targetType, MethodBinding method) 
				throws InferenceFailureException 
	{
		TypeBinding[] typeArguments = invocationSite.genericTypeArguments();
		boolean isGenericMethod = method.original().typeVariables != Binding.NO_TYPE_VARIABLES;
		if (isGenericMethod) {
			if (typeArguments == null) {
				// invocation type inference (18.5.2):
				TypeBinding returnType = method.isConstructor() ? method.declaringClass : method.returnType;
				if (returnType == TypeBinding.VOID)
					throw new InferenceFailureException("expression has no value");

				ParameterizedTypeBinding parameterizedType = parameterizedWithWildcard(returnType);
				if (parameterizedType != null) {
					TypeBinding[] arguments = parameterizedType.arguments;
					InferenceVariable[] betas = inferenceContext.addTypeVariableSubstitutions(arguments);
					TypeBinding gbeta = inferenceContext.environment.createParameterizedType(
							parameterizedType.genericType(), betas, parameterizedType.enclosingType(), parameterizedType.getTypeAnnotations());
					inferenceContext.currentBounds.captures.put(gbeta, parameterizedType);
					ConstraintTypeFormula newConstraint = new ConstraintTypeFormula(gbeta, targetType, COMPATIBLE);
					if (!inferenceContext.reduceAndIncorporate(newConstraint))
						return false;
				}

				if (targetType.isBaseType()) {
					TypeBinding thetaR = inferenceContext.substitute(returnType);
					if (thetaR instanceof InferenceVariable) {
						TypeBinding wrapper = inferenceContext.currentBounds.findWrapperTypeBound((InferenceVariable)thetaR);
						if (wrapper != null) {
							if (!inferenceContext.reduceAndIncorporate(new ConstraintTypeFormula(thetaR, wrapper, ReductionResult.SAME))
								|| !inferenceContext.reduceAndIncorporate(new ConstraintTypeFormula(wrapper, targetType, ReductionResult.COMPATIBLE)))
								return false;
						}
					}
				}

				ConstraintTypeFormula newConstraint = new ConstraintTypeFormula(inferenceContext.substitute(returnType), targetType, COMPATIBLE);
				if (!inferenceContext.reduceAndIncorporate(newConstraint))
					return false;
			}
		} else {
			throw new IllegalStateException("Method as PolyExpression must be generic");
		}
		return true;
	}

	private static ParameterizedTypeBinding parameterizedWithWildcard(TypeBinding returnType) {
		if (!(returnType instanceof ParameterizedTypeBinding))
			return null;
		ParameterizedTypeBinding parameterizedType = (ParameterizedTypeBinding) returnType;
		TypeBinding[] arguments = parameterizedType.arguments;
		for (int i = 0; i < arguments.length; i++) {
			if (arguments[i].isWildcard())
				return parameterizedType;
		}
		return null;
	}

	Collection inputVariables(final InferenceContext18 context) {
		// from 18.5.2.
		if (this.left instanceof LambdaExpression) {
			if (this.right instanceof InferenceVariable) {
				return Collections.singletonList(this.right);
			}
			if (this.right.isFunctionalInterface(context.scope)) {
				LambdaExpression lambda = (LambdaExpression) this.left;
				MethodBinding sam = this.right.getSingleAbstractMethod(context.scope); // TODO derive with target type?
				final Set variables = new HashSet();
				if (lambda.argumentsTypeElided()) {
					// i)
					int len = sam.parameters.length;
					for (int i = 0; i < len; i++) {
						sam.parameters[i].collectInferenceVariables(variables);
					}
				}
				if (sam.returnType != TypeBinding.VOID) {
					// ii)
					final TypeBinding r = sam.returnType;
					Statement body = lambda.body();
					if (body instanceof Expression) {
						variables.addAll(new ConstraintExpressionFormula((Expression) body, r, COMPATIBLE).inputVariables(context));
					} else {
						// TODO: should I use LambdaExpression.resultExpressions? (is currently private).
						body.traverse(new ASTVisitor() {
							public boolean visit(ReturnStatement returnStatement, BlockScope scope) {
								variables.addAll(new ConstraintExpressionFormula(returnStatement.expression, r, COMPATIBLE).inputVariables(context));
								return false;
							}
						}, (BlockScope)null);
					}
				}
				return variables;
			}
		} else if (this.left instanceof ReferenceExpression) {
			if (this.right instanceof InferenceVariable) {
				return Collections.singletonList(this.right);
			}
			if (this.right.isFunctionalInterface(context.scope) && !this.left.isExactMethodReference()) {
				MethodBinding sam = this.right.getSingleAbstractMethod(context.scope);
				final Set variables = new HashSet();
				int len = sam.parameters.length;
				for (int i = 0; i < len; i++) {
					sam.parameters[i].collectInferenceVariables(variables);
				}
				return variables;
			}			
		} else if (this.left instanceof ConditionalExpression && this.left.isPolyExpression()) {
			ConditionalExpression expr = (ConditionalExpression) this.left;
			Set variables = new HashSet();
			variables.addAll(new ConstraintExpressionFormula(expr.valueIfTrue, this.right, COMPATIBLE).inputVariables(context));
			variables.addAll(new ConstraintExpressionFormula(expr.valueIfFalse, this.right, COMPATIBLE).inputVariables(context));
			return variables;
		}
		return EMPTY_VARIABLE_LIST;
	}

	// debugging:
	public String toString() {
		StringBuffer buf = new StringBuffer().append(LEFT_ANGLE_BRACKET);
		this.left.printExpression(4, buf);
		buf.append(relationToString(this.relation));
		appendTypeName(buf, this.right);
		buf.append(RIGHT_ANGLE_BRACKET);
		return buf.toString();
	}
}
