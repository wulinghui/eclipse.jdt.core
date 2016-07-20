package org.eclipse.jdt.internal.core;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.compiler.env.IModule;

public interface IModuleRoot {

	public static int K_MODULE_PROJECT = 0;
	public static int K_MODULE_JAR = 1;
	public static int K_MODULE_JRT = 2;

	public IModule getModule() throws JavaModelException;
	
	public int getKind();

	public IPath getPath();
}
