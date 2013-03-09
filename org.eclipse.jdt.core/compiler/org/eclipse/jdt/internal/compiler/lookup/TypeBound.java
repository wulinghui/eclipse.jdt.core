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

/**
 * Implementation of 18.1.3 in JLS8
 */
public class TypeBound extends ReductionResult {
	
	InferenceVariable left;

	// avoid attempts to incorporate the same pair of type bounds more than once:
	boolean hasBeenIncorporated; // TODO: check if this has to be a property of the BoundSet as to support retrying during resolution
	
	static TypeBound createBoundOrDependency(InferenceContext18 context, TypeBinding type, TypeBinding[] parameters, InferenceVariable variable, int idx) {
        // Part of JLS8 sect 18.1.3:
		if (type.mentionsAny(parameters, idx))
			return new TypeBound(context.environment, type, context.inferenceVariables, idx, SUBTYPE);
		else
			return new TypeBound(variable, context.substitute(type), SUBTYPE);		
	}

	/** Create a true type bound. */
	TypeBound(InferenceVariable inferenceVariable, TypeBinding typeBinding, int relation) {
		this.left = inferenceVariable;
		this.right = typeBinding;
		this.relation = relation;
		if (relation == SAME && typeBinding.isProperType())
			inferenceVariable.resolvedType = typeBinding;
	}

	/** Create a type dependency. */
	private TypeBound(LookupEnvironment env, TypeBinding type, InferenceVariable[] variables, int idx, int relation) {
		this.left = variables[idx];
		this.right = Scope.substitute(new InferenceSubstitution(env, variables), type);
		this.relation = relation;
	}

	/** distinguish bounds from dependencies. */
	boolean isBound() {
		return this.right.isProperType();
	}
	public int hashCode() {
		return this.left.hashCode() + this.right.hashCode() + this.relation;
	}
	public boolean equals(Object obj) {
		if (obj instanceof TypeBound) {
			TypeBound other = (TypeBound) obj;
			return this.left == other.left && this.right == other.right && this.relation == other.relation;
		}
		return false;
	}
	
	// debugging:
	public String toString() {
		boolean isBound = this.right.isProperType();
		StringBuffer buf = new StringBuffer();
		buf.append(isBound ? "TypeBound  " : "Dependency "); //$NON-NLS-1$ //$NON-NLS-2$
		buf.append(this.left.sourceName);
		buf.append(relationToString(this.relation));
		buf.append(this.right.readableName());
		return buf.toString();
	}
}
