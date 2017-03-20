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
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.INameEnvironmentExtension;
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
	PackageBinding getTopLevelPackage(char[] name) {
		PackageBinding packageBinding = this.declaredPackages.get(name);
		if (packageBinding != null) {
			if (packageBinding == LookupEnvironment.TheNotFoundPackage)
				return null;
			return packageBinding;
		}
		if (declaresPackage(new char[][] {name})) {
			packageBinding = new PackageBinding(name, this.environment);
		}
		if (packageBinding != null) {
		//if (this.nameEnvironment.isPackage(null, name, mod)) {
			this.declaredPackages.put(name, packageBinding);
			return packageBinding;
		}

		this.declaredPackages.put(name, LookupEnvironment.TheNotFoundPackage); // saves asking the oracle next time
		return null;
	}

	public PackageBinding getDeclaredPackage(char[][] name) {
		// return package binding if there exists a package named name in this module
		if (name == null || name.length == 0) {
			return this.environment.getDefaultPackage(this.moduleName);
		}
//		char[] qualifiedName = CharOperation.concatWith(name, '.');
		PackageBinding packageBinding = getTopLevelPackage(name[0]);
		if (packageBinding == null || packageBinding == LookupEnvironment.TheNotFoundPackage)
			return null;
		int length = name.length, index = 1;
		while (index < length) {
			char[] simpleName = name[index++];
			PackageBinding binding = packageBinding.getPackage0(simpleName);
			if (binding != null) {
				if (binding == LookupEnvironment.TheNotFoundPackage) {
					return null;
				}
			} else {
				if (declaresPackage(packageBinding.compoundName, simpleName)) {
					char[][] subPkgCompoundName = CharOperation.arrayConcat(packageBinding.compoundName, simpleName);
					binding = new PackageBinding(subPkgCompoundName, packageBinding, this.environment);
					packageBinding.addPackage(binding);
				} else {
					packageBinding.addNotFoundPackage(simpleName);
					return null;
				}
			}
			packageBinding = binding;
		}
		return packageBinding;
	}
	public PackageBinding createPackage(char[][] compoundName) {
		PackageBinding packageBinding = this.declaredPackages.get(compoundName[0]);
		if (packageBinding == null || packageBinding == LookupEnvironment.TheNotFoundPackage) {
			packageBinding = new PackageBinding(compoundName[0], this.environment);
			this.declaredPackages.put(compoundName[0], packageBinding);
		}
		for (int i = 1, length = compoundName.length; i < length; i++) {
			// check to see if it collides with a known type...
			// this case can only happen if the package does not exist as a directory in the file system
			// otherwise when the source type was defined, the correct error would have been reported
			// unless its an unresolved type which is referenced from an inconsistent class file
			// NOTE: empty packages are not packages according to changes in JLS v2, 7.4.3
			// so not all types cause collision errors when they're created even though the package did exist
			ReferenceBinding type = packageBinding.getType0(compoundName[i]);
			if (type != null && type != LookupEnvironment.TheNotFoundType
					&& !(type instanceof UnresolvedReferenceBinding))
				return null;

			PackageBinding parent = packageBinding;
			if ((packageBinding = parent.getPackage0(compoundName[i])) == null
					|| packageBinding == LookupEnvironment.TheNotFoundPackage) {
				// if the package is unknown, check to see if a type exists which would collide with the new package
				// catches the case of a package statement of: package java.lang.Object;
				// since the package can be added after a set of source files have already been compiled,
				// we need to check whenever a package is created
				INameEnvironment nameEnvironment = this.environment.nameEnvironment;
				if (nameEnvironment instanceof INameEnvironmentExtension) {
					// When the nameEnvironment is an instance of INameEnvironmentWithProgress, it can get avoided to
					// search for secondaryTypes (see flag).
					// This is a performance optimization, because it is very expensive to search for secondary types
					// and it isn't necessary to check when creating a package,
					// because package name can not collide with a secondary type name.
					if (((INameEnvironmentExtension) nameEnvironment).findType(compoundName[i], parent.compoundName,
							false, getDependencyClosureContext()) != null) {
						return null;
					}
				} else {
					if (nameEnvironment.findType(compoundName[i], parent.compoundName) != null) {
						return null;
					}
				}
				packageBinding = new PackageBinding(CharOperation.subarray(compoundName, 0, i + 1), parent,
						this.environment);
				parent.addPackage(packageBinding);
			}
		}
		return packageBinding;
	}
	public PackageBinding computePackageFrom(char[][] constantPoolName, boolean isMissing) {
		if (constantPoolName.length == 1)
			return this.environment.getDefaultPackage(this.moduleName);

		PackageBinding packageBinding = getPackage(CharOperation.subarray(constantPoolName, 0, constantPoolName.length - 1));//this.declaredPackages.get(constantPoolName[0]);
		if (packageBinding != null && packageBinding.isValidBinding())
			return packageBinding;
//		if (packageBinding == null || packageBinding == LookupEnvironment.TheNotFoundPackage) {
			packageBinding = new PackageBinding(constantPoolName[0], this.environment);
			if (isMissing) packageBinding.tagBits |= TagBits.HasMissingType;
			this.declaredPackages.put(constantPoolName[0], packageBinding);
//		}

		for (int i = 1, length = constantPoolName.length - 1; i < length; i++) {
			PackageBinding parent = packageBinding;
			if ((packageBinding = parent.getPackage0(constantPoolName[i])) == null || packageBinding == LookupEnvironment.TheNotFoundPackage) {
				packageBinding = new PackageBinding(CharOperation.subarray(constantPoolName, 0, i + 1), parent, this.environment);
				if (isMissing) {
					packageBinding.tagBits |= TagBits.HasMissingType;
				}
				parent.addPackage(packageBinding);
			}
		}
		return packageBinding;
	}
}