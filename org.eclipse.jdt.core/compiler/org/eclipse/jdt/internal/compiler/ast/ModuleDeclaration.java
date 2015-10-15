package org.eclipse.jdt.internal.compiler.ast;

import org.eclipse.jdt.internal.compiler.CompilationResult;

public class ModuleDeclaration extends TypeDeclaration {

	public ImportReference[] exports;
	public ImportReference[] requires;
	public int exportsCount;
	public int requiresCount;
	public char[][] moduleName;

	public static final ImportReference[] EMPTY = new ImportReference[0];

	public ModuleDeclaration(CompilationResult compilationResult) {
		super(compilationResult);
		this.exports = EMPTY;
		this.requires = EMPTY;
		this.exportsCount = 0;
		this.requiresCount = 0;
	}

	public boolean isModule() {
		return true;
	}
}
