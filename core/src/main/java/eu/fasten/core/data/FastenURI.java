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
import java.net.URISyntaxException;

/** A class representing a Fasten URI; it has to be considered experimental until the BNF for such URIs is set in stone. */

public class FastenURI {
	/** The underlying {@link URI}. */
	protected final URI uri;
	/** The forge of the {@linkplain #rawProduct product} associated with this FastenURI, or {@code null} if the forge is not specified. */
	protected final String rawForge;
	/** The product associated with this FastenURI, or {@code null} if the product is not specified. */
	protected final String rawProduct;
	/** The {@linkplain #rawProduct product} version, or {@code null} if the version is not specified. */
	protected final String rawVersion;
	/** The module, or {@code null} if the module is not specified. */
	protected final String rawNamespace;
	/** The language-dependent part, or {@code null} if the language-dependent part is not specified. */
	protected final String rawEntity;

	protected FastenURI(final URI uri) {
		this.uri = uri;
		if (uri.getScheme() != null && ! "fasten".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Scheme, if specified, must be 'fasten'");
		final String forgeProductVersion = uri.getRawAuthority();

		if (forgeProductVersion == null) {
			rawForge = rawProduct = rawVersion = null;
		}
		else {
			final var exclPos = forgeProductVersion.indexOf('!');
			String productVersion;
			if (exclPos == -1) { // No forge
				rawForge = null;
				productVersion = forgeProductVersion;
			}
			else {
				rawForge = forgeProductVersion.substring(0,  exclPos);
				productVersion = forgeProductVersion.substring(exclPos + 1);
				if (productVersion.indexOf('!') >= 0) throw new IllegalArgumentException("More than one forge");
				if (rawForge.indexOf('$') >= 0) throw new IllegalArgumentException("Version / forge inverted or mixed");
			}

			final var dollarPos = productVersion.indexOf('$');
			if (dollarPos == -1) {
				rawProduct = productVersion;
				rawVersion = null;
			}
			else {
				rawProduct = productVersion.substring(0, dollarPos);
				rawVersion = productVersion.substring(dollarPos + 1);
				if (rawVersion.indexOf('$') >= 0) throw new IllegalArgumentException("More than one version");
			}

			if (rawProduct.length() == 0) throw new IllegalArgumentException("The product cannot be empty");
		}

		final var path = uri.getRawPath();

		if (path == null || path.length() == 0) {
			rawNamespace = rawEntity = null;
			return;
		}

		int slashPos;
		if (path.charAt(0) == '/') { // We have a namespace
			slashPos = path.indexOf('/', 1); // Skip first slash

			if (slashPos == -1)  throw new IllegalArgumentException("Missing entity");
			rawNamespace = path.substring(1, slashPos);

			if (rawNamespace.length() == 0) throw new IllegalArgumentException("The namespace cannot be empty");
			rawEntity = path.substring(slashPos + 1);
		}
		else {
			rawNamespace = null;
			rawEntity = path;
		}

		if (rawEntity.length() == 0) throw new IllegalArgumentException("The entity part cannot be empty");
		if (rawEntity.indexOf(':') >= 0) throw new IllegalArgumentException("The entity part cannot contain colons");
	}

	protected FastenURI(final String s) throws URISyntaxException {
		this(new URI(s));
	}

	/**
	 * Creates a {@link FastenURI} from a string, with the same logic of {@link URI#create(String)}.
	 * @param s a string specifying a {@link FastenURI}.
	 * @return a {@link FastenURI}.
	 */

	public static FastenURI create(final String s) {
		return new FastenURI(URI.create(s));
	}

	/**
	 * Creates a {@link FastenURI} from a {@link URI}.
	 * @param uri a {@link URI} a specifying a {@link FastenURI}.
	 * @return a {@link FastenURI}.
	 * @throws IllegalArgumentException if the argument does not satisfy the further constraints of a {@link FastenURI}.
	 */

	public static FastenURI create(final URI uri) {
		return new FastenURI(uri);
	}

	public static FastenURI create(final String rawForge, final String rawProduct, final String rawVersion, final String rawNamespace, final String rawEntity) {
		final StringBuffer urisb = new StringBuffer();
		urisb.append("fasten:");
		if (rawProduct != null) {
			urisb.append("//");
			if (rawForge != null) urisb.append(rawForge + "!");
			urisb.append(rawProduct);
			if (rawVersion != null) urisb.append("$" + rawVersion);
			urisb.append("/" + rawNamespace + "/" + rawEntity);
		} else urisb.append("/" + rawNamespace + "/" + rawEntity);
		return new FastenURI(URI.create(urisb.toString()));
	}

	public static FastenURI create(final String rawForgeProductVersion, final String rawNamespace, final String rawEntity) {
		return new FastenURI(URI.create("fasten:" + rawForgeProductVersion + "/" + rawNamespace + "/" + rawEntity));
	}

	public String getRawForge() {
		return rawForge;
	}

	public String getRawProduct() {
		return rawProduct;
	}

	public String getRawVersion() {
		return rawVersion;
	}

	public String getRawEntity() {
		return rawEntity;
	}

	public String getRawNamespace() {
		return rawNamespace;
	}

	private static int decode(final char c) {
		if ((c >= '0') && (c <= '9')) return c - '0';
		if ((c >= 'a') && (c <= 'f')) return c - 'a' + 10;
		if ((c >= 'A') && (c <= 'F')) return c - 'A' + 10;
		assert false;
		return -1;
	}

	private static char decode(final char c1, final char c2) {
		return (char) (((decode(c1) & 0xf) << 4) | ((decode(c2) & 0xf) << 0));
	}

	protected static String decode(final String s) {
		if (s == null) return s;
		final int n = s.length();
		if (n == 0 || s.indexOf('%') < 0) return s;

		final StringBuilder sb = new StringBuilder(n);

		for (int i = 0; i < n; i++) {
			final char c = s.charAt(i);
			if (c != '%') sb.append(c);
			else sb.append(decode(s.charAt(++i), s.charAt(++i)));
		}

		return sb.toString();
	}

	public String getForge() {
		return decode(rawForge);
	}

	public String getProduct() {
		return decode(rawProduct);
	}

	public String getVersion() {
		return decode(rawVersion);
	}

	public String getEntity() {
		return decode(rawEntity);
	}

	public String getNamespace() {
		return decode(rawNamespace);
	}


	public FastenURI resolve(final FastenURI fastenURI) {
		return create(uri.resolve(fastenURI.uri));
	}

	public FastenURI resolve(final String str) {
		return create(uri.resolve(URI.create(str)));
	}

	public FastenURI relativize(final FastenURI uri) {
		return create(this.uri.relativize(uri.uri));
	}

	public String getScheme() {
		return uri.getScheme();
	}

	public boolean isAbsolute() {
		return uri.isAbsolute();
	}

	public int getPort() {
		return uri.getPort();
	}

	public String getPath() {
		return uri.getPath();
	}

	public String getQuery() {
		return uri.getQuery();
	}

	@Override
	public String toString() {
		return uri.toString();
	}

	@Override
	public boolean equals(final Object o) {
		if (o == null) return false;
		return uri.toString().equals(o.toString());
	}

	@Override
	public int hashCode() {
		return uri.toString().hashCode();
	}

	public FastenURI canonicalize() {
		 return this;
	}
}
