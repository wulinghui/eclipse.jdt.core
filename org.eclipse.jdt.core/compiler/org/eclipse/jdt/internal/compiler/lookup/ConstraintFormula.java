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
import java.util.List;
import java.util.Set;

/**
 * Implementation of 18.1.2 in JLS8
 */
abstract class ConstraintFormula extends ReductionResult {

	static final List EMPTY_VARIABLE_LIST = Collections.EMPTY_LIST;

	public abstract Object reduce(InferenceContext18 inferenceContext) throws InferenceFailureException;

	/** 5.3: compatibility check which includes the option of boxing/unboxing. */
	protected boolean isCompatibleWithInLooseInvocationContext(TypeBinding one, TypeBinding two, InferenceContext18 context) {
		if (one.isCompatibleWith(two, context.scope))
			return true;
		if (one.isBaseType()) {
			if (one != TypeBinding.NULL && !two.isBaseType())
				return context.environment.computeBoxingType(one) != one; //$IDENTITY-COMPARISON$ just checking if boxing could help
		} else if (two.isBaseType() && two != TypeBinding.NULL) {
			return context.environment.computeBoxingType(two) != two; //$IDENTITY-COMPARISON$ just checking if boxing could help
		}
		return false;
	}

	Collection inputVariables(InferenceContext18 context) {
		return EMPTY_VARIABLE_LIST;
	}
	
	Collection outputVariables(InferenceContext18 context) {
		Set variables = new HashSet();
		this.right.collectInferenceVariables(variables);
		variables.removeAll(inputVariables(context));
		return variables;
	}
}
