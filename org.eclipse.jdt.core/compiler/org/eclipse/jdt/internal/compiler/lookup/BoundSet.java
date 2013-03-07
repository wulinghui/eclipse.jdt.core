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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Implementation of 18.1.3 in JLS8.
 * This class is also responsible for incorporation as defined in 18.3.
 */
class BoundSet {

	static final BoundSet TRUE = new BoundSet();	// empty set of bounds
	static final BoundSet FALSE = new BoundSet();	// pseudo bounds
	
	/**
	 * This structure holds all type bounds for a given inference variable.
	 * These are internally stored in three sets, one for each of the relations
	 * that may occur in a type bound: SUPERTYPE, SAME, SUBTYPE.
	 */
	private class ThreeSets {
		Set/*<TypeBound>*/ superBounds;
		Set/*<TypeBound>*/ sameBounds;
		Set/*<TypeBound>*/ subBounds;
		
		public ThreeSets() {
			// empty, the sets are lazily initialized
		}
		/** Add a type bound to the appropriate set. */
		public void addBound(TypeBound bound) {
			switch (bound.relation) {
				case ReductionResult.SUPERTYPE:
					if (this.superBounds == null) this.superBounds = new HashSet();
					this.superBounds.add(bound);
					break;
				case ReductionResult.SAME:
					if (this.sameBounds == null) this.sameBounds = new HashSet();
					this.sameBounds.add(bound);
					break;
				case ReductionResult.SUBTYPE:
					if (this.subBounds == null) this.subBounds = new HashSet();
					this.subBounds.add(bound);
					break;
				default:
					throw new IllegalArgumentException("Unexpected bound relation in : "+bound);
			}
		}
		// pre: this.superBounds != null
		public TypeBinding[] lowerBounds() {
			TypeBinding[] rights = new TypeBinding[this.superBounds.size()];
			Iterator it = this.superBounds.iterator();
			int i = 0;
			while(it.hasNext()) {
				TypeBinding right=((TypeBound)it.next()).right;
				if ((right).isProperType())
					rights[i++] = right;
			}
			return rights;
		}
		// pre: this.subBounds != null
		public TypeBinding[] upperBounds() {
			ReferenceBinding[] rights = new ReferenceBinding[this.subBounds.size()];
			TypeBinding simpleUpper = null;
			Iterator it = this.subBounds.iterator();
			int i = 0;
			while(it.hasNext()) {
				TypeBinding right=((TypeBound)it.next()).right;
				if ((right).isProperType()) {
					if (right instanceof ReferenceBinding) {
						rights[i++] = (ReferenceBinding) right;
					} else {
						if (simpleUpper != null)
							return Binding.NO_TYPES; // shouldn't
						simpleUpper = right;
					}
				}
			}
			if (i == 0)
				return Binding.NO_TYPES;
			if (i == 1 && simpleUpper != null)
				return new TypeBinding[] { simpleUpper };
			if (i < rights.length)
				System.arraycopy(rights, 0, rights=new ReferenceBinding[i], 0, i);
			return rights;
		}
		public boolean hasDependency(InferenceVariable beta) {
			if (this.superBounds != null && hasDependency(this.superBounds, beta))
				return true;
			if (this.sameBounds != null && hasDependency(this.sameBounds, beta))
				return true;
			if (this.subBounds != null && hasDependency(this.subBounds, beta))
				return true;
			return false;
		}
		private boolean hasDependency(Set someBounds, InferenceVariable var) {
			Iterator bIt = someBounds.iterator();
			while (bIt.hasNext()) {
				TypeBound bound = (TypeBound) bIt.next();
				if (bound.right == var || ParameterizedTypeBinding.typeParametersMentioned(var))
					return true;
			}
			return false;
		}
		/** Total number of type bounds in this container. */
		public int size() {
			int size = 0;
			if (this.superBounds != null)
				size += this.superBounds.size();
			if (this.sameBounds != null)
				size += this.sameBounds.size();
			if (this.subBounds != null)
				size += this.subBounds.size();
			return size;
		}
		public int flattenInto(TypeBound[] collected, int idx) {
			if (this.superBounds != null) {
				int len = this.superBounds.size();
				System.arraycopy(this.superBounds.toArray(), 0, collected, idx, len);
				idx += len;
			}
			if (this.sameBounds != null) {
				int len = this.sameBounds.size();
				System.arraycopy(this.sameBounds.toArray(), 0, collected, idx, len);
				idx += len;
			}
			if (this.subBounds != null) {
				int len = this.subBounds.size();
				System.arraycopy(this.subBounds.toArray(), 0, collected, idx, len);
				idx += len;
			}
			return idx;
		}
		public ThreeSets copy() {
			ThreeSets copy = new ThreeSets();
			if (this.superBounds != null)
				copy.superBounds = new HashSet(this.superBounds);
			if (this.sameBounds != null)
				copy.sameBounds = new HashSet(this.sameBounds);
			if (this.subBounds != null)
				copy.subBounds = new HashSet(this.subBounds);
			return copy;
		}
	}
	HashMap/*<InferenceVariable,ThreeSets>*/ boundsPerVariable = new HashMap();

	ConstraintExpressionFormula[] delayedExpressionConstraints = new ConstraintExpressionFormula[2];
	int constraintCount = 0;

	// A bound set evaluates to false if it contains the "FALSE" type bound.
	private boolean isFalse = false;

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

	/** Answer a flat representation of this BoundSet. */
	public TypeBound[] flatten() {
		int size = 0;
		Iterator outerIt = this.boundsPerVariable.values().iterator();
		while (outerIt.hasNext())
			size += ((ThreeSets)outerIt.next()).size();
		TypeBound[] collected = new TypeBound[size];
		if (size == 0) return collected;
		outerIt = this.boundsPerVariable.values().iterator();
		int idx = 0;
		while (outerIt.hasNext())
			idx = ((ThreeSets)outerIt.next()).flattenInto(collected, idx);
		return collected;
	}

	/**
	 * For resolution we work with a copy of the bound set, to enable retrying.
	 */
	public BoundSet copy() {
		BoundSet copy = new BoundSet();
		Iterator setsIterator = this.boundsPerVariable.entrySet().iterator();
		while (setsIterator.hasNext()) {
			Map.Entry entry = (Entry) setsIterator.next();
			copy.boundsPerVariable.put(entry.getKey(), ((ThreeSets)entry.getValue()).copy());
		}
		copy.delayedExpressionConstraints = this.delayedExpressionConstraints;
		copy.constraintCount = this.constraintCount;
		return copy;
	}

	public void addBound(TypeBound bound) {
		this.isFalse |= (bound == ReductionResult.FALSE);
		ThreeSets three = (ThreeSets) this.boundsPerVariable.get(bound.left);
		if (three == null)
			this.boundsPerVariable.put(bound.left, (three = new ThreeSets()));
		three.addBound(bound);
	}

	private void addBounds(TypeBound[] newBounds) {
		for (int i = 0; i < newBounds.length; i++)
			addBound(newBounds[i]);
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
			// using a flattened copy also allows us to insert more bounds during the process
			// without disturbing the current round of incorporation:
			TypeBound[] bounds = flatten();
			int boundsCount = bounds.length;
			if (boundsCount < 2)
				return newConstraints;
			// check each pair:
			for (int i = 0; i < boundsCount; i++) {
				TypeBound boundI = bounds[i];
				for (int j = i+1; j < boundsCount; j++) {
					TypeBound boundJ = bounds[j];
					if (boundI.hasBeenIncorporated && boundJ.hasBeenIncorporated)
						continue;
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
				boundI.hasBeenIncorporated = true;
			}
		} while (hasUpdate);
		if (newConstraints != null && newConstraintsCount < newConstraints.length) // need to shrink?
			System.arraycopy(newConstraints, 0, newConstraints = new ConstraintFormula[newConstraintsCount], 0, newConstraintsCount);
		return newConstraints;
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
		ThreeSets sets = (ThreeSets) this.boundsPerVariable.get(alpha);
		if (sets != null && sets.hasDependency(beta))
			return true;
		sets = (ThreeSets) this.boundsPerVariable.get(beta);
		if (sets != null && sets.hasDependency(alpha))
			return true;
		return false;
	}

	/**
	 * JLS 18.1.3:
	 * Answer all upper bounds for the given inference variable as defined by any bounds in this set. 
	 */
	public TypeBinding[] upperBounds(InferenceVariable variable) {
		ThreeSets three = (ThreeSets) this.boundsPerVariable.get(variable);
		if (three == null || three.subBounds == null)
			return Binding.NO_TYPES;
		return three.upperBounds();
	}
	
	/**
	 * JLS 18.1.3:
	 * Answer all lower bounds for the given inference variable as defined by any bounds in this set. 
	 */
	TypeBinding[] lowerBounds(InferenceVariable variable) {
		ThreeSets three = (ThreeSets) this.boundsPerVariable.get(variable);
		if (three == null || three.superBounds == null)
			return Binding.NO_TYPES;
		return three.lowerBounds();
	}
	
	// term 'instantiation' is defined in spec, but this seems to be unnecessary
	// due to update into InferenceVariable.resolvedType
	
	/** Does this bound set contain the bound FALSE? */
	public boolean isSatisfiable() {
		return !this.isFalse;
	}
	
	// debugging:
	public String toString() {
		StringBuffer buf = new StringBuffer("Type Bounds:\n"); //$NON-NLS-1$
		TypeBound[] flattened = flatten();
		for (int i = 0; i < flattened.length; i++) {
			buf.append('\t').append(flattened[i].toString()).append('\n');
		}
		return buf.toString();
	}
}
