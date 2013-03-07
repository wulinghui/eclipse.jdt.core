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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.internal.compiler.ast.Expression;

/**
 * Main class for new type inference as per JLS8 sect 18.
 * Keeps contextual state and drives the algorithm.
 */
public class InferenceContext18 {

	private static final int[] INVERSE = new int[6];
	static {
		INVERSE[ReductionResult.SUBTYPE] = ReductionResult.SUPERTYPE;
		INVERSE[ReductionResult.SUPERTYPE] = ReductionResult.SUBTYPE;
	}
	InferenceVariable[] inferenceVariables;
	BoundSet currentBounds;
	ConstraintFormula[] constraints;
	
	Scope scope;
	LookupEnvironment environment;
	ReferenceBinding object;
	
	// interim
	Expression[] invocationArguments;
	
	/** Construct an inference context for an invocation (method/constructor). */
	public InferenceContext18(Scope scope, Expression[] arguments, TypeBinding[] genericTypeArguments) {
		this.scope = scope;
		this.environment = scope.environment();
		this.object = scope.getJavaLangObject();
		if (genericTypeArguments != null)
			createInitialBoundSet(genericTypeArguments);
		this.invocationArguments = arguments;
	}

	public void createInitialBoundSet(TypeBinding[] typeParameters) {
		// JLS8 18.1.3
		if (this.currentBounds != null) return; // already initialized from explicit type parameters
		if (typeParameters != null)
			addTypeVariableSubstitutions(typeParameters);
		this.currentBounds = new BoundSet(this, typeParameters);
	}

	public void createInitialConstraintsForParameters(TypeBinding[] parameters) {
		// 18.5.1
		// TODO discriminate (strict,loose,variable-arity) invocations
		if (this.invocationArguments == null)
			return;
		int len = Math.min(this.invocationArguments.length, parameters.length); // varargs
		this.constraints = new ConstraintFormula[len];
		for (int i = 0; i < len; i++) {
			TypeBinding thetaF = substitute(parameters[i]);	
			this.constraints[i] = new ConstraintExpressionFormula(this.invocationArguments[i], thetaF, ReductionResult.COMPATIBLE);
		}
	}

	public void createInitialConstraintsForTargetType(TypeBinding returnType, TypeBinding expectedType) {
		// 18.5.2
// TODO: should depend on isPolyExpression once that is reliably in place:
		if (expectedType == null) return;
// TODO: enable once isPolyExpression is reliably in place
//		if (returnType == TypeBinding.VOID) this.currentBounds.isFalse = true;
		if (expectedType.isBaseType())
// TODO: could be OK if !isPolyExpression:
			return;
//			InferenceContext18.missingImplementation("NYI");
		TypeBinding thetaR = substitute(returnType);
		int l = 0;
		if (this.constraints != null) {
			l = this.constraints.length;
			System.arraycopy(this.constraints, 0, this.constraints=new ConstraintFormula[l+1], 0, l);
		} else {
			l = 0;
			this.constraints = new ConstraintFormula[1];
		}
		this.constraints[l] = new ConstraintTypeFormula(thetaR, expectedType, ReductionResult.COMPATIBLE);
	}

	public InferenceVariable createInferenceVariable(ReferenceBinding type) {
		InferenceVariable variable;
		if (this.inferenceVariables == null) {
			variable = new InferenceVariable(type, 0);
			this.inferenceVariables = new InferenceVariable[] { variable };
		} else {
			int len = this.inferenceVariables.length;
			variable = new InferenceVariable(type, len);
			System.arraycopy(this.inferenceVariables, 0, this.inferenceVariables = new InferenceVariable[len+1], 0, len);
			this.inferenceVariables[len] = variable;
		}
		return variable;
	}

	public void addTypeVariableSubstitutions(TypeBinding[] typeVariables) {
		int len2 = typeVariables.length;
		int start = 0;
		if (this.inferenceVariables != null) {
			int len1 = this.inferenceVariables.length;
			System.arraycopy(this.inferenceVariables, 0, this.inferenceVariables = new InferenceVariable[len1+len2], 0, len1);
			start = len1;
		} else {
			this.inferenceVariables = new InferenceVariable[len2];
		}
		for (int i = 0; i < typeVariables.length; i++)
			this.inferenceVariables[start+i] = new InferenceVariable(typeVariables[i], start+i);
	}

	private void addConstraints(ConstraintFormula[] newConstraints) {
		int len1 = 0;
		int len2 = newConstraints.length;
		if (this.constraints != null) {
			len1 = this.constraints.length;
		} else {
			this.constraints = new ConstraintFormula[len2];
		}
		if (len1 > 0)
			System.arraycopy(this.constraints, 0, this.constraints = new ConstraintFormula[len1+len2], 0, len1);
		System.arraycopy(newConstraints, 0, this.constraints, len1, len2);
	}

	/**
	 * Try to solve the inference problem defined by constraints and bounds previously registered.
	 * @return success?
	 */
	public boolean solve() {
		while (true) {
			if (!reduce())
				return false;
			this.constraints = this.currentBounds.incorporate(this);
			if (this.constraints == ConstraintFormula.FALSE_ARRAY) 
				return false;
			if (resolve())
				return isResolved();
		}
/* TODO: are we sure this will always terminate? Cf. e.g. (Discussion in 18.3):
 *  
 *    "The assertion that incorporation reaches a fixed point is not obvious. ...
 *    To do: each of these properties needs to be guaranteed via refinements to the specification."
 */
	}

	/**
	 * JLS 18.2. perform one round of reduction 
	 */
	boolean reduce() {
		if (this.constraints != null) {
			int j = 0;
			for (int i = 0; i < this.constraints.length; i++) {
				ConstraintFormula currentConstraint = this.constraints[i];
				ConstraintFormula[] result = this.currentBounds.reduceOneConstraint(this, currentConstraint);
				if (result == ConstraintFormula.FALSE_ARRAY)
					return false;
				if (result == ConstraintFormula.TRUE_ARRAY)
					continue;
				if (result != null) { // not reduced
					for (int k = 0; k < result.length; k++)
						this.constraints[j++] = result[k];
				}
			}
			if (j < this.constraints.length)
				System.arraycopy(this.constraints, 0, this.constraints = new ConstraintFormula[j], 0, j);
		}
		return true;
	}
	
	/**
	 * <b>JLS 18.4</b> Resolution
	 * @return answer true iff a fix point has been reached, i.e., no more constraints have been added.
	 */
	private boolean resolve() {
		boolean didAddConstraints = false;
		if (this.inferenceVariables != null) {
			for (int i = 0; i < this.inferenceVariables.length; i++) {
				// find a minimal set of dependent variables:
				Set variableSet = new HashSet();
				int numUnresolved = addDependencies(variableSet, i); // numUnresolved => terminate
				int numVars = variableSet.size();
				
				if (numUnresolved > 0 && numVars > 0) {
					// try to instantiate this set of variables in a fresh copy of the bound set:
					BoundSet tmpBoundSet = this.currentBounds.copy();
					InferenceVariable[] variables = (InferenceVariable[]) variableSet.toArray(new InferenceVariable[numVars]);
					for (int j = 0; j < variables.length; j++) {
						InferenceVariable variable = variables[j];
						// 1. attempt:
						TypeBinding[] lowerBounds = tmpBoundSet.lowerBounds(variable);
						if (lowerBounds != Binding.NO_TYPES) {
							TypeBinding lub = this.scope.lowerUpperBound(lowerBounds);
							if (lub != TypeBinding.VOID && lub != null)
								tmpBoundSet.addBound(new TypeBound(variable, lub, ReductionResult.SAME));
							continue;
						}
						// 2. attempt:
						TypeBinding[] upperBounds = tmpBoundSet.upperBounds(variable);
						if (upperBounds != Binding.NO_TYPES) {
							TypeBinding glb;
							if (upperBounds.length == 1) {
								glb = upperBounds[0];
							} else {
								ReferenceBinding[] glbs = Scope.greaterLowerBound((ReferenceBinding[])upperBounds);
								if (glbs == null)
									throw new UnsupportedOperationException("no glb for "+Arrays.asList(upperBounds));
								else if (glbs.length == 1)
									glb = glbs[0];
								else
									glb = new IntersectionCastTypeBinding(glbs, this.environment);
							}
							tmpBoundSet.addBound(new TypeBound(variable, glb, ReductionResult.SAME));
						}
					}
					// check if incorporation produces new constraints:
					ConstraintFormula[] newConstraints = tmpBoundSet.incorporate(this);
					// check success:
					boolean hasFalse = false;
					if (newConstraints != null)
						for (int j = 0; j < newConstraints.length; j++)
							if (newConstraints[j] == ReductionResult.FALSE)
								hasFalse = true;
					hasFalse |= !tmpBoundSet.isSatisfiable();
					if (hasFalse) {
						return true; // we have the final outcome
					} else {
						if (newConstraints.length > 0) {
							addConstraints(newConstraints);
							didAddConstraints = true;
						}
						this.currentBounds = tmpBoundSet;
					}
				}
			}
		}
		return !didAddConstraints;
	}
	
	/** 
	 * starting with our i'th inference variable collect all variables
	 * reachable via dependencies (regardless of relation kind).
	 * @param variableSet collect all variables found into this set
	 * @param i seed index into {@link #inferenceVariables}.
	 * @return count of unresolved variables in the set.
	 */
	private int addDependencies(Set variableSet, int i) {
		InferenceVariable currentVariable = this.inferenceVariables[i];
		if (currentVariable.isResolved()) return 0;
		if (!variableSet.add(currentVariable)) return 1;
		int numUnresolved = 1;
		for (int j = 0; j < this.inferenceVariables.length; j++) {
			if (i == j) continue;
			if (this.currentBounds.dependsOnResolutionOf(currentVariable, this.inferenceVariables[j]))
				numUnresolved += addDependencies(variableSet, j);
		}
		return numUnresolved;
	}

	/**
	 * Substitute any type variables mentioned in 'type' by the corresponding inference variable, if one exists. 
	 */
	public TypeBinding substitute(TypeBinding type) {
		return 	Scope.substitute(new InferenceSubstitution(this.environment, this.inferenceVariables), type);
	}

	/**
	 * Have all inference variables been resolved successfully?
	 */
	public boolean isResolved() {
		if (this.inferenceVariables != null) {
			for (int i = 0; i < this.inferenceVariables.length; i++) {
				if (!this.inferenceVariables[i].isResolved())
					return false;
			}
		}
		return true;
	}

	/**
	 * Retrieve the resolved solutions for all given type variables.
	 * @param typeParameters
	 * @return the substituted types or <code>null</code> is any type variable could not be substituted.
	 */
	public TypeBinding /*@Nullable*/[] getSolutions(final TypeVariableBinding[] typeParameters) {
		int len = typeParameters.length;
		TypeBinding[] substitutions = new TypeBinding[len];
		for (int i = 0; i < typeParameters.length; i++) {
			for (int j = 0; j < this.inferenceVariables.length; j++) {
				InferenceVariable variable = this.inferenceVariables[j];
				if (variable.typeParameter == typeParameters[i]) {
					substitutions[i] = variable.resolvedType;
					break;
				}
			}
			if (substitutions[i] == null)
				return null;
		}
		return substitutions;
	}

	// debugging:
	public String toString() {
		StringBuffer buf = new StringBuffer("Inference Context"); //$NON-NLS-1$
		if (isResolved())
			buf.append(" (resolved)"); //$NON-NLS-1$
		buf.append('\n');
		if (this.inferenceVariables != null) {
			buf.append("Inference Variables:\n"); //$NON-NLS-1$
			for (int i = 0; i < this.inferenceVariables.length; i++) {
				buf.append('\t').append(this.inferenceVariables[i].sourceName).append("\t:\t"); //$NON-NLS-1$
				if (this.inferenceVariables[i].isResolved())
					buf.append(this.inferenceVariables[i].resolvedType.readableName());
				else
					buf.append("UNRESOLVED"); //$NON-NLS-1$
				buf.append('\n');
			}
		}
		if (this.constraints != null) {
			buf.append("Constraints:\n"); //$NON-NLS-1$
			for (int i = 0; i < this.constraints.length; i++)
				if (this.constraints[i] != null)
					buf.append('\t').append(this.constraints[i].toString()).append('\n');
		}
		if (this.currentBounds != null)
			buf.append(this.currentBounds.toString());
		return buf.toString();
	}

	// INTERIM: infrastructure for detecting failures caused by specific known incompleteness:
	static final String JLS_18_2_3_INCOMPLETE_TO_DO_DEFINE_THE_MOST_SPECIFIC_ARRAY_SUPERTYPE_OF_A_TYPE_T = "JLS 18.2.3 incomplete: \"\"To do: [...] define the most specific array supertype of a type T."; //$NON-NLS-1$
	public static void missingImplementation(String msg) {
		if (msg == JLS_18_2_3_INCOMPLETE_TO_DO_DEFINE_THE_MOST_SPECIFIC_ARRAY_SUPERTYPE_OF_A_TYPE_T)
			return; // when enabled produces 56 distinct errors in GenericTypeTest
		throw new UnsupportedOperationException(msg);
	}
}
