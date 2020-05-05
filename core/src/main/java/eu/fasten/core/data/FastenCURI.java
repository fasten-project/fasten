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

/** A class representing a Fasten URI for the C language; it has to be considered experimental
 *  until the BNF for such URIs is set in stone. */

public class FastenCURI extends FastenURI {
	protected final String filename;
	protected final String functionOrVariableName;
	protected final String namespace;
	protected final String binary;
	protected final boolean isFunction;

	public FastenCURI(final String s) {
		this(URI.create(s));
	}

	public FastenCURI(final URI uri) {
		super(uri);
		if (rawEntity == null) {
			filename = functionOrVariableName = null;
			isFunction = false;
			namespace = null;
			binary = null;
			return;
		}
		final var semicolonPosEntity = rawEntity.indexOf(";");
		String s;
		if (semicolonPosEntity == -1) {
			filename = null; // Without filename
			s = decode(rawEntity);
		}
		else {
			filename = decode(rawEntity.substring(0, semicolonPosEntity));
			s = decode(rawEntity.substring(semicolonPosEntity + 1));
		}

		final var openParenPos = s.indexOf('(');
		if (isFunction = (openParenPos != -1)) { // function
			if (s.length() == openParenPos + 1 || s.charAt(openParenPos + 1) != ')') throw new IllegalArgumentException("Open parenthesis, but no closed parenthesis");
			if (s.length() != openParenPos + 2) throw new IllegalArgumentException("Garbage after closed parenthesis");
			functionOrVariableName = decode(s.substring(0, s.length() - 2));
		}
		else functionOrVariableName = decode(s); // variable
		if (rawNamespace == null) {
			namespace = null;
			binary = null;
		} else {
			final var semicolonPosNamespace = rawNamespace.indexOf(";");
			if (semicolonPosNamespace == -1) throw new IllegalArgumentException("Namespace must contain a semicolon");
			var b = decode(rawNamespace.substring(0, semicolonPosNamespace));
			binary = b.equals("") ? null : b;
			namespace = decode(rawNamespace.substring(semicolonPosNamespace + 1));
		}
	}

	/**
	 * Creates a {@link FastenCURI} from a string, with the same logic of {@link URI#create(String)}.
	 * @param s a string specifying a {@link FastenCURI}.
	 * @return a {@link FastenCURI}.
	 */

	public static FastenCURI create(final String s) {
		return new FastenCURI(URI.create(s));
	}

	/**
	 * Creates a {@link FastenCURI} from a {@link URI}.
	 * @param uri a {@link URI} a specifying a {@link FastenCURI}.
	 * @return a {@link FastenCURI}.
	 * @throws IllegalArgumentException if the argument does not satisfy the further constraints of a {@link FastenCURI}.
	 */

	public static FastenCURI create(final URI uri) {
		return new FastenCURI(uri);
	}

	/**
	 * Creates a {@link FastenCURI} from components.
	 *
	 * <P>Please note that while {@link FastenURI} components must be provided <em>raw</em>, all
	 * other components must not be.
	 *
	 * @param rawForge the forge, or {@code null}.
	 * @param rawProduct the product, or {@code null}.
	 * @param rawVersion the version, or {@code null}.
	 * @param filename the filename, if present, or {@code null}.
	 * @param functionOrVariableName the name of the function or variable.
	 * @param isFunction whether the returned URI refers to a variable or function.
	 * @throws IllegalArgumentException if the argument does not satisfy the further constraints of a {@link FastenCURI}.
	 */

	public static FastenCURI create(final String rawForge, final String rawProduct, final String rawVersion,
			final String binary, final String namespace, final String filename,
			final String functionOrVariableName, final boolean isFunction) {
		if (binary != null && namespace == null) throw new IllegalArgumentException("If binary is provided then namespace must be provided");
		final StringBuffer entitysb = new StringBuffer();
		if (filename != null) entitysb.append(filename + ';');
		entitysb.append(functionOrVariableName);
		if (isFunction) entitysb.append("()");
		final String rawNamespace;
		if (binary != null)
			rawNamespace = binary + ";" + namespace;
		else if (namespace != null)
			rawNamespace = ";" + namespace;
		else
			rawNamespace = null;
		final FastenURI fastenURI = FastenURI.create(rawForge, rawProduct, rawVersion, rawNamespace, entitysb.toString());
		return create(fastenURI.uri);
	}

	/** Returns the name of the function or variable associated with this FASTEN C URI.
	 *
	 * @return the name of the function or variable associated with this FASTEN C URI.
	 */
	public String getName() {
		return functionOrVariableName;
	}

	/** Returns the name of the function or variable associated with this FASTEN C URI.
	 *
	 * @return the name of the function or variable associated with this FASTEN C URI, or {@code null} if none was specified at creation time.
	 */
	public String getFilename() {
		return filename;
	}

	/** Returns whether this FASTEN C URI represents a function.
	 *
	 * @return whether this FASTEN C URI represents a function.
	 */
	public boolean isFunction() {
		return isFunction;
	}

	/** Returns the binary associated with this Fasten C URI.
	 *
	 * @return the binary associated with this Fasten C URI.
	 */
	public String getBinary() {
		return binary;
	}

	/** Returns the namespace of this Fasten C URI.
	 * In Fasten C URI the namespace is C for public functions / variables.
	 * Otherwise, is the absolute path from project's root to the filename of
	 * the entity.
	 *
	 * @return the namespace of this Fasten C URI.
	 */
	@Override
	public String getNamespace() {
		return namespace;
	}

	/** Returns whether this FASTEN C URI represents a public function / variable.
	 *
	 * @return whether this FASTEN C URI represents a public function / variable.
	 */
	public boolean isPublic() {
		if (namespace.equals("C"))
			return true;
		return false;
	}


	/** Relativizes the provided FASTEN C URI with respected to this FASTEN C URI.
	 *
	 * <p>Note that this method is only a convenient version of {@link FastenURI#relativize(FastenURI)},
	 * which it overrides.
	 * @param u a FASTEN C URI.
	 * @return {@code u} relativized to this FASTEN C URI.
	 * @see FastenURI#relativize(FastenURI)
	 */
	@Override
	public FastenCURI relativize(final FastenURI u) {
		if (rawNamespace == null) throw new IllegalStateException("You cannot relativize without a namespace");

		final String rawAuthority = u.uri.getRawAuthority();
		// There is an authority and it doesn't match: return u
		if (rawAuthority != null && ! rawAuthority.equals(uri.getRawAuthority())) return u instanceof FastenCURI ? (FastenCURI) u : create(u.uri);
		// Matching authorities, or no authority, and there's a namespace, and it doesn't match: return namespace + entity
		if (u.rawNamespace != null && ! rawNamespace.equals(u.rawNamespace)) return FastenCURI.create("/" + u.rawNamespace + "/" +  u.rawEntity);
		// Matching authorities, or no authority, matching namespaces, or no namespace: return entity
		return FastenCURI.create(u.getRawEntity());
	}

	public FastenCURI resolve(final FastenCURI u) {
		// Standard resolution will work; might be more efficient
		return create(this.uri.resolve(u.uri));
	}

	@Override
	public FastenCURI canonicalize() {
		return FastenCURI.create(rawForge, rawProduct, rawVersion, binary, namespace, filename, functionOrVariableName, isFunction);
	}
}
