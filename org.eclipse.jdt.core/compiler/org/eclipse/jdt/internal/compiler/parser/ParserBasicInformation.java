/*******************************************************************************
 * Copyright (c) 2000, 2014 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *  
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.compiler.parser;

/*An interface that contains static declarations for some basic information
 about the parser such as the number of rules in the grammar, the starting state, etc...*/
public interface ParserBasicInformation {

	int ERROR_SYMBOL = 122,
						MAX_NAME_LENGTH = 41,
						NUM_STATES = 1110,

						NT_OFFSET = 122,
						SCOPE_UBOUND = 282,
						SCOPE_SIZE = 283,
						LA_STATE_OFFSET = 15907,
						MAX_LA = 1,
						NUM_RULES = 812,
						NUM_TERMINALS = 122,
						NUM_NON_TERMINALS = 370,
						NUM_SYMBOLS = 492,
						START_STATE = 981,
						EOFT_SYMBOL = 60,
						EOLT_SYMBOL = 60,
						ACCEPT_ACTION = 15906,
						ERROR_ACTION = 15907;
}
