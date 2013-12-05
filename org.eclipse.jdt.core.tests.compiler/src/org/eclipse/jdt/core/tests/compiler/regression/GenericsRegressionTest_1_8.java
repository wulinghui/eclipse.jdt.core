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
//	TESTS_NAMES = new String[] { "testConditionalExpression2" };
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

public void testConditionalExpression1() {
	runConformTest(
		new String[] {
			"X.java",
			"class A {}\n" +
			"class B extends A {}\n" +
			"public class X {\n" +
			"	<T> T combine(T x, T y) { return x; }\n" +
			"	A test(A a, B b, boolean flag) {\n" +
			"		return combine(flag ? a : b, a);\n" +
			"	}\n" +
			"}\n"
		});
}

public void _testConditionalExpression2() {
	runConformTest(
		new String[] {
			"X.java",
			"class A{/**/}\n" + 
			"class B extends A {/**/}\n" + 
			"class C extends B {/**/}\n" + 
			"class G<T> {/**/}\n" + 
			"\n" + 
			"public class X {\n" + 
			"G<A> ga=null;\n" + 
			"G<B> gb=null;\n" + 
			"G<C> gc=null;\n" + 
			"G<? super A> gsa=null;\n" + 
			"G<? super B> gsb=null;\n" + 
			"G<? super C> gsc=null;\n" + 
			"\n" + 
			"@SuppressWarnings(\"unused\")\n" + 
			"    public void test(boolean f) {\n" + 
			"		G<? super B> l1 = (f) ? gsa : gb;\n" +
			"		G<? super B> l2 = (f) ? gsb : gb;\n" +
			"       G<? super C> l3 = (f) ? gsc : gb;\n" +
			"       G<? super B> l4 = (f) ? gsb : gsb;\n" +
			"	}\n" +
			"}"
		});
}
}
