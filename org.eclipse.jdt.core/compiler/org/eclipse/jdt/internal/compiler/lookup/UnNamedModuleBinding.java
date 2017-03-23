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
 *     
 *******************************************************************************/
package org.eclipse.jdt.internal.compiler.lookup;

import java.util.Collection;
import java.util.stream.Stream;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.env.IModuleContext;
import org.eclipse.jdt.internal.compiler.util.JRTUtil;

/**
 * Represents the unnamed module
 *
 */
public class UnNamedModuleBinding extends ModuleBinding {

	UnNamedModuleBinding(LookupEnvironment env) {
		super(env);
	}
	public ModuleBinding[] getAllRequiredModules() {
		// Not caching required modules here, because some more modules may have been
		// added to the list of known modules, and we need to check all of them as well
		Collection<ModuleBinding> allRequires = dependencyCollector().get();
		ModuleBinding javaBase = this.environment.getModule(JRTUtil.JAVA_BASE_CHAR);
		if (!CharOperation.equals(this.moduleName, TypeConstants.JAVA_BASE) && javaBase != null) {
			allRequires.add(javaBase);
		}
		return allRequires.size() > 0 ? allRequires.toArray(new ModuleBinding[allRequires.size()]) : NO_REQUIRES;
	}
	protected Stream<ModuleBinding> getRequiredModules(boolean transitiveOnly) {
		return Stream.of(this.environment.knownModules.valueTable).filter(m -> m != null);
	}
	public IModuleContext getDependencyClosureContext() {
		return IModuleContext.UNNAMED_MODULE_CONTEXT;
	}
	public IModuleContext getModuleGraphContext() {
		return IModuleContext.UNNAMED_MODULE_CONTEXT;
	}
}