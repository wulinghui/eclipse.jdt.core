package org.eclipse.jdt.internal.core;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.compiler.env.IModule;
import org.eclipse.jdt.internal.compiler.env.IModuleEnvironment;
import org.eclipse.jdt.internal.compiler.env.IModulePathEntry;
import org.eclipse.jdt.internal.compiler.env.PackageLookup;
import org.eclipse.jdt.internal.compiler.env.TypeLookup;

public class ProjectEntry implements IModulePathEntry, IModuleEnvironment {

	JavaProject project;
	
	public ProjectEntry(JavaProject project) {
		// 
		this.project = project;
	}
	@Override
	public IModule getModule() {
		// 
		try {
			return this.project.getModule();
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public IModuleEnvironment getLookupEnvironment() {
		// 
		return this;
	}

	@Override
	public IModuleEnvironment getLookupEnvironmentFor(IModule module) {
		//
		if (getModule() == module)
			return this;
		return null;
	}
	@Override
	public TypeLookup typeLookup() {
		// 
		return TypeLookup.Dummy;
	}
	@Override
	public PackageLookup packageLookup() {
		// 
		return PackageLookup.Dummy;
	}

}
