/*******************************************************************************
 * Copyright (c) 2013 GK Software AG.
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
 *     Stephan Herrmann - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.core.tests.compiler.regression;

import java.util.Map;

import junit.framework.Test;

import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

/**
 * Test new type inference as defined in JLS8 sect 18.
 */
public class TypeInferenceTest_1_8 extends AbstractComparableTest {

	public TypeInferenceTest_1_8(String name) {
		super(name);
	}

	// Static initializer to specify tests subset using TESTS_* static variables
	// All specified tests which do not belong to the class are skipped...
	static {
		TESTS_NAMES = new String[] { "testInference" };
//		TESTS_NUMBERS = new int[] { 1, 2 };
//		TESTS_RANGE = new int[] { 1097, -1 };
	}
	public static Test suite() {
		return buildComparableTestSuite(testClass());
	}

	public static Class testClass() {
		return TypeInferenceTest_1_8.class;
	}

	protected Map getCompilerOptions() {
		Map options = super.getCompilerOptions();
		options.put(CompilerOptions.OPTION_ReportMissingOverrideAnnotationForInterfaceMethodImplementation, CompilerOptions.DISABLED);
		options.put(CompilerOptions.OPTION_ReportUnusedLocal, CompilerOptions.IGNORE);
		options.put(CompilerOptions.OPTION_ReportUnusedParameter, CompilerOptions.IGNORE);
		options.put(CompilerOptions.OPTION_ReportUnusedPrivateMember, CompilerOptions.IGNORE);
		options.put(CompilerOptions.OPTION_ReportUnusedTypeParameter, CompilerOptions.IGNORE);
		return options;
	}

	// scratchpad:
	public void testInference1() {
		this.runConformTest(
			new String[] {
				"X.java",
				"import java.util.List;\n" +
				"class Y extends X {}\n" +
				"public class X {\n" +
				"    <T extends String> void process(T t) {}\n" +
				"    void foo() {\n" +
				"        process(id(\"huhu\"));\n" +
				"    }\n" +
				"    <X> X id(X x) { return x; }\n" +
				"}\n"
			});
	}
	public void testInference2() {
		this.runConformTest(
			new String[] {
				"X.java",
				"import java.util.List;\n" +
				"class Y extends X {}\n" +
				"public class X {\n" +
				"    <S,T extends String, U extends S & T, W extends List<T>> void process(S s, T t, U u, U u2, W w) {}\n" +
				"    void foo() {\n" +
				"        process(new Object(), id(\"huhu\"), this, new Y(), new List<String>());\n" +
				"    }\n" +
				"    <Z> Z id(Z z) { return z; }\n" +
				"}\n"
			});
	}
}
