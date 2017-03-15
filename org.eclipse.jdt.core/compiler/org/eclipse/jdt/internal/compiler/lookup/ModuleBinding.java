/*******************************************************************************
 * Copyright (c) 2016, 2017 IBM Corporation and others.
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
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.ast.Wildcard;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.env.IModuleAwareNameEnvironment;
import org.eclipse.jdt.internal.compiler.env.IModuleContext;
import org.eclipse.jdt.internal.compiler.env.IModuleEnvironment;
import org.eclipse.jdt.internal.compiler.env.IModule;
import org.eclipse.jdt.internal.compiler.env.IModule.IModuleReference;
import org.eclipse.jdt.internal.compiler.env.IModule.IPackageExport;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.ITypeAnnotationWalker;
import org.eclipse.jdt.internal.compiler.util.HashtableOfPackage;
import org.eclipse.jdt.internal.compiler.util.JRTUtil;

public class ModuleBinding extends Binding {

	public static class UnNamedModule extends ModuleBinding {

		UnNamedModule(LookupEnvironment env) {
			super(env);
		}
		protected Stream<ModuleBinding> getRequiredModules(boolean transitiveOnly) {
			return Stream.of(this.environment.knownModules.valueTable).filter(m -> m != null);
		}
		public PackageBinding computePackageFrom(char[][] constantPoolName, boolean isMissing) {
			if (constantPoolName.length == 1)
				return this.environment.getDefaultPackage(this.moduleName);

			char[][] pkgName = CharOperation.subarray(constantPoolName, 0, constantPoolName.length - 1);
			char[] qualifiedName = CharOperation.concatWith(pkgName, '.');
			PackageBinding packageBinding = this.declaredPackages.get(qualifiedName);
			if (packageBinding == null || packageBinding == LookupEnvironment.TheNotFoundPackage) {
				packageBinding = getRequiredModules(false).map(m -> m.getExportedPackage(qualifiedName)).filter(p -> p != null).findFirst().orElse(null);
				if(packageBinding != null)
					return packageBinding;
				packageBinding = new PackageBinding(pkgName, null, this.environment);
				this.declaredPackages.put(qualifiedName, packageBinding);
				if (isMissing) {
					packageBinding.tagBits |= TagBits.HasMissingType;
				}
			}
			return packageBinding;
		}
//		public ModuleBinding[] getAllRequiredModules() {
//			List<ModuleBinding> allModules = Stream.of(this.environment.knownModules.valueTable).filter(m -> m != null).collect(Collectors.toList());
//			allModules.add(this.environment.getModule(TypeConstants.JAVA_BASE));
//			return allModules.toArray(new ModuleBinding[allModules.size()]);
//		}
//		public IModuleContext getModuleLookupContext() {
//			return IModuleContext.UNNAMED_MODULE_CONTEXT;
//		}
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
	public IPackageExport[] exports;
	public TypeBinding[] uses;
	public TypeBinding[] services;
	public TypeBinding[] implementations;
	public CompilationUnitScope scope;
	public LookupEnvironment environment;
	public int tagBits;
	private ModuleBinding[] requiredModules = null;
	private boolean isAuto;

	HashtableOfPackage declaredPackages;
	HashtableOfPackage exportedPackages;

	public static ModuleBinding[] NO_REQUIRES = new ModuleBinding[0];

	ModuleBinding(LookupEnvironment env) {
		this.moduleName = ModuleEnvironment.UNNAMED;
		this.environment = env;
		this.requires = IModule.NO_MODULE_REFS;
		this.exports = IModule.NO_EXPORTS;
		this.declaredPackages = new HashtableOfPackage(0);
		this.exportedPackages = new HashtableOfPackage(0);
	}
	public ModuleBinding(IModule module, LookupEnvironment environment) {
		this.moduleName = module.name();
		IModule decl = module;
		this.requires = decl.requires();
		if (this.requires == null)
			this.requires = IModule.NO_MODULE_REFS;
		this.exports = decl.exports();
		if (this.exports == null)
			this.exports = IModule.NO_EXPORTS;
		this.environment = environment;
		this.uses = Binding.NO_TYPES;
		this.services = Binding.NO_TYPES;
		this.implementations = Binding.NO_TYPES;
		this.declaredPackages = new HashtableOfPackage(5);
		this.exportedPackages = new HashtableOfPackage(5);
		this.isAuto = module.isAutomatic();
	}

	protected Stream<ModuleBinding> getRequiredModules(boolean transitiveOnly) {
		return Stream.of(this.requires).filter(ref -> transitiveOnly ? ref.isTransitive() : true)
			.map(ref -> this.environment.getModule(ref.name()))
			.filter(mod -> mod != null);
	}
	private void collectAllDependencies(Set<ModuleBinding> deps) {
		getRequiredModules(false).forEach(m -> {
			if (deps.add(m)) {
				m.collectAllDependencies(deps);
			}
		});
	}
	private void collectTransitiveDependencies(Set<ModuleBinding> deps) {
		getRequiredModules(true).forEach(m -> {
			if (deps.add(m)) {
				m.collectTransitiveDependencies(deps);
			}
		});
	}

	// All modules required by this module, either directly or indirectly
	public Supplier<Collection<ModuleBinding>> dependencyGraphCollector() {
		return () -> getRequiredModules(false)
			.collect(HashSet::new,
				(set, mod) -> {
					set.add(mod);
					mod.collectAllDependencies(set);
				},
				HashSet::addAll);
	}
	// All direct and transitive dependencies of this module
	public Supplier<Collection<ModuleBinding>> dependencyCollector() {
		return () -> getRequiredModules(false)
			.collect(HashSet::new,
				(set, mod) -> {
					set.add(mod);
					mod.collectTransitiveDependencies(set);
				},
				HashSet::addAll);
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

	/**
	 * Check if the specified package is exported to the client module by this module. True if the package appears
	 * in the list of exported packages and when the export is targeted, the module appears in the targets of the
	 * exports statement
	 * @param pkg - the package whose visibility is to be checked
	 * @param client - the module that wishes to use the package
	 * @return true if the package is visible to the client module, false otherwise
	 */
	public boolean isPackageExportedTo(PackageBinding pkg, ModuleBinding client) {
		PackageBinding resolved = getDeclaredPackage(pkg.compoundName);
		if (resolved != null) {
			if (this.isAuto || this == client) { // all packages are exported by an automatic module
				return true;
			}
			Predicate<IPackageExport> isExportedTo = e -> 
				Stream.of(e.targets()).map(ref -> this.environment.getModule(ref)).filter(m -> m != null).anyMatch(client::equals);
			
			return Stream.of(this.exports).filter(e -> CharOperation.equals(pkg.readableName(), e.name()))
					.anyMatch(e -> {
						if (!e.isQualified())
							return true;
						return client != this.environment.UnNamedModule && isExportedTo.test(e);
					});
		}
		return false;
	}
	public PackageBinding getTopLevelPackage(char[] name) {
		// return package binding if there exists a package named name in this module's context and it can be seen by this module
		// A package can be seen by this module if it declares the package or someone exports that package to it
		PackageBinding existing = this.environment.getPackage0(name);
		if (existing != null) {
			if (existing == LookupEnvironment.TheNotFoundPackage)
				return null;
		}
		if (declaresPackage(null, name)) {
			return new PackageBinding(name, this.environment);
		} else {
			return Stream.of(getAllRequiredModules()).sorted((m1, m2) -> m1.requires.length - m2.requires.length)
					.map(m -> {
						PackageBinding binding = m.getExportedPackage(name);
						if (binding != null && m.isPackageExportedTo(binding, this)) {
							return m.declaredPackages.get(name);
						}
						return null;
					})
			.filter(p -> p != null).findFirst().orElse(null);
		}
	}
	// Given parent is declared in this module, see if there is sub package named name declared in this module
//	private PackageBinding getDeclaredPackage(PackageBinding parent, char[] name) {
//		PackageBinding pkg = parent.getPackage0(name);
//		if (pkg != null && pkg != LookupEnvironment.TheNotFoundPackage)
//			return pkg;
//		if (declaresPackage(parent.compoundName, name)) {
//			char[][] subPkgCompoundName = CharOperation.arrayConcat(parent.compoundName, name);
//			PackageBinding binding = new PackageBinding(subPkgCompoundName, parent, this.environment);
//			parent.addPackage(binding);
//			this.declaredPackages.put(binding.readableName(), binding);
//			return binding;
//		}
//		// TODO: Situation can probably improved by adding NOtFoundPackage to this.declaredPackages 
//		//parent.addNotFoundPackage(name); Not a package in this module does not mean not a package at all
//		return null;
//	}
//	public PackageBinding getDeclaredPackage(char[] qualifiedName) {
//		// return package binding if there exists a package named name in this module
//		if (qualifiedName == null || qualifiedName.length == 0) {
//			return this.environment.getDefaultPackage(this.moduleName);
//		}
//
////		PackageBinding pkg = parent.getPackage0(name);
////		if (pkg != null && pkg != LookupEnvironment.TheNotFoundPackage)
////			return pkg;
//		if (declaresPackage(qualifiedName)) {
//			char[][] subPkgCompoundName = CharOperation.splitOn('.', qualifiedName);
//			PackageBinding binding = new PackageBinding(subPkgCompoundName, null, this.environment);
//			//parent.addPackage(binding);
//			this.declaredPackages.put(binding.readableName(), binding);
//			return binding;
//		}
//		// TODO: Situation can probably improved by adding NOtFoundPackage to this.declaredPackages 
//		//parent.addNotFoundPackage(name); Not a package in this module does not mean not a package at all
//		return null;
//	}
	public PackageBinding getDeclaredPackage(char[][] name) {
		// return package binding if there exists a package named name in this module
		if (name == null || name.length == 0) {
			return this.environment.getDefaultPackage(this.moduleName);
		}
		char[] qualifiedName = CharOperation.concatWith(name, '.');
		PackageBinding binding = this.declaredPackages.get(qualifiedName);
		if (binding != null) {
			if (binding == LookupEnvironment.TheNotFoundPackage)
				return null;
			return binding;
		}
		if (declaresPackage(name)) {
			binding = new PackageBinding(name, null, this.environment);
		} else {
			binding = LookupEnvironment.TheNotFoundPackage;
		}
		this.declaredPackages.put(qualifiedName, binding);
		return binding;
//		PackageBinding parent = null;
//		PackageBinding existing = this.environment.getPackage0(name[0]); 
//		if (existing != null) { // known top level package
//			if (existing == LookupEnvironment.TheNotFoundPackage)
//				return null;
//			parent = existing;
//		}
//		if (parent == null) {
//			if (declaresPackage(null, name[0])) { // unknown as yet, but declared in this module
//				parent = new PackageBinding(name[0], this.environment);
//				this.declaredPackages.put(name[0], parent);
//			} else {
//				this.declaredPackages.put(name[0], LookupEnvironment.TheNotFoundPackage); // not declared in this module
//				return null;
//			}
//		} else if (!declaresPackage(null, name[0])) { // already seen before, but not declared in this module
//			return null;
//		}
//		// check each sub package
//		for (int i = 1; i < name.length; i++) {
//			PackageBinding binding = getDeclaredPackage(parent, name[i]); 
//			if (binding == null) {
//				return null;
//			}
//			parent = binding;
//		}
//		return parent;
	}
	public PackageBinding getExportedPackage(char[] qualifiedPackageName) {
		PackageBinding existing = this.exportedPackages.get(qualifiedPackageName);
		if (existing != null && existing != LookupEnvironment.TheNotFoundPackage)
			return existing;
		if (this.isAuto) { // all packages are exported by an automatic module
			return getDeclaredPackage(CharOperation.splitOn('.', qualifiedPackageName));
		}
		//Resolve exports to see if the package or a sub package is exported
		return Stream.of(this.exports).sorted((e1, e2) -> e1.name().length - e2.name().length)
		.filter(e -> CharOperation.equals(qualifiedPackageName, e.name())) // TODO: improve this
		.map(e -> {
			PackageBinding binding = getDeclaredPackage(CharOperation.splitOn('.', e.name()));
			if (binding != null) {
				this.exportedPackages.put(e.name(), binding);
				return binding;
			}
			return null;
		}).filter(p -> p != null).findFirst().orElse(null);
	}
	public boolean declaresPackage(PackageBinding p) {
		PackageBinding pkg = this.declaredPackages.get(p.readableName());
		if (pkg == null) {
			pkg = getDeclaredPackage(p.compoundName);
			if (pkg == p) {
				//this.declaredPackages.put(p.readableName(), p);
				return true;
			}
		}
		return pkg == p;
	}
	public boolean declaresPackage(char[][] qualifiedName) {
		char[][] parentPkgName = CharOperation.subarray(qualifiedName, 0, qualifiedName.length - 1);
		return declaresPackage(parentPkgName, qualifiedName[qualifiedName.length - 1]);
	}
	public boolean declaresPackage(char[][] parentPackageName, char[] name) {
//		char[] qualifiedName = CharOperation.concatWith(parentPackageName, name, '.');
//		PackageBinding declared = this.declaredPackages.get(qualifiedName);
//		if (declared != null) {
//			if (declared == LookupEnvironment.TheNotFoundPackage)
//				return false;
//			else
//				return true;
//		}
		INameEnvironment nameEnvironment = this.environment.nameEnvironment;
		boolean isPackage = false;
		if (nameEnvironment instanceof IModuleAwareNameEnvironment) {
			isPackage = ((IModuleAwareNameEnvironment)nameEnvironment).isPackage(parentPackageName, name, getModuleLookupContext());
		} else {
			isPackage = nameEnvironment.isPackage(parentPackageName, name);
		}
//		if (isPackage) {
//			this.declaredPackages.put(qualifiedName, new PackageBinding(CharOperation.arrayConcat(parentPackageName, name), null, this.environment));
//		} else {
//			this.declaredPackages.put(qualifiedName, LookupEnvironment.TheNotFoundPackage);
//		}
		return isPackage;
	}
	public PackageBinding getPackage(char[][] parentPackageName, char[] packageName) {
		// Returns a package binding if there exists such a package in the context of this module and it is observable
		// A package is observable if it is declared in this module or it is exported by some required module
//		PackageBinding binding = null;
//		if (parentPackageName == null || parentPackageName.length == 0) {
//			binding = getTopLevelPackage(packageName);
//		} else {
//			binding = getDeclaredPackage(parentPackageName);
//			if (binding != null && binding != LookupEnvironment.TheNotFoundPackage) {
//				binding = getDeclaredPackage(binding, packageName);
//				if (binding != null)
//					return binding;
//			}
//		}
//		if (binding == null) {
//			char[] qualifiedPackageName = CharOperation.concatWith(parentPackageName, packageName, '.');
//			return Stream.of(getAllRequiredModules())
//					.map(m -> {
//						if (m.isAuto) {
//							return m.getPackage(parentPackageName, packageName);
//						}
//						PackageBinding p = m.getExportedPackage(qualifiedPackageName);
//						if (p != null && m.isPackageExportedTo(p, this)) {
//							return m.declaredPackages.get(qualifiedPackageName);
//						}
//						return null;
//					})
//			.filter(p -> p != null).findFirst().orElse(null);
//		}
//		return binding;
		return getPackage(CharOperation.arrayConcat(parentPackageName, packageName));
	}
	/**
	 * Check if the given package is visible to this module. True when the package is declared in
	 * this module or exported by some required module to this module.
	 * See {@link #isPackageExportedTo(PackageBinding, ModuleBinding)}
	 * 
	 * @param pkg
	 * 
	 * @return True, if the package is visible to this module, false otherwise
	 */
	public boolean canSee(PackageBinding pkg) {
		return declaresPackage(pkg) || Stream.of(getAllRequiredModules()).anyMatch(
				dep -> dep.isPackageExportedTo(pkg, ModuleBinding.this)
		);
	}
	public boolean dependsOn(ModuleBinding other) {
 		if (other == this)
 			return true;
		return Stream.of(getAllRequiredModules()).anyMatch(other::equals);
	}
	// A context representing just this module
 	public IModuleContext getModuleLookupContext() {
 		IModuleAwareNameEnvironment env = (IModuleAwareNameEnvironment) this.environment.nameEnvironment;
 		IModuleEnvironment moduleEnvironment = env.getModuleEnvironmentFor(this.moduleName);
 		return () -> moduleEnvironment == null ? Stream.empty() : Stream.of(moduleEnvironment);
 	}
 	// A context including this module and all it's required modules
 	public IModuleContext getDependencyClosureContext() {
 		if (this.isAuto)
 			return IModuleContext.UNNAMED_MODULE_CONTEXT;
 		ModuleBinding[] deps = getAllRequiredModules();
 		return getModuleLookupContext().includeAll(Stream.of(deps).map(m -> m.getModuleLookupContext()));
 	}
 	// A context that includes the entire module graph starting from this module
 	public IModuleContext getModuleGraphContext() {
 		Stream<ModuleBinding> reqs = getRequiredModules(false);
 		return getModuleLookupContext().includeAll(reqs.map(m -> m.getModuleGraphContext()).distinct());
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

	public String toString() {
		StringBuffer buffer = new StringBuffer(30);
		buffer.append("module " + new String(readableName())); //$NON-NLS-1$
		if (this.requires.length > 0) {
			buffer.append("\n/*    requires    */\n"); //$NON-NLS-1$
			for (int i = 0; i < this.requires.length; i++) {
				buffer.append("\n\t"); //$NON-NLS-1$
				if (this.requires[i].isTransitive())
					buffer.append("public "); //$NON-NLS-1$
				buffer.append(this.requires[i].name());
			}
		} else {
			buffer.append("\nNo Requires"); //$NON-NLS-1$
		}
		if (this.exports.length > 0) {
			buffer.append("\n/*    exports    */\n"); //$NON-NLS-1$
			for (int i = 0; i < this.exports.length; i++) {
				IPackageExport export = this.exports[i];
				buffer.append("\n\t"); //$NON-NLS-1$
				buffer.append(export.name());
				char[][] targets = export.targets();
				if (targets != null) {
					buffer.append("to "); //$NON-NLS-1$
					for (int j = 0; j < targets.length; j++) {
						if (j != 0)
							buffer.append(", "); //$NON-NLS-1$
						buffer.append(targets[j]);
					}
				}
			}
		} else {
			buffer.append("\nNo Exports"); //$NON-NLS-1$
		}
		if (this.uses != null && this.uses.length > 0) {
			buffer.append("\n/*    uses    /*\n"); //$NON-NLS-1$
			for (int i = 0; i < this.uses.length; i++) {
				buffer.append("\n\t"); //$NON-NLS-1$
				buffer.append(this.uses[i].debugName());
			}
		} else {
			buffer.append("\nNo Uses"); //$NON-NLS-1$
		}
		if (this.services != null && this.services.length > 0) {
			buffer.append("\n/*    Services    */\n"); //$NON-NLS-1$
			for (int i = 0; i < this.services.length; i++) {
				buffer.append("\n\t"); //$NON-NLS-1$
				buffer.append("provides "); //$NON-NLS-1$
				buffer.append(this.services[i].debugName());
				buffer.append(" with "); //$NON-NLS-1$
				buffer.append(this.implementations[i].debugName());
			}
		} else {
			buffer.append("\nNo Services"); //$NON-NLS-1$
		}
		return buffer.toString();
	}
	public PackageBinding getPackage(char[][] compoundName) {
		PackageBinding binding = getDeclaredPackage(compoundName);
		if (binding != null && binding != LookupEnvironment.TheNotFoundPackage) {
			return binding;
		}
		return Stream.of(getAllRequiredModules()).map(m -> {
			PackageBinding p = m.getDeclaredPackage(compoundName);
			if (p != null && p.isValidBinding()) {
				if (m.isPackageExportedTo(p, this)) {
					return p;
				} else {
					return new ConcealedPackageBinding(compoundName, this.environment, m);
				}
			}
			return null;
		}).filter(p -> p != null).reduce(null, (p1, p2) -> {
			if (p1 == null)
				return p2;
			if (p1.problemId() == ProblemReasons.Ambiguous)
				return p1;
			if (!p2.isValidBinding()) {
				return p1;
			} else {
				return p1.isValidBinding() ? new ConflictPackageBinding(compoundName, this.environment) : p2;
			}
		});
	}
	public ReferenceBinding getType(char[][] compoundName) {
		ReferenceBinding binding = null;
		char[][] parentPackageName =  CharOperation.subarray(compoundName, 0, compoundName.length - 1);
		PackageBinding pkg = getPackage(parentPackageName);
		if (pkg != null) {
			binding = pkg.getType0(compoundName[compoundName.length - 1]);
		}
		return binding;
	}
	public ReferenceBinding findType(char[][] compoundName) {
		// TODO
		ReferenceBinding binding = null;
		char[][] parentPackageName =  CharOperation.subarray(compoundName, 0, compoundName.length - 1);
		PackageBinding pkg = getPackage(parentPackageName);
		if (pkg != null) {
			binding = pkg.getType(compoundName[compoundName.length - 1], this.moduleName);
		}
		else {
			binding = this.environment.askForType(compoundName, this.moduleName);
		}
		return binding;
	}
	public Binding getTypeOrPackage(char[][] name) {
		ReferenceBinding type = findType(name);
		if (type != null && type.isValidBinding())
			return type;
		PackageBinding packageBinding = getPackage(name);
		if (packageBinding != null) {
			if (packageBinding.isValidBinding() || packageBinding.problemId() == ProblemReasons.Ambiguous)
				return packageBinding;
		}
			
		return null;
	}
	
	public PackageBinding createPackage(char[][] compoundName) {
		for(int i = 1; i < compoundName.length; i++) {
//			char[][] typeName = CharOperation.subarray(compoundName, 0, i);
			ReferenceBinding type;// = findType(typeName);
//			if (type != null && type != LookupEnvironment.TheNotFoundType && !(type instanceof UnresolvedReferenceBinding))
//				return null;
			char[][] packageName = CharOperation.subarray(compoundName, 0, i);
			PackageBinding pkg = this.declaredPackages.get(CharOperation.concatWith(packageName, '.'));
			if (pkg != null) {
				type = pkg.getType0(compoundName[i]);
				if (type != null && type != LookupEnvironment.TheNotFoundType && !(type instanceof UnresolvedReferenceBinding))
					return null;
			}
		}
		char[] qualifiedName = CharOperation.concatWith(compoundName, '.');
		PackageBinding packageBinding = this.declaredPackages.get(qualifiedName);
		if (packageBinding == null || packageBinding == LookupEnvironment.TheNotFoundPackage) {
			packageBinding = new PackageBinding(compoundName, null, this.environment);
			this.declaredPackages.put(qualifiedName, packageBinding);
		}
		//PackageBinding packageBinding = getPackage(compoundName);
		return packageBinding;
	}
	public ReferenceBinding getCachedType(char[][] compoundName) {
		char[][] pkgName = CharOperation.subarray(compoundName, 0, compoundName.length - 1);
		//PackageBinding pkg = this.declaredPackages.get(CharOperation.concatWith(pkgName, '.'));
		PackageBinding pkg = getPackage(pkgName);
		if (pkg != null) {
			return pkg.getType0(compoundName[compoundName.length - 1]);
		}
		return null;
	}
	public PackageBinding computePackageFrom(char[][] constantPoolName, boolean isMissing) {
		if (constantPoolName.length == 1)
			return this.environment.getDefaultPackage(this.moduleName);

		char[][] pkgName = CharOperation.subarray(constantPoolName, 0, constantPoolName.length - 1);
		char[] qualifiedName = CharOperation.concatWith(pkgName, '.');
		PackageBinding packageBinding = this.declaredPackages.get(qualifiedName);
		if (packageBinding == null || packageBinding == LookupEnvironment.TheNotFoundPackage) {
			packageBinding = new PackageBinding(pkgName, null, this.environment);
			this.declaredPackages.put(qualifiedName, packageBinding);
			if (isMissing) {
				packageBinding.tagBits |= TagBits.HasMissingType;
			}
		}
		return packageBinding;
	}
	public TypeBinding getTypeFromTypeSignature(SignatureWrapper wrapper, TypeVariableBinding[] staticVariables, ReferenceBinding enclosingType, 
			char[][][] missingTypeNames, ITypeAnnotationWalker walker) 
	{
		// TypeVariableSignature = 'T' Identifier ';'
		// ArrayTypeSignature = '[' TypeSignature
		// ClassTypeSignature = 'L' Identifier TypeArgs(optional) ';'
		//   or ClassTypeSignature '.' 'L' Identifier TypeArgs(optional) ';'
		// TypeArgs = '<' VariantTypeSignature VariantTypeSignatures '>'
		int dimension = 0;
		while (wrapper.signature[wrapper.start] == '[') {
			wrapper.start++;
			dimension++;
		}
		// annotations on dimensions?
		AnnotationBinding [][] annotationsOnDimensions = null;
		if (dimension > 0 && walker != ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER) {
			for (int i = 0; i < dimension; i++) {
				AnnotationBinding [] annotations = BinaryTypeBinding.createAnnotations(walker.getAnnotationsAtCursor(0), this.environment, missingTypeNames, this);
				if (annotations != Binding.NO_ANNOTATIONS) { 
					if (annotationsOnDimensions == null)
						annotationsOnDimensions = new AnnotationBinding[dimension][];
						annotationsOnDimensions[i] = annotations;
				}
				walker = walker.toNextArrayDimension();
			}
		}
		if (wrapper.signature[wrapper.start] == 'T') {
		    int varStart = wrapper.start + 1;
		    int varEnd = wrapper.computeEnd();
			for (int i = staticVariables.length; --i >= 0;)
				if (CharOperation.equals(staticVariables[i].sourceName, wrapper.signature, varStart, varEnd))
					return getTypeFromTypeVariable(staticVariables[i], dimension, annotationsOnDimensions, walker, missingTypeNames);
		    ReferenceBinding initialType = enclosingType;
			do {
				TypeVariableBinding[] enclosingTypeVariables;
				if (enclosingType instanceof BinaryTypeBinding) { // compiler normal case, no eager resolution of binary variables
					enclosingTypeVariables = ((BinaryTypeBinding)enclosingType).typeVariables; // do not trigger resolution of variables
				} else { // codepath only use by codeassist for decoding signatures
					enclosingTypeVariables = enclosingType.typeVariables();
				}
				for (int i = enclosingTypeVariables.length; --i >= 0;)
					if (CharOperation.equals(enclosingTypeVariables[i].sourceName, wrapper.signature, varStart, varEnd))
						return getTypeFromTypeVariable(enclosingTypeVariables[i], dimension, annotationsOnDimensions, walker, missingTypeNames);
			} while ((enclosingType = enclosingType.enclosingType()) != null);
			this.environment.problemReporter.undefinedTypeVariableSignature(CharOperation.subarray(wrapper.signature, varStart, varEnd), initialType);
			return null; // cannot reach this, since previous problem will abort compilation
		}
		boolean isParameterized;
		TypeBinding type = getTypeFromSignature(wrapper.signature, wrapper.start, wrapper.computeEnd(), isParameterized = (wrapper.end == wrapper.bracket), enclosingType, missingTypeNames, walker);

		if (!isParameterized)
			return dimension == 0 ? type : this.environment.createArrayType(type, dimension, AnnotatableTypeSystem.flattenedAnnotations(annotationsOnDimensions));

		// type must be a ReferenceBinding at this point, cannot be a BaseTypeBinding or ArrayTypeBinding
		ReferenceBinding actualType = (ReferenceBinding) type;
		if (actualType instanceof UnresolvedReferenceBinding)
			if (actualType.depth() > 0)
				actualType = (ReferenceBinding) BinaryTypeBinding.resolveType(actualType, this.environment, false /* no raw conversion */); // must resolve member types before asking for enclosingType
		ReferenceBinding actualEnclosing = actualType.enclosingType();

		ITypeAnnotationWalker savedWalker = walker;
		if(actualType.depth() > 0) {
			int nonStaticNestingLevels = this.environment.countNonStaticNestingLevels(actualType);
			for (int i = 0; i < nonStaticNestingLevels; i++) {
				walker = walker.toNextNestedType();
			}
		}

		TypeBinding[] typeArguments = getTypeArgumentsFromSignature(wrapper, staticVariables, enclosingType, actualType, missingTypeNames, walker);
		ReferenceBinding currentType = this.environment.createParameterizedType(actualType, typeArguments, actualEnclosing);
		ReferenceBinding plainCurrent = actualType;

		while (wrapper.signature[wrapper.start] == '.') {
			wrapper.start++; // skip '.'
			int memberStart = wrapper.start;
			char[] memberName = wrapper.nextWord();
			plainCurrent = (ReferenceBinding) BinaryTypeBinding.resolveType(plainCurrent, this.environment, false);
			ReferenceBinding memberType = plainCurrent.getMemberType(memberName);
			// need to protect against the member type being null when the signature is invalid
			if (memberType == null)
				this.environment.problemReporter.corruptedSignature(currentType, wrapper.signature, memberStart); // aborts
			if(memberType.isStatic()) {
				// may happen for class files generated by eclipse before bug 460491 was fixed. 
				walker = savedWalker;
			} else {
				walker = walker.toNextNestedType();
			}
			if (wrapper.signature[wrapper.start] == '<') {
				wrapper.start++; // skip '<'
				typeArguments = getTypeArgumentsFromSignature(wrapper, staticVariables, enclosingType, memberType, missingTypeNames, walker);
			} else {
				typeArguments = null;
			}
			if (typeArguments != null || 											// has type arguments, or ... 
					(!memberType.isStatic() && currentType.isParameterizedType())) 	// ... can see type arguments of enclosing
			{
				if (memberType.isStatic())
					currentType = plainCurrent; // ignore bogus parameterization of enclosing
				currentType = this.environment.createParameterizedType(memberType, typeArguments, currentType);
			} else {
				currentType = memberType;
			}
			plainCurrent = memberType;
		}
		wrapper.start++; // skip ';'
		TypeBinding annotatedType = this.environment.annotateType(currentType, savedWalker, missingTypeNames, this);
		return dimension == 0 ? annotatedType : this.environment.createArrayType(annotatedType, dimension, AnnotatableTypeSystem.flattenedAnnotations(annotationsOnDimensions));
	}
	private TypeBinding[] getTypeArgumentsFromSignature(SignatureWrapper wrapper, TypeVariableBinding[] staticVariables, ReferenceBinding enclosingType, ReferenceBinding genericType,
			char[][][] missingTypeNames, ITypeAnnotationWalker walker)
	{
		java.util.ArrayList<TypeBinding> args = new java.util.ArrayList<>(2);
		int rank = 0;
		do {
			args.add(getTypeFromVariantTypeSignature(wrapper, staticVariables, enclosingType, genericType, rank, missingTypeNames,
						walker.toTypeArgument(rank++)));
		} while (wrapper.signature[wrapper.start] != '>');
		wrapper.start++; // skip '>'
		TypeBinding[] typeArguments = new TypeBinding[args.size()];
		args.toArray(typeArguments);
		return typeArguments;
	}
	TypeBinding getTypeFromVariantTypeSignature(
			SignatureWrapper wrapper,
			TypeVariableBinding[] staticVariables,
			ReferenceBinding enclosingType,
			ReferenceBinding genericType,
			int rank,
			char[][][] missingTypeNames,
			ITypeAnnotationWalker walker) {
		// VariantTypeSignature = '-' TypeSignature
		//   or '+' TypeSignature
		//   or TypeSignature
		//   or '*'
		switch (wrapper.signature[wrapper.start]) {
			case '-' :
				// ? super aType
				wrapper.start++;
				TypeBinding bound = getTypeFromTypeSignature(wrapper, staticVariables, enclosingType, missingTypeNames, walker.toWildcardBound());
				AnnotationBinding [] annotations = BinaryTypeBinding.createAnnotations(walker.getAnnotationsAtCursor(-1), this.environment, missingTypeNames, this);
				return this.environment.typeSystem.getWildcard(genericType, rank, bound, null /*no extra bound*/, Wildcard.SUPER, annotations);
			case '+' :
				// ? extends aType
				wrapper.start++;
				bound = getTypeFromTypeSignature(wrapper, staticVariables, enclosingType, missingTypeNames, walker.toWildcardBound());
				annotations = BinaryTypeBinding.createAnnotations(walker.getAnnotationsAtCursor(-1), this.environment, missingTypeNames, this);
				return this.environment.typeSystem.getWildcard(genericType, rank, bound, null /*no extra bound*/, Wildcard.EXTENDS, annotations);
			case '*' :
				// ?
				wrapper.start++;
				annotations = BinaryTypeBinding.createAnnotations(walker.getAnnotationsAtCursor(-1), this.environment, missingTypeNames, this);
				return this.environment.typeSystem.getWildcard(genericType, rank, null, null /*no extra bound*/, Wildcard.UNBOUND, annotations);
			default :
				return getTypeFromTypeSignature(wrapper, staticVariables, enclosingType, missingTypeNames, walker);
		}
	}
	TypeBinding getTypeFromSignature(char[] signature, int start, int end, boolean isParameterized, TypeBinding enclosingType, 
			char[][][] missingTypeNames, ITypeAnnotationWalker walker)
	{
		int dimension = 0;
		while (signature[start] == '[') {
			start++;
			dimension++;
		}
		// annotations on dimensions?
		AnnotationBinding [][] annotationsOnDimensions = null;
		if (dimension > 0 && walker != ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER) {
			for (int i = 0; i < dimension; i++) {
				AnnotationBinding [] annotations = BinaryTypeBinding.createAnnotations(walker.getAnnotationsAtCursor(0), this.environment, missingTypeNames, this);
				if (annotations != Binding.NO_ANNOTATIONS) { 
					if (annotationsOnDimensions == null)
						annotationsOnDimensions = new AnnotationBinding[dimension][];
						annotationsOnDimensions[i] = annotations;
				}
				walker = walker.toNextArrayDimension();
			}
		}
		
		if (end == -1)
			end = signature.length - 1;

		// Just switch on signature[start] - the L case is the else
		TypeBinding binding = null;
		if (start == end) {
			switch (signature[start]) {
				case 'I' :
					binding = TypeBinding.INT;
					break;
				case 'Z' :
					binding = TypeBinding.BOOLEAN;
					break;
				case 'V' :
					binding = TypeBinding.VOID;
					break;
				case 'C' :
					binding = TypeBinding.CHAR;
					break;
				case 'D' :
					binding = TypeBinding.DOUBLE;
					break;
				case 'B' :
					binding = TypeBinding.BYTE;
					break;
				case 'F' :
					binding = TypeBinding.FLOAT;
					break;
				case 'J' :
					binding = TypeBinding.LONG;
					break;
				case 'S' :
					binding = TypeBinding.SHORT;
					break;
				default :
					this.environment.problemReporter.corruptedSignature(enclosingType, signature, start);
					// will never reach here, since error will cause abort
			}
		} else {
			binding = getTypeFromConstantPoolName(signature, start + 1, end, isParameterized, missingTypeNames); // skip leading 'L' or 'T'
		}
		
		if (isParameterized) {
			if (dimension != 0)
				throw new IllegalStateException();
			return binding;
		}
		
		if (walker != ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER) {
			binding = this.environment.annotateType(binding, walker, missingTypeNames, this);
		}
		
		if (dimension != 0)
			binding =  this.environment.typeSystem.getArrayType(binding, dimension, AnnotatableTypeSystem.flattenedAnnotations(annotationsOnDimensions));
		
		return binding;
	}
	ReferenceBinding getTypeFromConstantPoolName(char[] signature, int start, int end, boolean isParameterized, char[][][] missingTypeNames, ITypeAnnotationWalker walker) {
		if (end == -1)
			end = signature.length;
		char[][] compoundName = CharOperation.splitOn('/', signature, start, end);
		boolean wasMissingType = false;
		if (missingTypeNames != null) {
			for (int i = 0, max = missingTypeNames.length; i < max; i++) {
				if (CharOperation.equals(compoundName, missingTypeNames[i])) {
					wasMissingType = true;
					break;
				}
			}
		}
		ReferenceBinding binding = getTypeFromCompoundName(compoundName, isParameterized, wasMissingType);
		if (walker != ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER) {
			binding = (ReferenceBinding) this.environment.annotateType(binding, walker, missingTypeNames, this);
		}
		return binding;
	}

	ReferenceBinding getTypeFromConstantPoolName(char[] signature, int start, int end, boolean isParameterized, char[][][] missingTypeNames) {
		return getTypeFromConstantPoolName(signature, start, end, isParameterized, missingTypeNames, ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER);
	}
	private ReferenceBinding getTypeFromCompoundName(char[][] compoundName, boolean isParameterized, boolean wasMissingType) {
		ReferenceBinding binding = getCachedType(compoundName);
		if (binding == null) {
			PackageBinding packageBinding = this.environment.computePackageFrom(compoundName, false /* valid pkg */, this.moduleName);
			binding = new UnresolvedReferenceBinding(compoundName, packageBinding);
			if (wasMissingType) {
				binding.tagBits |= TagBits.HasMissingType; // record it was bound to a missing type
			}
			packageBinding.addType(binding);
		} else if (binding == LookupEnvironment.TheNotFoundType) {
			// report the missing class file first
			if (!wasMissingType) {
				/* Since missing types have been already been complained against while producing binaries, there is no class path 
				 * misconfiguration now that did not also exist in some equivalent form while producing the class files which encode 
				 * these missing types. So no need to bark again. Note that wasMissingType == true signals a type referenced in a .class 
				 * file which could not be found when the binary was produced. See https://bugs.eclipse.org/bugs/show_bug.cgi?id=364450 */
				this.environment.problemReporter.isClassPathCorrect(compoundName, this.environment.unitBeingCompleted, this.environment.missingClassFileLocation);
			}
			// create a proxy for the missing BinaryType
			binding = this.environment.createMissingType(null, compoundName);
		} else if (!isParameterized) {
		    // check raw type, only for resolved types
	        binding = (ReferenceBinding) this.environment.convertUnresolvedBinaryToRawType(binding);
		}
		return binding;
	}
	private TypeBinding getTypeFromTypeVariable(TypeVariableBinding typeVariableBinding, int dimension, AnnotationBinding [][] annotationsOnDimensions, ITypeAnnotationWalker walker, char [][][] missingTypeNames) {
		AnnotationBinding [] annotations = BinaryTypeBinding.createAnnotations(walker.getAnnotationsAtCursor(-1), this.environment, missingTypeNames, this);
		if (annotations != null && annotations != Binding.NO_ANNOTATIONS)
			typeVariableBinding = (TypeVariableBinding) this.environment.createAnnotatedType(typeVariableBinding, new AnnotationBinding [][] { annotations });

		if (dimension == 0) {
			return typeVariableBinding;
		}
		return this.environment.typeSystem.getArrayType(typeVariableBinding, dimension, AnnotatableTypeSystem.flattenedAnnotations(annotationsOnDimensions));
	}
}
