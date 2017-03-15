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

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Indicates a package that is exported to a module by multiple readable modules
 * resulting in a conflict when resolving the package name
 *
 */
public class ConflictPackageBinding extends PackageBinding {

	public ConflictPackageBinding(char[][] compoundName, LookupEnvironment environment) {
		super(compoundName, null, environment);
	}
	// List of all modules that export this package
	public Collection<ModuleBinding> modules() {
		return Stream.of(this.environment.knownModules.valueTable).filter(m -> m != null)
				.filter(m -> m.exportedPackages.get(readableName()) != null).collect(Collectors.toList());
	}
	
	public int problemId() {
		return ProblemReasons.Ambiguous; // TODO: add new error for conflicting exported packages
	}
}
