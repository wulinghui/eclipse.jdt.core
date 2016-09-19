/*******************************************************************************
 * Copyright (c) 2016 IBM Corporation and others.
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
import java.util.HashSet;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.env.IModule;
import org.eclipse.jdt.internal.compiler.env.IModule.IModuleReference;
import org.eclipse.jdt.internal.compiler.env.IModule.IPackageExport;
import org.eclipse.jdt.internal.compiler.env.IModuleContext;
import org.eclipse.jdt.internal.compiler.env.IModuleEnvironment;
import org.eclipse.jdt.internal.compiler.util.JRTUtil;

public class ModuleBinding extends Binding {

	public static class UnNamedModule extends ModuleBinding {

		UnNamedModule(LookupEnvironment env) {
			super(env);
		}
		public ModuleBinding[] getAllRequiredModules() {
			return NO_REQUIRES;
		}
		public IModuleContext getModuleLookupContext() {
			return IModuleContext.UNNAMED_MODULE_CONTEXT;
		}
		public IModuleContext getDependencyClosureContext() {
			return IModuleContext.UNNAMED_MODULE_CONTEXT;
		}
		public IModuleContext getModuleGraphContext() {
			return IModuleContext.UNNAMED_MODULE_CONTEXT;
		}
		public boolean canSee(PackageBinding pkg) {
			//TODO - if the package is part of a named module, then we should check if the module exports the package
			return true;
		}
	}
	public char[] moduleName;
	public IModuleReference[] requires;
	public IPackageExport[] exportedPackages;
	public TypeBinding[] uses;
	public TypeBinding[] services;
	public TypeBinding[] implementations;
	public CompilationUnitScope scope;
	public LookupEnvironment environment;
	public IModuleEnvironment moduleEnviroment;
	public int tagBits;
	private ModuleBinding[] requiredModules = null;

	public static ModuleBinding[] NO_REQUIRES = new ModuleBinding[0];
	public static IModuleReference[] NO_MODULE_REFS = new IModuleReference[0];
	public static IPackageExport[] NO_EXPORTS = new IPackageExport[0];

	ModuleBinding(LookupEnvironment env) {
		this.moduleName = ModuleEnvironment.UNNAMED;
		this.environment = env;
		this.requires = NO_MODULE_REFS;
		this.exportedPackages = NO_EXPORTS;
	}
	public ModuleBinding(IModule module, LookupEnvironment environment) {
		this.moduleName = module.name();
		this.requires = module.requires();
		if (this.requires == null)
			this.requires = NO_MODULE_REFS;
		this.exportedPackages = module.exports();
		if (this.exportedPackages == null)
			this.exportedPackages = NO_EXPORTS;
		this.environment = environment;
		this.uses = Binding.NO_TYPES;
		this.services = Binding.NO_TYPES;
		this.implementations = Binding.NO_TYPES;
		this.moduleEnviroment = module.getLookupEnvironment();
	}

	private Stream<ModuleBinding> getRequiredModules(boolean implicitOnly) {
		return Stream.of(this.requires).filter(ref -> implicitOnly ? ref.isPublic() : true)
			.map(ref -> this.environment.getModule(ref.name()))
			.filter(mod -> mod != null);
	}
	public Supplier<Collection<ModuleBinding>> dependencyCollector() {
		return () -> getRequiredModules(false)
			.collect(HashSet::new,
				(set, mod) -> {
					set.add(mod);
					set.addAll(mod.implicitDependencyCollector().get());
				},
				HashSet::addAll);
	}
	public Supplier<Collection<ModuleBinding>> implicitDependencyCollector() {
		return () -> getRequiredModules(true)
			.collect(HashSet::new,
				(set, mod) -> {
					if (set.add(mod))
						set.addAll(mod.implicitDependencyCollector().get());
				},
			HashSet::addAll);
	}
	/**
	 * Collect all implicit dependencies offered by this module
	 * Any module dependent on this module will have an implicit dependence on all other modules
	 * specified as ' requires public '
	 * @return
	 *  collection of implicit dependencies
	 */
	public Collection<ModuleBinding> getImplicitDependencies() {
		return implicitDependencyCollector().get();
	}

	/**
	 * Get all the modules required by this module
	 * All required modules include modules explicitly specified as required in the module declaration
	 * as well as implicit dependencies - those specified as ' requires public ' by one of the
	 * dependencies
	 * 
	 * @return
	 *   An array of all required modules
	 */
	public ModuleBinding[] getAllRequiredModules() {
		if (this.requiredModules != null)
			return this.requiredModules;

		Collection<ModuleBinding> allRequires = dependencyCollector().get();
		ModuleBinding javaBase = this.environment.getModule(JRTUtil.JAVA_BASE_CHAR);
		if (!CharOperation.equals(this.moduleName, TypeConstants.JAVA_BASE) && javaBase != null) {
			allRequires.add(javaBase);
		}
		return this.requiredModules = allRequires.size() > 0 ? allRequires.toArray(new ModuleBinding[allRequires.size()]) : NO_REQUIRES;
	}

	public char[] name() {
		return this.moduleName;
	}

	public boolean isPackageExported(char[] pkgName) {
		Predicate<IPackageExport> isExported = e -> CharOperation.equals(e.name(), pkgName);
		return Stream.of(this.exportedPackages).anyMatch(isExported);
	}
	/**
	 * Check if the specified package is exported to the client module by this module. True if the package appears
	 * in the list of exported packages and when the export is targeted, the module appears in the targets of the
	 * exports statement
	 * @param pkg - the package whose visibility is to be checked
	 * @param client - the module that wishes to use the package
	 * @return true if the package is visible to the client module, false otherwise
	 */
	public boolean isPackageExportedTo(PackageBinding pkg, ModuleBinding client) {
		Predicate<IPackageExport> isExported = e -> CharOperation.equals(e.name(), pkg.readableName());
		//.and(e -> e.exportedTo == null);
		Predicate<IPackageExport> isTargeted = e -> e.exportedTo() != null;
		Predicate<IPackageExport> isExportedTo = e -> 
			Stream.of(e.exportedTo()).map(ref -> this.environment.getModule(ref)).filter(m -> m != null).anyMatch(client::equals);
		return Stream.of(this.exportedPackages).anyMatch(isExported.and(isTargeted.negate().or(isExportedTo)));
	}
	/**
	 * Check if the given package is visible to this module. True when the package is exported by some
	 * required module to this module. See {@link #isPackageExportedTo(PackageBinding, ModuleBinding)}
	 * @param pkg
	 * @return True, if the package is visible to this module, false otherwise
	 */
	public boolean canSee(PackageBinding pkg) {
		return Stream.of(getAllRequiredModules()).filter(dep -> dep.isPackageExportedTo(pkg, this)).findFirst().isPresent();
	}
	
	public boolean dependsOn(ModuleBinding other) {
 		if (other == this)
 			return true;
		return Stream.of(getAllRequiredModules()).anyMatch(other::equals);
	}
	// A context representing just this module
 	public IModuleContext getModuleLookupContext() {
 		return () -> this.moduleEnviroment == null ? Stream.empty() : Stream.of(this.moduleEnviroment);
 	}
 	// A context including this module and all it's required modules
 	public IModuleContext getDependencyClosureContext() {
 		ModuleBinding[] deps = getAllRequiredModules();
 		return getModuleLookupContext().includeAll(Stream.of(deps).map(m -> m.getDependencyClosureContext()));
 	}
 	// A context that includes the entire module graph starting from this module
 	public IModuleContext getModuleGraphContext() {
 		Stream<ModuleBinding> reqs = getRequiredModules(false);
 		return this == this.environment.UnNamedModule ? IModuleContext.UNNAMED_MODULE_CONTEXT : 
 			getModuleLookupContext().includeAll(reqs.map(m -> m.getModuleGraphContext()));
 	}
	@Override
	public int kind() {
		//
		return ClassFileConstants.AccModule;
	}

	@Override
	public char[] readableName() {
		//
		return this.moduleName;
	}
}
