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

import org.eclipse.jdt.internal.compiler.ast.ConditionalExpression;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.LambdaExpression;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.ReferenceExpression;

/**
 * Implementation of 18.1.2 in JLS8, cases:
 * <ul>
 * <li>Expression -> T</li>
 * <li>Expression contains<sub>throws</sub> T</li>
 * </ul>
 */
class ConstraintExpressionFormula extends ConstraintFormula {
	Expression left;
	boolean isDelayed; // TODO defined in spec but never referenced

	ConstraintExpressionFormula(Expression expression, TypeBinding type, int relation) {
		this.left = expression;
		this.right = type;
		this.relation = relation;
	}
	
	public Object reduce(InferenceContext18 inferenceContext) {
		TypeBinding exprType = this.left.resolvedType;
		if (exprType == null || !exprType.isValidBinding())
			return FALSE;
		if (this.right.isProperType()) {
			if (exprType.isCompatibleWith(this.right, inferenceContext.scope))
				return TRUE;
			return FALSE;
		} else if (!this.left.isPolyExpression) {
			return new ConstraintTypeFormula(exprType, this.right, COMPATIBLE);
		} else {
			// shapes of poly expressions (18.2.1)
			// - parenthesized expression : these are transparent in our AST
			if (this.left instanceof MessageSend) {
				MethodBinding method = ((MessageSend) this.left).binding.original(); // FIXME calling original() to revert old-style inference
				if (method.typeVariables != Binding.NO_TYPE_VARIABLES) {
					// invocation type inference (18.5.2):
					if (method.returnType == TypeBinding.VOID)
						return FALSE; // error: poly expression has no value
					inferenceContext.addTypeVariableSubstitutions(method.typeVariables);
					// TODO handle primitive types
					return new ConstraintTypeFormula(inferenceContext.substitute(method.returnType), this.right, COMPATIBLE);
				}
			} else if (this.left instanceof ConditionalExpression) {
				// TODO
			} else if (this.left instanceof LambdaExpression) {
				// TODO
			} else if (this.left instanceof ReferenceExpression) {
				// TODO
			}
		}
		return FALSE;
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
