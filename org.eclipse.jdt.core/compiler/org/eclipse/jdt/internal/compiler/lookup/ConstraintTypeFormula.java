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

import org.eclipse.jdt.internal.compiler.ast.Wildcard;

/**
 * Implementation of 18.1.2 in JLS8, cases:
 * <ul>
 * <li>S -> T <em>compatible</em></li>
 * <li>S <: T <em>subtype</em></li>
 * <li>S = T  <em>equality</em></li>
 * <li>S <= T <em>type argument containment</em></li>
 * </ul>
 */
class ConstraintTypeFormula extends ConstraintFormula {

	TypeBinding left;
	
	public ConstraintTypeFormula(TypeBinding exprType, TypeBinding right, int relation) {
		this.left = exprType;
		this.right = right;
		this.relation = relation;
	}

	// return: ReductionResult or ConstraintFormula[]
	public Object reduce(InferenceContext18 inferenceContext) {
		switch (this.relation) {
		case COMPATIBLE:
			// 18.2.2:
			if (this.left.isProperType() && this.right.isProperType()) {
				if (isCompatibleWithInLooseInvocationContext(this.left, this.right, inferenceContext))
					return TRUE;
				return FALSE;
			}
			if (this.left.isBaseType() && this.left != TypeBinding.NULL) {
				TypeBinding sPrime = inferenceContext.environment.computeBoxingType(this.left);
				return new ConstraintTypeFormula(sPrime, this.right, COMPATIBLE);
			}
			if (this.right.isBaseType() && this.right != TypeBinding.NULL) {
				TypeBinding tPrime = inferenceContext.environment.computeBoxingType(this.right);
				return new ConstraintTypeFormula(this.left, tPrime, COMPATIBLE);
			}
			return new ConstraintTypeFormula(this.left, this.right, SUBTYPE);
		case SUBTYPE:
			// 18.2.3:
			return reduceSubType(inferenceContext.scope, this.left, this.right);
		case SUPERTYPE:
			// 18.2.3:
			return reduceSubType(inferenceContext.scope, this.right, this.left);
		case SAME:
			// 18.2.4:
			return reduceTypeEquality();
		case TYPE_ARGUMENT_CONTAINED:
			// 18.2.3:
			if (this.right.kind() != Binding.WILDCARD_TYPE) {
				if (this.left.kind() != Binding.WILDCARD_TYPE) {
					return new ConstraintTypeFormula(this.left, this.right, SUBTYPE);						
				} else {
					return FALSE;
				}
			} else {
				WildcardBinding t = (WildcardBinding) this.right;
				if (t.boundKind == Wildcard.UNBOUND || t.bound.id == TypeIds.T_JavaLangObject)
					return TRUE;
				if (t.boundKind == Wildcard.EXTENDS) {
					if (this.left.kind() != Binding.WILDCARD_TYPE) {
						return new ConstraintTypeFormula(this.left, t.bound, SUBTYPE);
					} else {
						WildcardBinding s = (WildcardBinding) this.left;
						if (s.boundKind == Wildcard.EXTENDS) {
							return new ConstraintTypeFormula(s.bound, t.bound, SUBTYPE);
						} else {
							return FALSE;
						}
					}
				} else { // SUPER 
					if (this.left.kind() != Binding.WILDCARD_TYPE) {
						return new ConstraintTypeFormula(t.bound, this.left, SUBTYPE);
					} else {
						WildcardBinding s = (WildcardBinding) this.left;
						if (s.boundKind == Wildcard.SUPER) {
							return new ConstraintTypeFormula(t.bound, s.bound, SUBTYPE);
						} else {
							return FALSE;
						}
					}
				}
			}
		}
		return this;
	}

	private Object reduceTypeEquality() {
		// 18.2.4
		if (this.left.kind() == Binding.WILDCARD_TYPE) {
			if (this.right.kind() == Binding.WILDCARD_TYPE) {
				WildcardBinding leftWC = (WildcardBinding)this.left;
				WildcardBinding rightWC = (WildcardBinding)this.right;
				if (leftWC.bound == null && rightWC.bound == null)
					return TRUE;
				if ((leftWC.boundKind == Wildcard.EXTENDS && rightWC.boundKind == Wildcard.EXTENDS)
					||(leftWC.boundKind == Wildcard.SUPER && rightWC.boundKind == Wildcard.SUPER))
				{
					return new ConstraintTypeFormula(leftWC.bound, rightWC.bound, SAME); // TODO more bounds?
				}						
			}
		} else {
			if (this.right.kind() != Binding.WILDCARD_TYPE) {
				// left and right are types
				if (this.left.isProperType() && this.right.isProperType()) {
					if (this.left == this.right)
						return TRUE;
					return FALSE;
				}
				if (this.left instanceof InferenceVariable) {
					return new TypeBound((InferenceVariable) this.left, this.right, SAME);
				}
				if (this.right instanceof InferenceVariable) {
					return new TypeBound((InferenceVariable) this.right, this.left, SAME);
				}
				if (this.left.original() == this.right.original()) {
					throw new UnsupportedOperationException("NYI");
				}
				if (this.left.isArrayType() && this.right.isArrayType() && this.left.dimensions() == this.right.dimensions()) {
					return new ConstraintTypeFormula(this.left.leafComponentType(), this.right.leafComponentType(), SAME);
				}
			}
		}
		return FALSE;
	}

	private Object reduceSubType(Scope scope, TypeBinding subCandidate, TypeBinding superCandidate) {
		// 18.2.3 Subtyping Constraints
		if (subCandidate.isProperType() && superCandidate.isProperType()) {
			if (subCandidate.isCompatibleWith(superCandidate, scope))
				return TRUE;
			return FALSE;
		}
		if (subCandidate instanceof InferenceVariable)
			return new TypeBound((InferenceVariable)subCandidate, superCandidate, SUBTYPE);
		if (superCandidate instanceof InferenceVariable)
			return new TypeBound((InferenceVariable)superCandidate, subCandidate, SUPERTYPE); // normalize to have variable on LHS
		if (subCandidate.id == TypeIds.T_null)
			return TRUE;
		ReferenceBinding c;
		switch (superCandidate.kind()) {
			case Binding.GENERIC_TYPE:
			case Binding.TYPE:
			case Binding.RAW_TYPE: // TODO: check special handling of raw types?
				c = (ReferenceBinding) superCandidate;
				if (subCandidate instanceof ReferenceBinding) {
					ReferenceBinding s = (ReferenceBinding) subCandidate;
					if (s.original() == c)
						return TRUE;
					if (s.superclass() == c)
						return TRUE;
					ReferenceBinding[] superInterfaces = s.superInterfaces();
					if (superInterfaces != null) {
						for (int i=0, l=superInterfaces.length; i<l; i++)
							if (superInterfaces[i] == c)
								return TRUE;
					}
				}
				return FALSE;
			case Binding.ARRAY_TYPE:
				// FIXME: WTF?
				// let S'[] be the most specific array type that is a supertype of S (or S itself)
				if (subCandidate.kind() != Binding.ARRAY_TYPE)
					return FALSE;
				ArrayBinding subArray = (ArrayBinding) subCandidate;				
				TypeBinding superElement = ((ArrayBinding)superCandidate).elementsType();
				TypeBinding subElement = subArray.leafComponentType();
				if (!superElement.isBaseType() && !subElement.isBaseType()) {
					return new ConstraintTypeFormula(subElement, superElement, SUBTYPE);
				}
				return superElement == subElement ? TRUE : FALSE;
			case Binding.PARAMETERIZED_TYPE:
				TypeBinding[] superArgs = ((ParameterizedTypeBinding) superCandidate).arguments;
				TypeBinding substitutedSuper = subCandidate.findSuperTypeOriginatingFrom(superCandidate); // C<B1,B2,...>
				if (substitutedSuper == null || substitutedSuper.isRawType()) return FALSE;
				TypeBinding[] substitutedArgs = ((ParameterizedTypeBinding) substitutedSuper).arguments;
//				// BEGIN GUESS WORK:
//				if (superArgs == null || subArgs == null || superArgs.length != subArgs.length) {
//					if (substitutedSub.isRawType()) {
//						// FIXME: this integration is not yet sanctioned by the spec (0.6.2)
//						return inferUncheckedConversion(scope, substitutedSub, superCandidate);
//					}
//					// bail out only if our guess work produced a useless result
//					InferenceContext18.missingImplementation(InferenceContext18.JLS_18_2_3_INCOMPLETE_TO_DEFINE_THE_PARAMETERIZATION_OF_A_CLASS_C_FOR_A_TYPE_T);
//					return FALSE; // TODO
//				}
//				// END GUESS WORK
				ConstraintFormula[] results = new ConstraintFormula[superArgs.length];
				for (int i = 0; i < superArgs.length; i++) {
					results[i] = new ConstraintTypeFormula(substitutedArgs[i], superArgs[i], TYPE_ARGUMENT_CONTAINED);
				}
				return results;
			case Binding.WILDCARD_TYPE:
				// TODO If S is an intersection type of which T is an element, the constraint reduces to true. 
				if (subCandidate.kind() == Binding.INTERSECTION_TYPE)
					InferenceContext18.missingImplementation("NYI");
				WildcardBinding variable = (WildcardBinding) superCandidate;
				if (variable.boundKind == Wildcard.SUPER)
					return new ConstraintTypeFormula(subCandidate, variable.bound, SUBTYPE);
				return FALSE;
			case Binding.TYPE_PARAMETER:
				// same as wildcard (but we don't have a lower bound any way)
				// TODO If S is an intersection type of which T is an element, the constraint reduces to true.
				if (subCandidate.kind() == Binding.INTERSECTION_TYPE)
					InferenceContext18.missingImplementation("NYI");
				return FALSE;
			case Binding.INTERSECTION_TYPE:
				InferenceContext18.missingImplementation("NYI");
		}
		return this;// TODO
	}

	private Object inferUncheckedConversion(Scope scope, TypeBinding subCandidate, TypeBinding superCandidate) {
		// 18.5.5
		ReferenceBinding subOriginal = (ReferenceBinding) subCandidate.original();
		TypeVariableBinding[] typeVariables = subOriginal.typeVariables();
		int length = typeVariables.length;
		TypeBinding[] typeParameters = new TypeBinding[length];
		for (int i = 0; i < length; i++) {
			typeParameters[i] = new WildcardBinding(subOriginal, i, null, null, Wildcard.UNBOUND, scope.environment());
		}
		ReferenceBinding s1 = scope.environment().createParameterizedType(subOriginal, typeParameters, subOriginal.enclosingType());
		if (s1.isCompatibleWith(superCandidate))
			return TRUE;
		
		InferenceContext18 infCtx18 = new InferenceContext18(scope, null, null);
		typeParameters = infCtx18.createInitialBoundSet(typeVariables); // creates initial bound set B
		ReferenceBinding s2 = scope.environment().createParameterizedType(subOriginal, typeParameters, subOriginal.enclosingType());
		infCtx18.setInitialConstraint(new ConstraintTypeFormula(s2, superCandidate, ReductionResult.SUBTYPE));
		BoundSet result = infCtx18.solve();
		if (result != null && infCtx18.isResolved(result)) {
			TypeBinding[] solutions = infCtx18.getSolutions(typeVariables, result);
			if (solutions != null) {
				// create new bounds
				ConstraintFormula[] formulas = new ConstraintTypeFormula[length];
				for (int i = 0; i < length; i++) {
					formulas[i] = new ConstraintTypeFormula(typeParameters[i], solutions[i], ReductionResult.SAME);
				}
				return formulas;
			}
		}
		return FALSE;
	}

	// debugging
	public String toString() {
		StringBuffer buf = new StringBuffer("Type Constraint:\n"); //$NON-NLS-1$
		buf.append("\t⟨").append(this.left.readableName()); //$NON-NLS-1$
		buf.append(relationToString(this.relation));
		buf.append(this.right.readableName()).append('⟩');
		return buf.toString();
	}
}
