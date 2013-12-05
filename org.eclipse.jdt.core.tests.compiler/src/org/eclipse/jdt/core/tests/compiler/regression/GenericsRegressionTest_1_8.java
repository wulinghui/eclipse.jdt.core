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

import junit.framework.Test;

public class GenericsRegressionTest_1_8 extends AbstractRegressionTest {

static {
//	TESTS_NAMES = new String[] { "test0056c" };
//	TESTS_NUMBERS = new int[] { 40, 41, 43, 45, 63, 64 };
//	TESTS_RANGE = new int[] { 11, -1 };
}
public GenericsRegressionTest_1_8(String name) {
	super(name);
}
public static Class testClass() {
	return GenericsRegressionTest_1_8.class;
}
public static Test suite() {
	return buildMinimalComplianceTestSuite(testClass(), F_1_8);
}

public void testBug423070() {
	this.runConformTest(
		new String[] {
			"junk/Junk3.java",
			"package junk;\n" + 
			"\n" + 
			"import java.util.ArrayList;\n" + 
			"import java.util.Collections;\n" + 
			"import java.util.List;\n" + 
			"\n" + 
			"class ZZObject extends Object {\n" + 
			"}\n" + 
			"\n" + 
			"public class Junk3 {\n" + 
			"\n" + 
			"    public static final List EMPTY_LIST = new ArrayList<>();\n" + 
			"    public static final <T> List<T> emptyList() {\n" + 
			"        return (List<T>) EMPTY_LIST;\n" + 
			"    }\n" + 
			"    \n" + 
			"    public Junk3(List<ZZObject> list) {\n" + 
			"    }\n" + 
			"    \n" + 
			"    //FAILS - if passed as argument\n" + 
			"    public Junk3() {\n" + 
			"        this(emptyList());\n" + 
			"    }\n" + 
			"    \n" + 
			"\n" + 
			"    //WORKS - if you assign it (and lose type info?)\n" + 
			"    static List works = emptyList();\n" + 
			"    public Junk3(boolean bogus) {\n" + 
			"        this(works);\n" + 
			"    }\n" + 
			"}",
		});
}
}
