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

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.ast.Expression;

/**
 * Main class for new type inference as per JLS8 sect 18.
 * Keeps contextual state and drives the algorithm.
 */
public class InferenceContext18 {

	InferenceVariable[] inferenceVariables;
	BoundSet currentBounds;
	ConstraintFormula[] initialConstraints;
	
	Scope scope;
	LookupEnvironment environment;
	ReferenceBinding object;
	
	// interim, processed by createInitialConstraintsForParameters()
	Expression[] invocationArguments;
	
	/** Construct an inference context for an invocation (method/constructor). */
	public InferenceContext18(Scope scope, Expression[] arguments) {
		this.scope = scope;
		this.environment = scope.environment();
		this.object = scope.getJavaLangObject();
		this.invocationArguments = arguments;
	}

	/**
	 * JLS 18.1.3: Create initial bounds from a given set of type parameters declarations.
	 * @return the set of inference variables created so far
	 */
	public InferenceVariable[] createInitialBoundSet(TypeVariableBinding[] typeParameters) {
		// 
		if (this.currentBounds != null) return Binding.NO_INFERENCE_VARIABLES; // already initialized from explicit type parameters
		this.currentBounds = new BoundSet();
		if (typeParameters != null) {
			this.inferenceVariables = addInitialTypeVariableSubstitutions(typeParameters);
			this.currentBounds.addBoundsFromTypeParameters(this, typeParameters, this.inferenceVariables);
		}
		return this.inferenceVariables;
	}

	/**
	 * Substitute any type variables mentioned in 'type' by the corresponding inference variable, if one exists. 
	 */
	public TypeBinding substitute(TypeBinding type) {
		return 	Scope.substitute(new InferenceSubstitution(this.environment, this.inferenceVariables), type);
	}

	/** JLS 18.5.1: compute bounds from formal and actual parameters. */
	public void createInitialConstraintsForParameters(TypeBinding[] parameters, boolean isVarArgs, boolean passThrough, TypeBinding varArgsType) {
		// TODO discriminate (strict,loose,variable-arity) invocations
		if (this.invocationArguments == null)
			return;
		int len = (isVarArgs && !passThrough) ? parameters.length - 1 : parameters.length;
		this.initialConstraints = new ConstraintFormula[this.invocationArguments.length];
		for (int i = 0; i < len; i++) {
			TypeBinding thetaF = substitute(parameters[i]);	
			this.initialConstraints[i] = new ConstraintExpressionFormula(this.invocationArguments[i], thetaF, ReductionResult.COMPATIBLE);
		}
		if (!passThrough && varArgsType instanceof ArrayBinding) {
			TypeBinding thetaF = substitute(((ArrayBinding) varArgsType).elementsType());
			for (int i = len; i < this.invocationArguments.length; i++) {
				this.initialConstraints[i] = new ConstraintExpressionFormula(this.invocationArguments[i], thetaF, ReductionResult.COMPATIBLE);
			}
		}
	}

	public void setInitialConstraint(ConstraintFormula constraintFormula) {
		this.initialConstraints = new ConstraintFormula[] { constraintFormula };
	}

	private InferenceVariable[] addInitialTypeVariableSubstitutions(TypeBinding[] typeVariables) {
		int len = typeVariables.length;
		this.inferenceVariables = new InferenceVariable[len];
		for (int i = 0; i < len; i++)
			this.inferenceVariables[i] = new InferenceVariable(typeVariables[i], i, this.environment);
		return this.inferenceVariables;
	}

	/** Add new inference variables for the given type variables. */
	public InferenceVariable[] addTypeVariableSubstitutions(TypeBinding[] typeVariables) {
		int len2 = typeVariables.length;
		InferenceVariable[] newVariables = new InferenceVariable[len2];
		for (int i = 0; i < typeVariables.length; i++)
			newVariables[i] = new InferenceVariable(typeVariables[i], i, this.environment);

		int start = 0;
		if (this.inferenceVariables != null) {
			int len1 = this.inferenceVariables.length;
			System.arraycopy(this.inferenceVariables, 0, this.inferenceVariables = new InferenceVariable[len1+len2], 0, len1);
			start = len1;
		} else {
			this.inferenceVariables = new InferenceVariable[len2];
		}
		System.arraycopy(newVariables, 0, this.inferenceVariables, start, len2);
		return newVariables;
	}

	/** JLS 18.1.3 Bounds: throws α: the inference variable α appears in a throws clause */
	public void addThrowsContraints(TypeBinding[] parameters, InferenceVariable[] variables, ReferenceBinding[] thrownExceptions) {
		for (int i = 0; i < parameters.length; i++) {
			TypeBinding parameter = parameters[i];
			for (int j = 0; j < thrownExceptions.length; j++) {
				if (TypeBinding.equalsEquals(parameter, thrownExceptions[j])) {
					this.currentBounds.inThrows.add(variables[i]);
					break;
				}
			}
		}		
	}

	/** JLS 18.5.1 Invocation Applicability Inference */
	public void inferInvocationApplicability(MethodBinding method, TypeBinding[] arguments) {
		ConstraintExpressionFormula.inferInvocationApplicability(this, method, arguments);
	}

	/** JLS 18.5.2 Invocation Type Inference */
	public boolean inferPolyInvocationType(InvocationSite invocationSite, MethodBinding method) throws InferenceFailureException {
		return ConstraintExpressionFormula.inferPolyInvocationType(this, invocationSite, method);
	}

	/** 18.5.2: before Invocation Type Inference purge all instantiations.
	 * @return the previous (unpurged) bound set
	 */
	public BoundSet purgeInstantiations() {
		BoundSet original = this.currentBounds;
		this.currentBounds = this.currentBounds.copy(true/*purgeInstantiations*/);
		return original;
	}

	// ========== Below this point: implementation of the generic algorithm: ==========

	/**
	 * Try to solve the inference problem defined by constraints and bounds previously registered.
	 * @return a bound set representing the solution, or null if inference failed
	 * @throws InferenceFailureException a compile error has been detected during inference
	 */
	public /*@Nullable*/ BoundSet solve() throws InferenceFailureException {
		if (!reduce())
			return null;
		if (!this.currentBounds.incorporate(this))
			return null;
		return resolve();
	}

	/**
	 * JLS 18.2. reduce all initial constraints 
	 * @throws InferenceFailureException 
	 */
	private boolean reduce() throws InferenceFailureException {
		if (this.initialConstraints != null) {
			for (int i = 0; i < this.initialConstraints.length; i++) {
				if (!this.currentBounds.reduceOneConstraint(this, this.initialConstraints[i]))
					return false;
			}
		}
		this.initialConstraints = null;
		return true;
	}

	/**
	 * Have all inference variables been instantiated successfully?
	 */
	public boolean isResolved(BoundSet boundSet) {
		if (this.inferenceVariables != null) {
			for (int i = 0; i < this.inferenceVariables.length; i++) {
				if (!boundSet.isInstantiated(this.inferenceVariables[i]))
					return false;
			}
		}
		return true;
	}

	/**
	 * Retrieve the resolved solutions for all given type variables.
	 * @param typeParameters
	 * @param boundSet where instantiations are to be found
	 * @return array containing the substituted types or <code>null</code> elements for any type variable that could not be substituted.
	 */
	public TypeBinding /*@Nullable*/[] getSolutions(final TypeVariableBinding[] typeParameters, BoundSet boundSet) {
		int len = typeParameters.length;
		TypeBinding[] substitutions = new TypeBinding[len];
		for (int i = 0; i < typeParameters.length; i++) {
			for (int j = 0; j < this.inferenceVariables.length; j++) {
				InferenceVariable variable = this.inferenceVariables[j];
				if (TypeBinding.equalsEquals(variable.typeParameter, typeParameters[i])) {
					substitutions[i] = boundSet.getInstantiation(variable);
					break;
				}
			}
			if (substitutions[i] == null)
				return null;
		}
		return substitutions;
	}

	/** When inference produces a new constraint, reduce it to a suitable type bound and add the latter to the bound set. */
	public boolean reduceAndIncorporate(ConstraintFormula constraint) throws InferenceFailureException {
		return this.currentBounds.reduceOneConstraint(this, constraint); // TODO(SH): should we immediately call a diat incorporate, or can we simply wait for the next round?
	}

	/**
	 * <b>JLS 18.4</b> Resolution
	 * @return answer null if some constraint resolved to FALSE, otherwise the boundset representing the solution
	 * @throws InferenceFailureException 
	 */
	private /*@Nullable*/ BoundSet resolve() throws InferenceFailureException {
		// NOTE: 18.5.2 ... 
		// "(While it was necessary to demonstrate that the inference variables in B1 could be resolved
		//   in order to establish applicability, the resulting instantiations are not considered part of B1.)
		// TODO: Does this imply resolve() should *not* modify the boundset at all??
		BoundSet tmpBoundSet = this.currentBounds;
		if (this.inferenceVariables != null) {
			for (int i = 0; i < this.inferenceVariables.length; i++) {
				InferenceVariable currentVariable = this.inferenceVariables[i];
				if (this.currentBounds.isInstantiated(currentVariable)) continue;
				// find a minimal set of dependent variables:
				Set variableSet = new HashSet();
				int numUninstantiated = addDependencies(tmpBoundSet, variableSet, i);
				final int numVars = variableSet.size();
				
				if (numUninstantiated > 0 && numVars > 0) {
					final InferenceVariable[] variables = (InferenceVariable[]) variableSet.toArray(new InferenceVariable[numVars]);
					if (!tmpBoundSet.hasCaptureBound(variableSet)) {
						// try to instantiate this set of variables in a fresh copy of the bound set:
						BoundSet prevBoundSet = tmpBoundSet;
						tmpBoundSet = tmpBoundSet.copy(false/*purgeInstantiations*/);
						for (int j = 0; j < variables.length; j++) {
							InferenceVariable variable = variables[j];
							// try lower bounds:
							TypeBinding[] lowerBounds = tmpBoundSet.lowerBounds(variable, true/*onlyProper*/);
							if (lowerBounds != Binding.NO_TYPES) {
								TypeBinding lub = this.scope.lowerUpperBound(lowerBounds);
								if (lub != TypeBinding.VOID && lub != null)
									tmpBoundSet.addBound(new TypeBound(variable, lub, ReductionResult.SAME));
							} else {
								TypeBinding[] upperBounds = tmpBoundSet.upperBounds(variable, true/*onlyProper*/);
								// check exception bounds:
								if (tmpBoundSet.inThrows.contains(variable) && tmpBoundSet.hasOnlyTrivialExceptionBounds(variable, upperBounds)) {
									TypeBinding runtimeException = this.scope.getType(TypeConstants.JAVA_LANG_RUNTIMEEXCEPTION, 3);
									tmpBoundSet.addBound(new TypeBound(variable, runtimeException, ReductionResult.SAME));
								} else {
									// try upper bounds:
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
							}
						}
						if (tmpBoundSet.incorporate(this))
							continue;
						tmpBoundSet = prevBoundSet;// clean-up for second attempt
					}
					// Otherwise, a second attempt is made...
					final CaptureBinding18[] zs = new CaptureBinding18[numVars];
					for (int j = 0; j < numVars; j++)
						zs[j] = freshCapture(variables[j]);
					Substitution theta = new Substitution() {
						public LookupEnvironment environment() { 
							return InferenceContext18.this.environment;
						}
						public boolean isRawSubstitution() {
							return false;
						}
						public TypeBinding substitute(TypeVariableBinding typeVariable) {
							for (int j = 0; j < numVars; j++)
								if (variables[j] == typeVariable) //$IDENTITY-COMPARISON$ InferenceVariable does not participate in type annotation encoding
									return zs[j];
							return typeVariable;
						}
					};
					for (int j = 0; j < numVars; j++) {
						InferenceVariable variable = variables[j];
						CaptureBinding18 zsj = zs[j];
						// add lower bounds:
						TypeBinding[] lowerBounds = tmpBoundSet.lowerBounds(variable, false/*onlyProper*/);
						if (lowerBounds != Binding.NO_TYPES) {
							lowerBounds = Scope.substitute(theta, lowerBounds);
							TypeBinding lub = this.scope.lowerUpperBound(lowerBounds);
							if (lub != TypeBinding.VOID && lub != null)
								zsj.lowerBound = lub;
						}
						// add upper bounds:
						TypeBinding[] upperBounds = tmpBoundSet.upperBounds(variable, false/*onlyProper*/);
						if (upperBounds != Binding.NO_TYPES) {
							for (int k = 0; k < upperBounds.length; k++)
								upperBounds[k] = Scope.substitute(theta, upperBounds[k]);
							setUpperBounds(zsj, upperBounds);
						}
						// FIXME: remove capture bounds
						tmpBoundSet.addBound(new TypeBound(variable, zsj, ReductionResult.SAME));
					}
					if (tmpBoundSet.incorporate(this))
						continue;
					return null;
				}
			}
		}
		return tmpBoundSet;
	}
	
	// === FIXME(stephan): all this capture business is quite drafty: ===
	int captureId = 0;
	
	/** For 18.4: "Let Z1, ..., Zn be fresh type variables" use capture bindings. */
	private CaptureBinding18 freshCapture(InferenceVariable variable) {
		Binding declaringElement = (variable.typeParameter instanceof TypeVariableBinding)
				? ((TypeVariableBinding) variable.typeParameter).declaringElement : null;
		char[] sourceName = CharOperation.concat("Z-".toCharArray(), variable.sourceName);
		return new CaptureBinding18(declaringElement, sourceName, this.captureId++, this.environment);
	}
	// === ===
	
	private void setUpperBounds(CaptureBinding18 typeVariable, TypeBinding[] substitutedUpperBounds) {
		// 18.4: ... define the upper bound of Zi as glb(L1θ, ..., Lkθ)
		if (substitutedUpperBounds.length == 1) {
			typeVariable.setUpperBounds(substitutedUpperBounds, this.object); // shortcut
		} else {
			TypeBinding[] glbs = Scope.greaterLowerBound(substitutedUpperBounds, this.scope, this.environment);
			typeVariable.setUpperBounds(glbs, this.object);
		}
	}

	/** 
	 * starting with our i'th inference variable collect all variables
	 * reachable via dependencies (regardless of relation kind).
	 * @param variableSet collect all variables found into this set
	 * @param i seed index into {@link #inferenceVariables}.
	 * @return count of uninstantiated variables added to the set.
	 */
	private int addDependencies(BoundSet boundSet, Set variableSet, int i) {
		InferenceVariable currentVariable = this.inferenceVariables[i];
		if (boundSet.isInstantiated(currentVariable)) return 0;
		if (!variableSet.add(currentVariable)) return 1;
		int numUninstantiated = 1;
		for (int j = 0; j < this.inferenceVariables.length; j++) {
			if (i == j) continue;
			if (boundSet.dependsOnResolutionOf(currentVariable, this.inferenceVariables[j]))
				numUninstantiated += addDependencies(boundSet, variableSet, j);
		}
		return numUninstantiated;
	}

	// debugging:
	public String toString() {
		StringBuffer buf = new StringBuffer("Inference Context"); //$NON-NLS-1$
		if (isResolved(this.currentBounds))
			buf.append(" (resolved)"); //$NON-NLS-1$
		buf.append('\n');
		if (this.inferenceVariables != null) {
			buf.append("Inference Variables:\n"); //$NON-NLS-1$
			for (int i = 0; i < this.inferenceVariables.length; i++) {
				buf.append('\t').append(this.inferenceVariables[i].sourceName).append("\t:\t"); //$NON-NLS-1$
				if (this.currentBounds.isInstantiated(this.inferenceVariables[i]))
					buf.append(this.currentBounds.getInstantiation(this.inferenceVariables[i]).readableName());
				else
					buf.append("NOT INSTANTIATED"); //$NON-NLS-1$
				buf.append('\n');
			}
		}
		if (this.initialConstraints != null) {
			buf.append("Initial Constraints:\n"); //$NON-NLS-1$
			for (int i = 0; i < this.initialConstraints.length; i++)
				if (this.initialConstraints[i] != null)
					buf.append('\t').append(this.initialConstraints[i].toString()).append('\n');
		}
		if (this.currentBounds != null)
			buf.append(this.currentBounds.toString());
		return buf.toString();
	}

	// INTERIM: infrastructure for detecting failures caused by specific known incompleteness:
	public static void missingImplementation(String msg) {
		throw new UnsupportedOperationException(msg);
	}
}
