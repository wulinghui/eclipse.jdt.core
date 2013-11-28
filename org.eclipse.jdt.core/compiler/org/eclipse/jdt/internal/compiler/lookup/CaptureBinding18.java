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
 * Capture-like type variable introduced during 1.8 type inference.
 */
public class CaptureBinding18 extends CaptureBinding {
	
	TypeBinding[] upperBounds;

	public CaptureBinding18(ReferenceBinding contextType, char[] sourceName, int captureID, LookupEnvironment environment) {
		super(contextType, sourceName, 0, captureID, environment);
	}
	
	public void setUpperBounds(TypeBinding[] upperBounds, ReferenceBinding javaLangObject) {
		this.upperBounds = upperBounds;
		if (upperBounds.length > 0)
			this.firstBound = upperBounds[0];
		int numReferenceInterfaces = 0;
		for (int i = 0; i < upperBounds.length; i++) {
			TypeBinding aBound = upperBounds[i];
			if (aBound instanceof ReferenceBinding) {
				if (this.superclass == null && aBound.isClass())
					this.superclass = (ReferenceBinding) upperBounds[i];
				else if (aBound.isInterface())
					numReferenceInterfaces++;
				// TODO: what about additional super classes?? (see isCompatibleWith)
			}
		}
		this.superInterfaces = new ReferenceBinding[numReferenceInterfaces];
		int idx = 0;
		for (int i = 0; i < upperBounds.length; i++) {
			TypeBinding aBound = upperBounds[i];
			if (aBound.isInterface())
				this.superInterfaces[idx++] = (ReferenceBinding) aBound;
		}
		if (this.superclass == null)
			this.superclass = javaLangObject;
	}

	public TypeBinding clone(TypeBinding enclosingType) {
		return new CaptureBinding18(this.sourceType, this.sourceName, this.captureID, this.environment);
	}

	/**
	 * @see TypeBinding#isEquivalentTo(TypeBinding)
	 */
	public boolean isEquivalentTo(TypeBinding otherType) {
		// from CaptureBinding:
		if (equalsEquals(this, otherType)) return true;
		if (otherType == null) return false;
		if (this.upperBounds != null) {
			// from CaptureBinding:
			for (int i = 0; i < this.upperBounds.length; i++) {
				TypeBinding aBound = this.upperBounds[i];
				// capture of ? extends X[]
				if (aBound != null && aBound.isArrayType()) {
					if (!aBound.isCompatibleWith(otherType))
						continue;
				}
				switch (otherType.kind()) {
					case Binding.WILDCARD_TYPE :
					case Binding.INTERSECTION_TYPE :
						if (!((WildcardBinding) otherType).boundCheck(aBound))
							return false;
						break;
					default:
						return false;
				}
			}
			return true;
		}
		return false;
	}
	
	boolean isProperType(boolean admitCapture18) {
		return admitCapture18;
	}
	
	public char[] readableName() {
		if (this.lowerBound == null && this.firstBound != null) {
			if (!this.inRecursiveFunction) 
				try {
					this.inRecursiveFunction = true;
					return this.firstBound.readableName();
				} finally {
					this.inRecursiveFunction = false;
				}
		}
		return super.readableName();
	}
	
	public char[] shortReadableName() {
		if (this.lowerBound == null && this.firstBound != null)
			if (!this.inRecursiveFunction) 
				try {
					this.inRecursiveFunction = true;
					return this.firstBound.shortReadableName();
				} finally {
					this.inRecursiveFunction = false;
				}
		return super.shortReadableName();
	}
}
