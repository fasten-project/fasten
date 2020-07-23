/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.core.data;

import java.net.URI;

import it.unimi.dsi.fastutil.chars.CharOpenHashSet;

/** A class representing a Fasten URI for the Java language; it has to be considered experimental
 *  until the BNF for such URIs is set in stone. */

public class FastenJavaURI extends FastenURI {
	private static final long serialVersionUID = 1L;
	private final static FastenJavaURI[] NO_ARGS_ARRAY = new FastenJavaURI[0];
	protected final String className;
	protected final String functionOrAttributeName;
	protected final FastenJavaURI[] args;
	protected final FastenJavaURI returnType;

	public FastenJavaURI(final String s) {
		this(URI.create(s));
	}

	public FastenJavaURI(final URI uri) {
		super(uri);
		if (rawEntity == null) {
			className = null;
			functionOrAttributeName = null;
			returnType = null;
			args = null;
			return;
		}
		final var dotPos = rawEntity.indexOf(".");
		if (dotPos == -1) { // entity-type
			checkForCommasAndParentheses(rawEntity);
			className = decode(rawEntity);
			functionOrAttributeName = null;
			returnType = null;
			args = null;
			return;
		}
		final String classNameSpec = rawEntity.substring(0, dotPos);
		checkForCommasParenthesesOrDots(classNameSpec);
		className = decode(classNameSpec);
		final String funcArgsTypeSpec = rawEntity.substring(dotPos + 1);
		final var funcArgsType = decode(funcArgsTypeSpec);
		final var openParenPos = funcArgsType.indexOf('(');
		if (openParenPos == -1) { // entity-attribute
			checkForCommasParenthesesOrDots(funcArgsTypeSpec);
			args = null;
			returnType = null;
			functionOrAttributeName = funcArgsType;
			return;
		}
		final String functionOrAttributeNameSpec = funcArgsType.substring(0, openParenPos);
		checkForCommasParenthesesOrDots(functionOrAttributeNameSpec);
		functionOrAttributeName = functionOrAttributeNameSpec;
		final var closedParenPos = funcArgsType.indexOf(')');
		if (closedParenPos == -1) throw new IllegalArgumentException("Missing close parenthesis");
		final String returnTypeSpec = funcArgsType.substring(closedParenPos + 1);
		checkForCommasAndParentheses(returnTypeSpec);
		returnType = FastenJavaURI.create(returnTypeSpec);
		final var argString = funcArgsType.substring(openParenPos + 1, closedParenPos);
		if (argString.length() == 0) {
			args = NO_ARGS_ARRAY;
			return;
		}

		final var a = argString.split(",");
		args = new FastenJavaURI[a.length];
		for(int i = 0; i < a.length; i++) {
			checkForCommasAndParentheses(a[i]);
			args[i] = FastenJavaURI.create(a[i]);
		}
	}

	private void checkForCommasAndParentheses(final String returnTypeSpec) {
		if (returnTypeSpec.indexOf(',') != -1 || returnTypeSpec.indexOf('(') != -1 || returnTypeSpec.indexOf(')') != -1)
			throw new IllegalArgumentException("No parentheses or commas are allowed in type components");
	}

	private void checkForCommasParenthesesOrDots(final String returnTypeSpec) {
		if (returnTypeSpec.indexOf('.') != -1 || returnTypeSpec.indexOf(',') != -1 || returnTypeSpec.indexOf('(') != -1 || returnTypeSpec.indexOf(')') != -1)
			throw new IllegalArgumentException("No parentheses, commas or dots are allowed in entity components");
	}

	/**
	 * Creates a {@link FastenJavaURI} from a string, with the same logic of {@link URI#create(String)}.
	 * @param s a string specifying a {@link FastenJavaURI}.
	 * @return a {@link FastenJavaURI}.
	 */

	public static FastenJavaURI create(final String s) {
		return new FastenJavaURI(URI.create(s));
	}

	/**
	 * Creates a {@link FastenJavaURI} from a {@link URI}.
	 * @param uri a {@link URI} a specifying a {@link FastenJavaURI}.
	 * @return a {@link FastenJavaURI}.
	 * @throws IllegalArgumentException if the argument does not satisfy the further constraints of a {@link FastenJavaURI}.
	 */

	public static FastenJavaURI create(final URI uri) {
		return new FastenJavaURI(uri);
	}

	/**
	 * Creates a {@link FastenJavaURI} from components.
	 *
	 * <P>Please note that while {@link FastenURI} components must be provided <em>raw</em>, all
	 * other components must not be.
	 *
	 * @param rawForge the forge, or {@code null}.
	 * @param rawProduct the product, or {@code null}.
	 * @param rawVersion the version, or {@code null}.
	 * @param rawVersion the namespace, or {@code null}.
	 * @param typeName the name of the type.
	 * @param functionOrAttributeName the name of the function (if {@code returnType} is not {@code null}) or attribute.
	 * @param argTypes the types of arguments of the function; you can use either an empty array or {@code null} for the empty list.
	 * @param returnType the return type ({@code null} for attributes).
	 * @return a {@link FastenJavaURI}.
	 * @throws IllegalArgumentException if the argument does not satisfy the further constraints of a {@link FastenJavaURI}.
	 */

	public static FastenJavaURI create(final String rawForge, final String rawProduct, final String rawVersion, final String rawNamespace,
			final String typeName, final String functionOrAttributeName,
			final FastenJavaURI[] argTypes, final FastenJavaURI returnType) {
		final StringBuilder entitysb = new StringBuilder();
		entitysb.append(pctEncodeArg(typeName) + '.');
		entitysb.append(pctEncodeArg(functionOrAttributeName));

		if (returnType != null) {
			entitysb.append('(');
			if (argTypes != null)
				for (int i = 0; i < argTypes.length; i++) {
					if (i>0) entitysb.append(',');
					entitysb.append(FastenJavaURI.pctEncodeArg(argTypes[i].uri.toString()));
				}
			entitysb.append(')');
			entitysb.append(FastenJavaURI.pctEncodeArg(returnType.uri.toString()));
		}
		else if (argTypes != null && argTypes.length > 0) throw new IllegalArgumentException("You cannot specify argument types for an attribute");
		final FastenURI fastenURI = FastenURI.create(rawForge, rawProduct, rawVersion, rawNamespace, entitysb.toString());
		return create(fastenURI.uri);
	}

    public static FastenJavaURI create(final String rawPackageName, final String rawClassName) {
        var returnVal = create("/" + pctEncodeArg(rawPackageName) + "/" + pctEncodeArg(rawClassName));
        return returnVal;
    }

	private final static CharOpenHashSet typeChar = new CharOpenHashSet(new char[] {
			'-', '.', '_', '~', // unreserved
			'!', '$', '&', '\'', '*', ';', '=', // sub-delims-type
			'@'
	},
			.5f);

	public static String pctEncodeArg(final String s) {
		// Encoding characters not in arg-char (see BNF)
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			final char c = s.charAt(i);
			if (c < 0x7F && !Character.isLetterOrDigit(c) && ! typeChar.contains(c))
				sb.append("%" + String.format("%02X", Integer.valueOf(c)));
			else
				sb.append(c);
		}
		return sb.toString();
	}

	public String getClassName() {
		return className;
	}

	/** Returns the name of the method or attribute associated with this FASTEN Java URI.
	 *
	 * @return the name of the method or attribute associated with this FASTEN Java URI, or {@code null} if none was specified at creation time.
	 */
	public String getEntityName() {
		return functionOrAttributeName;
	}

	/** Returns the arguments of the method associated with this FASTEN Java URI, or {@code null}.
	 *
	 * @return the arguments of the method associated with this FASTEN Java URI, or {@code null} if the URI refers to an attribute.
	 */
	public FastenJavaURI[] getArgs() {
		return args.clone(); // defensive copy?
	}

	/** Returns the return type of the method associated with this FASTEN Java URI, or {@code null}.
	 *
	 * @return the return type of the method associated with this FASTEN Java URI, or {@code null} if the URI refers to an attribute.
	 */
	public FastenJavaURI getReturnType() {
		return returnType;
	}

	/** Relativizes the provided FASTEN Java URI with respected to this FASTEN Java URI.
	 *
	 * <p>Note that this method is only a convenient version of {@link FastenURI#relativize(FastenURI)},
	 * which it overrides.
	 * @param u a FASTEN Java URI.
	 * @return {@code u} relativized to this FASTEN Java URI.
	 * @see FastenURI#relativize(FastenURI)
	 */
	@Override
	public FastenJavaURI relativize(final FastenURI u) {
		if (rawNamespace == null) throw new IllegalStateException("You cannot relativize without a namespace");
		final String rawAuthority = u.uri.getRawAuthority();
		// There is an authority and it doesn't match: return u
		if (rawAuthority != null && ! rawAuthority.equals(uri.getRawAuthority())) return u instanceof FastenJavaURI ? (FastenJavaURI) u : create(u.uri);
		// Matching authorities, or no authority, and there's a namespace, and it doesn't match: return namespace + entity
		if (u.rawNamespace != null && ! rawNamespace.equals(u.rawNamespace)) return FastenJavaURI.create("/" + u.rawNamespace + "/" +  u.rawEntity);
		// Matching authorities, or no authority, matching namespaces, or no namespace: return entity
		return FastenJavaURI.create(u.getRawEntity());
	}

	public FastenJavaURI resolve(final FastenJavaURI u) {
		// Standard resolution will work; might be more efficient
		return create(this.uri.resolve(u.uri));
	}

	@Override
	public FastenJavaURI canonicalize() {
		final FastenJavaURI[] relativizedArgs = new FastenJavaURI[args.length];

		for(int i = 0; i < args.length; i++) relativizedArgs[i] = relativize(args[i]);
		final FastenJavaURI relativizedReturnType = relativize(returnType);
		return FastenJavaURI.create(rawForge, rawProduct, rawVersion, rawNamespace, className, functionOrAttributeName, relativizedArgs, relativizedReturnType);
	}
}
