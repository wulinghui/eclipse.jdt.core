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
import java.util.List;

/**
 * Implementation of 18.1.3 in JLS8.
 * This class is also responsible for incorporation as defined in 18.3.
 */
class BoundSet {

	static final BoundSet TRUE = new BoundSet();	// empty set of bounds
	static final BoundSet FALSE = new BoundSet();	// pseudo bounds
	
	TypeBound[] bounds = new TypeBound[4];
	int boundsCount = 0;
	
	private int numIncorporatedBounds; // tracks how many bounds have already been incorporated.

	ConstraintExpressionFormula[] delayedExpressionConstraints = new ConstraintExpressionFormula[2];
	int constraintCount = 0;
	
	// just for the two constants TRUE and FALSE:
	private BoundSet() {}
	
	public BoundSet(InferenceContext18 context, TypeBinding[] typeParameters) {
		if (typeParameters != null) {
			InferenceVariable[] variables = context.inferenceVariables;
			int length = typeParameters.length;
			for (int i = 0; i < length; i++) {
				TypeBinding typeParameter = typeParameters[i];
				TypeBound[] someBounds = typeParameter.getTypeBounds(typeParameters, variables[i], i, context);
				if (someBounds.length == 0) {
					addBound(new TypeBound(variables[i], context.object, ReductionResult.SUBTYPE));
				} else {
					addBounds(someBounds);
				}
			}
		}
	}

	/**
	 * For resolution we work with a copy of the bound set, to enable retrying.
	 */
	public BoundSet copy() {
		BoundSet copy = new BoundSet();
		copy.bounds = this.bounds;
		copy.boundsCount = this.boundsCount;
		copy.delayedExpressionConstraints = this.delayedExpressionConstraints;
		copy.constraintCount = this.constraintCount;
		copy.numIncorporatedBounds = this.numIncorporatedBounds;
		return copy;
	}

	void addBound(TypeBound bound) {
		int oldLength = this.bounds.length;
		if (this.boundsCount + 1 >= oldLength) 
			System.arraycopy(this.bounds, 0, this.bounds = new TypeBound[oldLength+4], 0, oldLength);
		this.bounds[this.boundsCount++] = bound;
	}

	void addBounds(TypeBound[] newBounds) {
		int oldLength = this.bounds.length;
		int newLength = newBounds.length;
		if (this.boundsCount + newLength >= oldLength) 
			System.arraycopy(this.bounds, 0, this.bounds = new TypeBound[oldLength+newLength], 0, oldLength);
		System.arraycopy(newBounds, 0, this.bounds, this.boundsCount, newLength);
		this.boundsCount += newLength;
	}
	
	void addFormula(ConstraintExpressionFormula formula) {
		int oldLength = this.delayedExpressionConstraints.length;
		if (this.constraintCount + 1 >= oldLength) 
			System.arraycopy(this.delayedExpressionConstraints, 0, this.delayedExpressionConstraints = new ConstraintExpressionFormula[oldLength+4], 0, oldLength);
		this.delayedExpressionConstraints[this.constraintCount++] = formula;
	}

	/**
	 * <b>JLS 18.3:</b> Try to infer new constraints from pairs of existing type bounds.
	 * Each new constraint is first reduced and checked for TRUE or FALSE, which will
	 * abort the processing. 
	 * @param context the context that manages our inference variables
	 * @return the new constraints derived from existing type bounds, 
	 * 	or {@link ConstraintFormula#TRUE_ARRAY} or {@link ConstraintFormula#FALSE_ARRAY}
	 *  to signal termination of the process or <code>null</code> to signal no change. 
	 */
	/*@Nullable*/ ConstraintFormula[] incorporate(InferenceContext18 context) {
		boolean hasUpdate;
		ConstraintFormula[] newConstraints = null;
		int newConstraintsCount = 0;
		do {
			hasUpdate = false;
			if (this.boundsCount < 2)
				return newConstraints;
			// check each pair:
			for (int i = 0; i < this.boundsCount; i++) {
				TypeBound boundI = this.bounds[i];
				for (int j = Math.max(i+1, this.numIncorporatedBounds); j < this.boundsCount; j++) {
					TypeBound boundJ = this.bounds[j];
					ConstraintFormula newConstraint = null;
					switch (boundI.relation) {
						case ReductionResult.SAME:
							switch (boundJ.relation) {
								case ReductionResult.SAME:
									newConstraint = combineSameSame(boundI, boundJ);
									break;
								case ReductionResult.SUBTYPE:
								case ReductionResult.SUPERTYPE:
									newConstraint = combineSameSubSuper(boundI, boundJ);
									break;
							}
							break;
						case ReductionResult.SUBTYPE:
							switch (boundJ.relation) {
								case ReductionResult.SAME:
									newConstraint = combineSameSubSuper(boundJ, boundI);
									break;
								case ReductionResult.SUPERTYPE:
									newConstraint = combineSuperAndSub(boundJ, boundI);
									break;
								case ReductionResult.SUBTYPE:
									newConstraint = combineEqualSupers(boundI, boundJ);
									break;
							}
							break;
						case ReductionResult.SUPERTYPE:
							switch (boundJ.relation) {
								case ReductionResult.SAME:
									newConstraint = combineSameSubSuper(boundJ, boundI);
									break;
								case ReductionResult.SUBTYPE:
									newConstraint = combineSuperAndSub(boundI, boundJ);
									break;
								case ReductionResult.SUPERTYPE:
									newConstraint = combineEqualSupers(boundI, boundJ);
									break;
							}
					}
					if (newConstraint != null) {
						ConstraintFormula[] result = reduceOneConstraint(context, newConstraint);
						if (result == ConstraintFormula.FALSE_ARRAY || result == ConstraintFormula.TRUE_ARRAY)
							return result; 
						if (result != null) {
							// not directly reduceable
							int l1;
							int l2 = result.length;
							if (newConstraints == null)
								newConstraints = new ConstraintFormula[5];
							else if ((l1=newConstraints.length) + l2 <= newConstraintsCount+1) // TODO check arithm.
								System.arraycopy(newConstraints, 0, newConstraints=new ConstraintFormula[l1+5], 0, l1);
							
							for (int k = 0; k < result.length; k++)
								newConstraints[newConstraintsCount++] = result[k];
						} else {
							hasUpdate = true; // has already been added to currentBounds
						}
					}
				}
			}
			this.numIncorporatedBounds = this.boundsCount;
		} while (hasUpdate);
		return newConstraints; // may be null;
	}

	private ConstraintFormula combineSameSame(TypeBound boundS, TypeBound boundT) {
		
		// α = S and α = T imply ⟨S = T⟩
		if (boundS.left == boundT.left)
			return new ConstraintTypeFormula(boundS.right, boundT.right, ReductionResult.SAME);

		// match against more shapes:
		ConstraintFormula newConstraint;
		newConstraint = combineSameWithProperType(boundS, boundT);
		if (newConstraint != null)
			return newConstraint;
		newConstraint = combineSameWithProperType(boundT, boundS);
		if (newConstraint != null)
			return newConstraint;
		return null;
	}

	// pre: boundLeft.left != boundRight.left
	private ConstraintTypeFormula combineSameWithProperType(TypeBound boundLeft, TypeBound boundRight) {
		//  α = U and S = T imply ⟨S[α:=U] = T[α:=U]⟩
		TypeBinding u = boundLeft.right;
		if (u.isProperType()) {
			InferenceVariable alpha = boundLeft.left;
			TypeBinding left = boundRight.left;
			TypeBinding right = boundRight.right.substituteInferenceVariable(alpha, u);
			return new ConstraintTypeFormula(left, right, ReductionResult.SAME);
		}
		return null;
	}
	
	private ConstraintFormula combineSameSubSuper(TypeBound boundS, TypeBound boundT) {
		//  α = S and α <: T imply ⟨S <: T⟩ 
		//  α = S and T <: α imply ⟨T <: S⟩
		InferenceVariable alpha = boundS.left;
		if (alpha == boundT.left)
			return new ConstraintTypeFormula(boundS.right, boundT.right, boundT.relation);
		if (alpha == boundT.right)
			return new ConstraintTypeFormula(boundT.right, boundS.right, boundT.relation);
		
		//  α = U and S <: T imply ⟨S[α:=U] <: T[α:=U]⟩ 
		TypeBinding u = boundS.right;
		if (u.isProperType()) {
			TypeBinding left = (alpha == boundT.left) ? u : boundT.left;
			TypeBinding right = boundT.right.substituteInferenceVariable(alpha, u);
			return new ConstraintTypeFormula(left, right, boundT.relation);
		}
		return null;
	}

	private ConstraintFormula combineSuperAndSub(TypeBound boundS, TypeBound boundT) {
		//  S <: α and α <: T imply ⟨S <: T⟩ 
		TypeBinding alpha = boundS.left;
		if (alpha == boundT.left)
			return new ConstraintTypeFormula(boundS.right, boundT.right, ReductionResult.SUBTYPE);
		if (boundS.right instanceof InferenceVariable) {
			// try reverse:
			alpha = boundS.right;
			if (alpha == boundT.right)
				return new ConstraintTypeFormula(boundS.left, boundT.left, ReductionResult.SUPERTYPE);
		}
		return null;
	}
	
	private ConstraintFormula combineEqualSupers(TypeBound boundS, TypeBound boundT) {
		// map to this rule from combineSuperAndSub:
		//  S <: α and α <: T imply ⟨S <: T⟩
		if (boundS.left == boundT.right)
			// came in as: α REL S and T REL α imply ⟨T REL S⟩ 
			return new ConstraintTypeFormula(boundT.left, boundS.right, boundS.relation);
		if (boundS.right == boundT.left)
			// came in as: S REL α and α REL T imply ⟨S REL T⟩ 
			return new ConstraintTypeFormula(boundS.left, boundT.right, boundS.relation);		
		return null;
	}

	/**
	 * Try to reduce the one given constraint. 
	 */
	public ConstraintFormula[] reduceOneConstraint(InferenceContext18 context, ConstraintFormula currentConstraint) {
		// since a single constraint can produce more than one constraint
		// it's good to serialize these in a queue to work from:
		List queue = new ArrayList();
		int clottedBottem = 0; // below this offset all constraints are un-reduceable, no need to touch again 
		while (currentConstraint != null || queue.size() > clottedBottem) {
			if (currentConstraint == null)
				currentConstraint = (ConstraintFormula) queue.remove(clottedBottem);
			Object result = currentConstraint.reduce(context);
			boolean progress = result != currentConstraint;
			currentConstraint = null; // consume
			if (!progress) {
				queue.add(clottedBottem++, result);
			} else {
				if (result == ReductionResult.FALSE)
					return ConstraintFormula.FALSE_ARRAY;
				if (result == ReductionResult.TRUE)
					return ConstraintFormula.TRUE_ARRAY;
				if (result != null) {
					if (result instanceof ConstraintFormula) {
						currentConstraint = (ConstraintFormula) result;
					} else if (result instanceof ConstraintFormula[]) {
						ConstraintFormula[] resultArray = (ConstraintFormula[]) result;
						for (int i = 0; i < resultArray.length; i++)
							queue.add(resultArray[i]);
					} else {
						this.addBound((TypeBound)result);
					}
				}
			}
		}
		if (queue.size() > 0)
			return (ConstraintFormula[]) queue.toArray(new ConstraintFormula[queue.size()]);
		return null; // all are taken care of
	}

	/**
	 * Helper for resolution (18.4):
	 * Does this bound set define a direct dependency between the two given inference variables? 
	 */
	public boolean dependsOnResolutionOf(InferenceVariable alpha, InferenceVariable beta) {
		for(int i=0; i<this.boundsCount; i++) {
			TypeBound bound = this.bounds[i];
			if (bound.left == alpha) {
				if (bound.right == beta || ParameterizedTypeBinding.typeParametersMentioned(alpha))
					return true;
			}
			if (bound.right == alpha) {
				if (bound.left == beta) // no traversal needed, left is known to be an InferenceVariable
					return true;
			}
		}
		return false;
	}

	/**
	 * JLS 18.1.3:
	 * Answer all upper bounds for the given inference variable as defined by any bounds in this set. 
	 */
	public TypeBinding[] upperBounds(InferenceVariable variable) {
		ReferenceBinding[] upper = new ReferenceBinding[this.boundsCount];
		int j = 0;
		TypeBinding simpleUpper = null;
		for (int i = 0; i < this.boundsCount; i++) {
			TypeBound bound = this.bounds[i];
			if (bound.left == variable 
					&& bound.right.isProperType() 
					&& bound.relation == ReductionResult.SUBTYPE)
			{
				if (bound.right instanceof ReferenceBinding) {
					upper[j++] = (ReferenceBinding) bound.right;
				} else {
					if (simpleUpper != null)
						return Binding.NO_SUPERINTERFACES; // shouldn't
					simpleUpper = bound.right;
				}
			}
		}
		if (j == 0)
			return Binding.NO_SUPERINTERFACES;
		if (j == 1 && simpleUpper != null)
			return new TypeBinding[] { simpleUpper };
		if (j < this.boundsCount)
			System.arraycopy(upper, 0, upper=new ReferenceBinding[j], 0, j);
		return upper;
	}
	
	/**
	 * JLS 18.1.3:
	 * Answer all lower bounds for the given inference variable as defined by any bounds in this set. 
	 */
	TypeBinding[] lowerBounds(InferenceVariable variable) {
		TypeBinding[] lower = new TypeBinding[this.boundsCount];
		int j = 0;
		for (int i = 0; i < this.boundsCount; i++) {
			TypeBound bound = this.bounds[i];
			if (bound.left == variable
					&& bound.right.isProperType()
					&& bound.relation == ReductionResult.SUPERTYPE)
				lower[j++] = bound.right;
		}
		if (j == 0)
			return Binding.NO_TYPES;
		if (j < this.boundsCount)
			System.arraycopy(lower, 0, lower=new TypeBinding[j], 0, j);
		return lower;
	}
	
	// term 'instantiation' is defined in spec, but this seems to be unnecessary
	// due to update into InferenceVariable.resolvedType
	
	public boolean isSatisfiable() {
		// TODO cache as a flag during addBound()?
		for (int i = 0; i < this.boundsCount; i++)
			if (this.bounds[i] == ReductionResult.FALSE)
				return false;
		return true;
	}
	
	// debugging:
	public String toString() {
		StringBuffer buf = new StringBuffer("Type Bounds:\n"); //$NON-NLS-1$
		for (int i = 0; i < this.boundsCount; i++) {
			buf.append('\t').append(this.bounds[i].toString()).append('\n');
		}
		return buf.toString();
	}
}
