/*******************************************************************************
 * Copyright (c) 2014 GK Software AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Stephan Herrmann - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.compiler.classfmt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.env.IBinaryAnnotation;
import org.eclipse.jdt.internal.compiler.env.IBinaryElementValuePair;
import org.eclipse.jdt.internal.compiler.env.ITypeAnnotationWalker;
import org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.eclipse.jdt.internal.compiler.lookup.SignatureWrapper;
import org.eclipse.jdt.internal.compiler.util.Util;

public class ExternalAnnotationProvider {

	private static final String CLASS_PREFIX = "class "; //$NON-NLS-1$
	private static final String INTERFACE_PREFIX = "interface "; //$NON-NLS-1$
	private static final String TYPE_PARAMETER_PREFIX = " <"; //$NON-NLS-1$

	public static final String ANNOTATION_FILE_SUFFIX = ".eea"; //$NON-NLS-1$ // FIXME(SH): define file extension

	private File annotationSource;
	private Map<String,String> methodAnnotationSources;
	private Map<String,String> fieldAnnotationSources;
	private String typeParameterAnnotationSource;
	
	public ExternalAnnotationProvider(String baseDir, String typeName) throws IOException {
		this.annotationSource = new File(baseDir+File.separatorChar+typeName+ANNOTATION_FILE_SUFFIX);
		if (!this.annotationSource.exists()) throw new FileNotFoundException(this.annotationSource.getAbsolutePath());
		this.methodAnnotationSources = new HashMap<String, String>();
		this.fieldAnnotationSources = new HashMap<String, String>();
		initialize(typeName);
	}
	
	private void initialize(String typeName) throws IOException {
		LineNumberReader reader = new LineNumberReader(new InputStreamReader(new FileInputStream(this.annotationSource)));
		try {
			String line = reader.readLine().trim();
			if (line.startsWith(CLASS_PREFIX)) {
				line = line.substring(CLASS_PREFIX.length());
			} else if (line.startsWith(INTERFACE_PREFIX)) {
				line = line.substring(INTERFACE_PREFIX.length());
			} else {
				throw new IOException("missing class header in annotation file"); //$NON-NLS-1$
			}
			if (!line.equals(typeName)) {
				throw new IOException("mismatching class name in annotation file, expected "+typeName+", but header said "+line); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if ((line = reader.readLine()) == null) {
				return;
			}
			if (line.startsWith(TYPE_PARAMETER_PREFIX)) {
				if ((line = reader.readLine()) == null) // skip first line, second line may contain type parameter annotations
					return;
				if (line.startsWith(TYPE_PARAMETER_PREFIX)) {
					this.typeParameterAnnotationSource = line.substring(TYPE_PARAMETER_PREFIX.length());
					if ((line = reader.readLine()) == null)
						return;					
				} 
			}
			do {
				if (line.isEmpty()) continue;
				String rawSig = null, annotSig = null;
				// selector:
				String selector = line;
				int errLine = -1;
				try {
					// raw signature:
					line = reader.readLine();
					if (line.charAt(0) == ' ')
						rawSig = line.substring(1);
					else
						errLine = reader.getLineNumber();
					// annotated signature:
					line = reader.readLine();
					if (line.charAt(0) == ' ')
						annotSig = line.substring(1);
				} catch (Exception ex) {
					// continue to escalate below
				}
				if (rawSig == null || annotSig == null) {
					if (errLine == -1) errLine = reader.getLineNumber();
					throw new IOException("Illegal format for annotation file at line "+errLine); //$NON-NLS-1$
				}
				if (rawSig.contains("(")) //$NON-NLS-1$
					this.methodAnnotationSources.put(selector+rawSig, annotSig);
				else
					this.fieldAnnotationSources.put(selector+rawSig, annotSig); // FIXME(SH): mark the start of the signature
			} while ((line = reader.readLine()) != null);
		} finally {
			reader.close();
		}
	}

	public ITypeAnnotationWalker forTypeParameters(LookupEnvironment environment) {
		if (this.typeParameterAnnotationSource != null)
			return new TypeParamtersAnnotationWalker(this.typeParameterAnnotationSource.toCharArray(), 0, 0, null, environment);
		return ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER;
	}

	public ITypeAnnotationWalker forMethod(char[] selector, char[] signature, LookupEnvironment environment) {
		String source = this.methodAnnotationSources.get(String.valueOf(CharOperation.concat(selector, signature)));
		if (source != null)
			return new MethodAnnotationWalker(source.toCharArray(), 0, environment);
		return ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER;
	}

	public ITypeAnnotationWalker forField(char[] selector, char[] signature, LookupEnvironment environment) {
		String source = this.fieldAnnotationSources.get(String.valueOf(CharOperation.concat(selector, signature)));
		if (source != null)
			return new FieldAnnotationWalker(source.toCharArray(), 0, environment);
		return ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("External Annotations from "+this.annotationSource.getAbsolutePath()).append('\n'); //$NON-NLS-1$
		sb.append("Methods:\n"); //$NON-NLS-1$
		for (Entry<String,String> e : this.methodAnnotationSources.entrySet())
			sb.append('\t').append(e.getKey()).append('\n');
		return sb.toString();
	}

	abstract class SingleMarkerAnnotation implements IBinaryAnnotation {
		@Override
		public IBinaryElementValuePair[] getElementValuePairs() {
			return ElementValuePairInfo.NoMembers;
		}
		protected char[] getBinaryTypeName(char[][] name) {
			return CharOperation.concat('L', CharOperation.concatWith(name, '/'), ';');
		}
	}

	SingleMarkerAnnotation NULLABLE, NONNULL;

	void initAnnotations(final LookupEnvironment environment) {
		if (this.NULLABLE == null) {
			this.NULLABLE = new SingleMarkerAnnotation() {
				@Override public char[] getTypeName() { return getBinaryTypeName(environment.getNullableAnnotationName()); }
			};
		}
		if (this.NONNULL == null) {
			this.NONNULL = new SingleMarkerAnnotation() {
				@Override public char[] getTypeName() { return getBinaryTypeName(environment.getNonNullAnnotationName()); }
			};
		}
	}

	abstract class MemberAnnotationWalker implements ITypeAnnotationWalker {
		
		char[] source;
		SignatureWrapper wrapper;
		int pos;
		int prevTypeArgStart;
		int currentTypeBound;
		LookupEnvironment environment;

		MemberAnnotationWalker(char[] source, int pos, LookupEnvironment environment) {
			this.source = source;
			this.pos = pos;
			this.environment = environment;
			initAnnotations(environment);
		}
		
		SignatureWrapper wrapperWithStart(int start) {
			if (this.wrapper == null)
				this.wrapper = new SignatureWrapper(this.source);
			this.wrapper.start = start;
			return this.wrapper;
		}

		@Override
		public ITypeAnnotationWalker toReceiver() {
			return ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER;
		}

		@Override
		public ITypeAnnotationWalker toTypeParameter(boolean isClassTypeParameter, int rank) {
			return ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER;
		}

		@Override
		public ITypeAnnotationWalker toTypeParameterBounds(boolean isClassTypeParameter, int parameterRank) {
			return ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER;
		}

		@Override
		public ITypeAnnotationWalker toTypeBound(short boundIndex) {
			return ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER;
		}

		@Override
		public ITypeAnnotationWalker toSupertype(short index) {
			return ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER;
		}

		@Override
		public ITypeAnnotationWalker toTypeArgument(int rank) {
			if (rank == 0) {
				int start = CharOperation.indexOf('<', this.source, this.pos) + 1;
				this.prevTypeArgStart = start;
				return new MethodAnnotationWalker(this.source, start, this.environment);
			}
			int next = this.prevTypeArgStart;
			switch (this.source[next]) {
				case '*': 
					break;
				case '-': 
				case '+':
					next++;
					//$FALL-THROUGH$
				default:
					next = wrapperWithStart(next).computeEnd();
			}
			next++;
		    this.prevTypeArgStart = next;
		    return new MethodAnnotationWalker(this.source, next,	this.environment);
		}

		@Override
		public ITypeAnnotationWalker toWildcardBound() {
			switch (this.source[this.pos]) {
				case '-': 
				case '+':
					return new MethodAnnotationWalker(this.source, this.pos+1, this.environment);
				default: // includes unbounded '*'
					return ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER;
			}			
		}

		@Override
		public ITypeAnnotationWalker toNextArrayDimension() {
			if (this.source[this.pos] == '[') {
				int newPos = this.pos+1;
				switch (this.source[newPos]) {
					case '0': case '1': newPos++; break;
				}
				return new MethodAnnotationWalker(this.source, newPos, this.environment);
			}
			return ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER;
		}

		@Override
		public ITypeAnnotationWalker toNextNestedType() {
			return this; // FIXME(stephan)
		}

		@Override
		public IBinaryAnnotation[] getAnnotationsAtCursor(int currentTypeId) {
			if (this.pos != -1 && this.pos < this.source.length-2) {
				switch (this.source[this.pos]) {
					case 'T':
					case 'L':
					case '[':
						switch (this.source[this.pos+1]) {
							case '0':
								return new IBinaryAnnotation[]{ ExternalAnnotationProvider.this.NULLABLE };
							case '1':
								return new IBinaryAnnotation[]{ ExternalAnnotationProvider.this.NONNULL };
						}
				}				
			}
			return null;
		}
	}

	/**
	 * Walker that may serve the annotations on type parameters of the current class or method.
	 * TODO: may need to evolve to also provide annotations on super types.
	 */
	public class TypeParamtersAnnotationWalker extends MemberAnnotationWalker {

		int[] rankStarts; // indices of start positions for type parameters per rank
		int currentRank;

		TypeParamtersAnnotationWalker(char[] source, int pos, int rank, int[] rankStarts, LookupEnvironment environment) {
			super(source, pos, environment);
			this.currentRank = rank;
			if (rankStarts != null) {
				this.rankStarts = rankStarts;
			} else {
				// eagerly scan all type parameters:
				int length = source.length;
				rankStarts = new int[length];
				int curRank = 0;
				// next block cf. BinaryTypeBinding.createTypeVariables():
				int depth = 0;
				boolean pendingVariable = true;
				scanVariables: {
					for (int i = pos; i < length; i++) {
						switch(this.source[i]) {
							case Util.C_GENERIC_START :
								depth++;
								break;
							case Util.C_GENERIC_END :
								if (--depth < 0)
									break scanVariables;
								break;
							case Util.C_NAME_END :
								if ((depth == 0) && (i +1 < length) && (this.source[i+1] != Util.C_COLON))
									pendingVariable = true;
								break;
							default:
								if (pendingVariable) {
									pendingVariable = false;
									rankStarts[curRank++] = i;
								}
						}
					}
				}
				System.arraycopy(rankStarts, 0, this.rankStarts = new int[curRank], 0, curRank);
			}
		}
		
		@Override
		public ITypeAnnotationWalker toTypeParameter(boolean isClassTypeParameter, int rank) {
			if (rank == this.currentRank)
				return this;
			if (rank < this.rankStarts.length)
				return new TypeParamtersAnnotationWalker(this.source, this.rankStarts[rank], rank, this.rankStarts, this.environment);
			return ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER;
		}

		@Override
		public ITypeAnnotationWalker toTypeParameterBounds(boolean isClassTypeParameter, int parameterRank) {
			return new TypeParamtersAnnotationWalker(this.source, this.rankStarts[parameterRank], parameterRank, this.rankStarts, this.environment);
		}

		@Override
		public ITypeAnnotationWalker toTypeBound(short boundIndex) {
			// assume we are positioned either at the start of the bounded type parameter
			// or at the start of a previous type bound
			int p = this.pos;
			int i = this.currentTypeBound;
			while(true) {
				// each bound is prefixed with ':'
				int colon = CharOperation.indexOf(Util.C_COLON, this.source, p);
				if (colon != -1)
					p = colon + 1;
				if (++i > boundIndex) break;
				// skip next type:
				p = wrapperWithStart(p).computeEnd()+1;
			}
			this.pos = p;
			this.currentTypeBound = boundIndex;
			return this;
		}

		@Override
		public ITypeAnnotationWalker toField() {
			throw new UnsupportedOperationException("Cannot navigate to fields"); //$NON-NLS-1$
		}

		@Override
		public ITypeAnnotationWalker toMethodReturn() {
			throw new UnsupportedOperationException("Cannot navigate to method return"); //$NON-NLS-1$
		}

		@Override
		public ITypeAnnotationWalker toMethodParameter(short index) {
			throw new UnsupportedOperationException("Cannot navigate to method parameter"); //$NON-NLS-1$
		}

		@Override
		public ITypeAnnotationWalker toThrows(int index) {
			throw new UnsupportedOperationException("Cannot navigate to throws"); //$NON-NLS-1$
		}

		@Override
		public IBinaryAnnotation[] getAnnotationsAtCursor(int currentTypeId) {
			if (this.pos != -1 && this.pos < this.source.length-1) {
				switch (this.source[this.pos]) {
					case '0':
						return new IBinaryAnnotation[]{ ExternalAnnotationProvider.this.NULLABLE };
					case '1':
						return new IBinaryAnnotation[]{ ExternalAnnotationProvider.this.NONNULL };
				}				
			}
			return super.getAnnotationsAtCursor(currentTypeId);
		}
	}

	class MethodAnnotationWalker extends MemberAnnotationWalker {

		int prevParamStart;
		TypeParamtersAnnotationWalker typeParametersWalker;

		MethodAnnotationWalker(char[] source, int pos, LookupEnvironment environment) {
			super(source, pos, environment);
		}
	
		int typeEnd(int start) {
			while (this.source[start] == '[') {
				start++;
				char an = this.source[start];
				if (an == '0' || an == '1')
					start++;
			}
			int end = wrapperWithStart(start).computeEnd();
			return end;
		}
		
		@Override
		public ITypeAnnotationWalker toTypeParameter(boolean isClassTypeParameter, int rank) {
			if (this.source[0] == '<') {
				if (this.typeParametersWalker == null)
					return this.typeParametersWalker = new TypeParamtersAnnotationWalker(this.source, this.pos+1, rank, null, this.environment);
				return this.typeParametersWalker.toTypeParameter(isClassTypeParameter, rank);
			}
			return ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER;
		}

		@Override
		public ITypeAnnotationWalker toTypeParameterBounds(boolean isClassTypeParameter, int parameterRank) {
			if (this.typeParametersWalker != null)
				return this.typeParametersWalker.toTypeParameterBounds(isClassTypeParameter, parameterRank);
			return ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER;
		}

		@Override
		public ITypeAnnotationWalker toMethodReturn() {
			int close = CharOperation.indexOf(')', this.source);
			if (close != -1)
				return new MethodAnnotationWalker(this.source, close+1, this.environment);
			return ITypeAnnotationWalker.EMPTY_ANNOTATION_WALKER;
		}

		@Override
		public ITypeAnnotationWalker toMethodParameter(short index) {
			if (index == 0) {
				int start = CharOperation.indexOf('(', this.source) + 1;
				this.prevParamStart = start;
				return new MethodAnnotationWalker(this.source, start, this.environment);
			}
			int end = typeEnd(this.prevParamStart);
			end++;
		    this.prevParamStart = end;
		    return new MethodAnnotationWalker(this.source, end, this.environment);
		}

		@Override
		public ITypeAnnotationWalker toThrows(int index) {
			return this;
		}

		@Override
		public ITypeAnnotationWalker toField() {
			throw new UnsupportedOperationException("Methods have no fields"); //$NON-NLS-1$
		}		
	}
	
	class FieldAnnotationWalker extends MemberAnnotationWalker {
		public FieldAnnotationWalker(char[] source, int pos, LookupEnvironment environment) {
			super(source, pos, environment);
		}

		@Override
		public ITypeAnnotationWalker toField() {
			return this;
		}

		@Override
		public ITypeAnnotationWalker toMethodReturn() {
			throw new UnsupportedOperationException("Field has no method return"); //$NON-NLS-1$
		}

		@Override
		public ITypeAnnotationWalker toMethodParameter(short index) {
			throw new UnsupportedOperationException("Field has no method parameter"); //$NON-NLS-1$
		}

		@Override
		public ITypeAnnotationWalker toThrows(int index) {
			throw new UnsupportedOperationException("Field has no throws"); //$NON-NLS-1$
		}
	}
}
