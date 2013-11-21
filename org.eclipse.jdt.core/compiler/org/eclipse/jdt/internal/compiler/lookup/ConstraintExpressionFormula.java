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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.ast.ConditionalExpression;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.LambdaExpression;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.ReferenceExpression;
import org.eclipse.jdt.internal.compiler.ast.ReturnStatement;
import org.eclipse.jdt.internal.compiler.ast.Statement;

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
		if (this.right.isProperType()) {
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
			if (this.left instanceof MessageSend) {
				MessageSend messageSend = (MessageSend) this.left;
				MethodBinding method = messageSend.binding;

				// Invocation Applicability Inference: 18.5.1
				// TODO(stephan): may not need all argument types (only last one used for varargs)
				Expression[] arguments = messageSend.arguments;
				TypeBinding[] argumentTypes = arguments == null ? Binding.NO_PARAMETERS : new TypeBinding[arguments.length];
				for (int i = 0; i < argumentTypes.length; i++)
					argumentTypes[i] = arguments[i].resolvedType;
				inferInvocationApplicability(inferenceContext, method, argumentTypes);
				// TODO(stephan): do we need InferenceContext18.purgeInstantiations() here, too?
				
				if (!inferPolyInvocationType(inferenceContext, messageSend, this.right, method))
					return FALSE;
				return null; // already incorporated
			} else if (this.left instanceof ConditionalExpression) {
				InferenceContext18.missingImplementation("NYI");
			} else if (this.left instanceof LambdaExpression) {
				InferenceContext18.missingImplementation("NYI");
			} else if (this.left instanceof ReferenceExpression) {
				if (this.right.getSingleAbstractMethod(inferenceContext.scope) == null)
					return FALSE;
				InferenceContext18.missingImplementation("NYI: reference expression");
			}
		}
		return FALSE;
	}
	
	static void inferInvocationApplicability(InferenceContext18 inferenceContext, MethodBinding method, TypeBinding[] arguments) 
	{
		// 18.5.1
		TypeVariableBinding[] typeVariables = method.typeVariables;
		TypeBinding[] parameters = method.parameters;
		InferenceVariable[] inferenceVariables = inferenceContext.createInitialBoundSet(typeVariables); // creates initial bound set B

		// check if varargs need special treatment:
		int paramLength = method.parameters.length;
		boolean passThrough = false;
		TypeBinding varArgsType = null;
		if (method.isVarargs()) {
			int varArgPos = paramLength-1;
			varArgsType = method.parameters[varArgPos];
			// FIXME(stephan): not sanctioned by spec 0.6.3 but fixes regression in GenericTypeTest.test0952:
			if (paramLength == arguments.length) {
				TypeBinding lastType = arguments[varArgPos];
				if (lastType == TypeBinding.NULL
						|| (varArgsType.dimensions() == lastType.dimensions()
						&& lastType.isCompatibleWith(varArgsType)))
					passThrough = true; // pass directly as-is
			}
			//
		}
		inferenceContext.createInitialConstraintsForParameters(parameters, method.isVarargs(), passThrough, varArgsType);
		inferenceContext.addThrowsContraints(typeVariables, inferenceVariables, method.thrownExceptions);
	}

	static boolean inferPolyInvocationType(InferenceContext18 inferenceContext, InvocationSite invocationSite, TypeBinding targetType, MethodBinding method) 
				throws InferenceFailureException 
	{
		TypeBinding[] typeArguments = invocationSite.genericTypeArguments();
		if (method.typeVariables != Binding.NO_TYPE_VARIABLES && typeArguments == null) {
			if (method.typeVariables != Binding.NO_TYPE_VARIABLES) {
				// invocation type inference (18.5.2):
				if (method.returnType == TypeBinding.VOID && !method.isConstructor()) // FIXME: 2nd part not sanctioned by the spec!
					throw new InferenceFailureException("expression has no value");

				ParameterizedTypeBinding parameterizedType = parameterizedWithWildcard(method.returnType);
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
					TypeBinding thetaR = inferenceContext.substitute(method.returnType);
					if (thetaR instanceof InferenceVariable) {
						TypeBinding wrapper = inferenceContext.currentBounds.findWrapperTypeBound((InferenceVariable)thetaR);
						if (wrapper != null) {
							if (!inferenceContext.reduceAndIncorporate(new ConstraintTypeFormula(thetaR, wrapper, ReductionResult.SAME))
								|| !inferenceContext.reduceAndIncorporate(new ConstraintTypeFormula(wrapper, targetType, ReductionResult.COMPATIBLE)))
								return false;
						}
					}
				}

				ConstraintTypeFormula newConstraint = new ConstraintTypeFormula(inferenceContext.substitute(method.returnType), targetType, COMPATIBLE);
				if (!inferenceContext.reduceAndIncorporate(newConstraint))
					return false;
			} else {
				throw new IllegalStateException("Method as PolyExpression must be generic");
			}
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
					Statement body = lambda.body;
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
		StringBuffer buf = new StringBuffer().append('⟨');
		this.left.printExpression(4, buf);
		buf.append(relationToString(this.relation));
		buf.append(this.right.readableName()).append('⟩');
		return buf.toString();
	}
}
