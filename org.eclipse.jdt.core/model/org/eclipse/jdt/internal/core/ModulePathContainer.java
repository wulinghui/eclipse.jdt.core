package org.eclipse.jdt.internal.core;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.env.IModule;
import org.eclipse.jdt.internal.compiler.env.IModule.IModuleReference;

public class ModulePathContainer implements IClasspathContainer{

	private IJavaProject project;

	public ModulePathContainer(IJavaProject project) {
		this.project = project;
	}
	@Override
	public IClasspathEntry[] getClasspathEntries() {
		//
		List<IClasspathEntry> entries = new ArrayList<>();
		ModuleSourcePathManager manager = JavaModelManager.getModulePathManager();
		try {
			IModule module = ((JavaProject)this.project).getModule();
			if (module == null)
				return new IClasspathEntry[0];
			for (IModuleReference ref : module.requires()) {
				JavaProject refRoot = manager.getModuleRoot(CharOperation.charToString(ref.name()));
				if (refRoot == null)
					continue;
				IPath path = refRoot.getPath();
				entries.add(JavaCore.newProjectEntry(path, ref.isPublic()));
			}
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return entries.toArray(new IClasspathEntry[entries.size()]);
	}

	@Override
	public String getDescription() {
		// 
		return "Module path";
	}

	@Override
	public int getKind() {
		// 
		return K_APPLICATION;
	}

	@Override
	public IPath getPath() {
		// 
		return new Path(JavaCore.MODULE_PATH_CONTAINER_ID);
	}

}
