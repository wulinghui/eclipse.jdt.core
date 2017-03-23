/*******************************************************************************
 * Copyright (c) 2016 IBM Corporation.
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
package org.eclipse.jdt.internal.core;

import org.eclipse.jdt.core.IModuleDescription;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.compiler.env.IModule;
import org.eclipse.jdt.internal.compiler.env.IModuleEnvironment;
import org.eclipse.jdt.internal.compiler.env.IModulePathEntry;

/**
 * Represents a project
 *
 */
public class ProjectEntry implements IModulePathEntry {

	JavaProject project;
	
	public ProjectEntry(JavaProject project) {
		// 
		this.project = project;
	}
	@Override
	public IModule getModule() {
		try {
			IModuleDescription module = this.project.getModuleDescription();
			if (module != null) {
				return (ModuleDescriptionInfo) ((JavaElement) module) .getElementInfo();
			}
		} catch (JavaModelException e) {
			// Proceed with null;
		}
		return null;
	}

	@Override
	public IModuleEnvironment getLookupEnvironment() {
		// 
		return null;//this.project;
	}

	@Override
	public IModuleEnvironment getLookupEnvironmentFor(IModule module) {
		//
//		if (getModule() == module) {
//			try {
//				ArrayList<IPackageFragmentRoot> children = this.project.getChildrenOfType(IJavaElement.PACKAGE_FRAGMENT_ROOT);
//				List<IPackageFragmentRoot> srcRoots = children.stream().filter(r -> {
//					try {
//						return r instanceof PackageFragmentRoot && r.getKind() == IPackageFragmentRoot.K_SOURCE;
//					} catch (JavaModelException e) {
//						//
//					}
//					return false;
//				}).collect(Collectors.toList());
//				return new NameLookup.Roots(srcRoots.toArray(new PackageFragmentRoot[srcRoots.size()]));
//			} catch (JavaModelException e) {
//				//
//			}		
////			return this.project;
//		}
		return null;
	}
	@Override
	public boolean isAutomaticModule() {
		return false;
	}
}
