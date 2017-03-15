/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
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
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.compiler.lookup;

/**
 * Indicates a package binding that is not visible to a module, because it is not exported
 * by the declaring module or is selectively exported
 *
 */
public class ConcealedPackageBinding extends PackageBinding {

	private ModuleBinding declaringModule;

	public ConcealedPackageBinding(char[][] compoundName, LookupEnvironment environment, ModuleBinding declaringModule) {
		super(compoundName, null, environment);
		this.declaringModule = declaringModule;
	}
	
	public int problemId() {
		return ProblemReasons.NotVisible;
	}

	public char[] declaringModule() {
		return this.declaringModule.moduleName;
	}
	
	ReferenceBinding getType(char[] name, char[] mod) {
		PackageBinding wrapped = this.declaringModule.declaredPackages.get(readableName());
		return wrapped.getType(name, mod);
	}

}
