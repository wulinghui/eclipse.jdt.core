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

import org.eclipse.jdt.core.compiler.CharOperation;

/**
 * Implementation of 18.1.1 in JLS8
 */
public class InferenceVariable extends ReferenceBinding {

	TypeBinding typeParameter;
	int rank; // TODO: remove?
	
	public InferenceVariable(TypeBinding typeParameter, int variableRank) {
		this.typeParameter = typeParameter;
		this.sourceName = CharOperation.concat(typeParameter.shortReadableName(), Integer.toString(variableRank).toCharArray(), '#');
		this.rank = variableRank;
	}

	public InferenceVariable(int expressionRank, int variableRank) {
		this.sourceName = CharOperation.concat("expr#".toCharArray(), Integer.toString(variableRank).toCharArray()); //$NON-NLS-1$
		this.rank = variableRank;
	}

	public char[] constantPoolName() {
		throw new UnsupportedOperationException();
	}

	public PackageBinding getPackage() {
		throw new UnsupportedOperationException();
	}

	public boolean isCompatibleWith(TypeBinding right, Scope scope) {
		return false;
	}

	boolean isProperType() {
		return false;
	}

	TypeBinding substituteInferenceVariable(InferenceVariable var, TypeBinding substituteType) {
		if (this == var)
			return substituteType;
		return this;
	}

	public char[] qualifiedSourceName() {
		throw new UnsupportedOperationException();
	}

	public char[] sourceName() {
		return this.sourceName;
	}

	public char[] readableName() {
		return this.sourceName;
	}

	public boolean hasTypeBit(int bit) {
		throw new UnsupportedOperationException();
	}
	
	public String debugName() {
		return String.valueOf(this.sourceName);
	}
	
	public String toString() {
		return debugName();
	}
}
