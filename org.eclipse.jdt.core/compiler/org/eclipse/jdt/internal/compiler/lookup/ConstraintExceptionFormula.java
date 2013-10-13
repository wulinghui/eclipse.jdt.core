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
import org.eclipse.jdt.internal.compiler.ast.ReferenceExpression;

/**
 * Constraint formula expressing that a given expression must have an exception type.
 * <ul>
 * <li>Expression contains<sub>throws</sub> T</li>
 * </ul>
 */
public class ConstraintExceptionFormula extends ConstraintFormula {

	Expression left;
	
	public ConstraintExceptionFormula(Expression left, TypeBinding type) {
		this.left = left;
		this.right = type;
		this.relation = EXCEPTIONS_CONTAINED;
	}
	
	public Object reduce(InferenceContext18 inferenceContext) {
		// JSL 18.2.5
		if (this.left instanceof LambdaExpression || this.left instanceof ReferenceExpression) {		
			InferenceContext18.missingImplementation("NYI");
		} else if (this.left.isPolyExpression()) {
			// parenthesized: transparent in our AST
			if (this.left instanceof ConditionalExpression) {
				InferenceContext18.missingImplementation("NYI");				
			}
		}
		return TRUE;
	}

}
