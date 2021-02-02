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

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;

/** A class representing a Fasten URI; it has to be considered experimental until the BNF for such URIs is set in stone. */

public class FastenURI implements Serializable {
	private static final long serialVersionUID = 1L;
	/** A placeholder Fasten URI. */
	public static final FastenURI NULL_FASTEN_URI = FastenURI.create("//-");
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

	protected FastenURI(URI uri) {
		if (uri.getScheme() != null && ! "fasten".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Scheme, if specified, must be 'fasten'");
		// Bypass URI when the scheme is specified, but there is no forge-product-version
		if (uri.isOpaque()) uri = URI.create(uri.getSchemeSpecificPart());

		this.uri = uri;
		final String forgeProductVersion = uri.getRawAuthority();

		if (forgeProductVersion == null) rawForge = rawProduct = rawVersion = null;
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
			}

                      this.validateRawForge();

			final var dollarPos = productVersion.indexOf('$');
			if (dollarPos == -1) {
				rawProduct = productVersion;
				rawVersion = null;
			}
			else {
				rawProduct = productVersion.substring(0, dollarPos);
				rawVersion = productVersion.substring(dollarPos + 1);
			}

                      this.validateRawVersion();
                      this.validateRawProduct();
		}

		final var path = uri.getRawPath();

		if (path.length() == 0) {
			rawNamespace = rawEntity = null;
			return;
		}

		int slashPos;
		if (path.charAt(0) == '/') { // We have a namespace
			slashPos = path.indexOf('/', 1); // Skip first slash

			if (slashPos == -1)  throw new IllegalArgumentException("Missing entity");
			rawNamespace = path.substring(1, slashPos);
			rawEntity = path.substring(slashPos + 1);
		}
		else {
			if (path.indexOf('/') != -1) throw new IllegalArgumentException("The entity part cannot contain a slash (namespaces must be always prefixed with a slash)"); // No slash
			rawNamespace = null;
			rawEntity = path;
		}
              this.validateRawNamespace();
              this.validateRawEntity();
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


	private static FastenURI create(final StringBuilder urisb, final String rawForge, final String rawProduct, final String rawVersion, final String rawNamespace, final String rawEntity) {
		if (rawProduct != null) {
			urisb.append("//");
			if (rawForge != null) urisb.append(rawForge + "!");
			urisb.append(rawProduct);
			if (rawVersion != null) urisb.append("$" + rawVersion);
			urisb.append("/" + rawNamespace + "/" + rawEntity);
		} else urisb.append("/" + rawNamespace + "/" + rawEntity);
		return new FastenURI(URI.create(urisb.toString()));
	}

	private static FastenURI create(final StringBuilder urisb, final String rawForge, final String rawProduct, final String rawVersion, final String rawPath) {
		if (rawProduct != null) {
			urisb.append("//");
			if (rawForge != null) urisb.append(rawForge + "!");
			urisb.append(rawProduct);
			if (rawVersion != null) urisb.append("$" + rawVersion);
			urisb.append(rawPath);
		} else urisb.append(rawPath);
		return new FastenURI(URI.create(urisb.toString()));
	}

	/**
	 * Creates a {@link FastenURI} from given raw (i.e., properly escaped) fine-grained components.
	 *
	 * <p>
	 * No check is performed on the correctness of the components.
	 *
	 * @param rawForge the forge, or {@code null}.
	 * @param rawProduct the product, or {@code null}.
	 * @param rawVersion the version, or {@code null}.
	 * @param rawNamespace the namespace.
	 * @param rawEntity the entity.
	 * @throws IllegalArgumentException if the argument does not satisfy the further constraints of a
	 *             {@link FastenURI}.
	 * @see #create(String, String, String)
	 */

	public static FastenURI create(final String rawForge, final String rawProduct, final String rawVersion, final String rawNamespace, final String rawEntity) {
		return create(new StringBuilder().append("fasten:"), rawForge, rawProduct, rawVersion, rawNamespace, rawEntity);
	}

	/**
	 * Creates a <em>schemeless</em> {@link FastenURI} from given raw (i.e., properly escaped)
	 * fine-grained components.
	 *
	 * <p>
	 * No check is performed on the correctness of the components.
	 *
	 * @param rawForge the forge, or {@code null}.
	 * @param rawProduct the product, or {@code null}.
	 * @param rawVersion the version, or {@code null}.
	 * @param rawNamespace the namespace.
	 * @param rawEntity the entity.
	 * @throws IllegalArgumentException if the argument does not satisfy the further constraints of a
	 *             {@link FastenURI}.
	 * @see #create(String, String, String)
	 */

	public static FastenURI createSchemeless(final String rawForge, final String rawProduct, final String rawVersion, final String rawNamespace, final String rawEntity) {
		return create(new StringBuilder(), rawForge, rawProduct, rawVersion, rawNamespace, rawEntity);
	}

	/**
	 * Creates a {@link FastenURI} from given raw (i.e., properly escaped) fine-grained components.
	 *
	 * <p>
	 * No check is performed on the correctness of the components.
	 *
	 * @param rawForge the forge, or {@code null}.
	 * @param rawProduct the product, or {@code null}.
	 * @param rawVersion the version, or {@code null}.
	 * @param rawPath the path.
	 * @throws IllegalArgumentException if the argument does not satisfy the further constraints of a
	 *             {@link FastenURI}.
	 * @see #create(String, String, String)
	 */

	public static FastenURI create(final String rawForge, final String rawProduct, final String rawVersion, final String rawPath) {
		return create(new StringBuilder().append("fasten:"), rawForge, rawProduct, rawVersion, rawPath);
	}

	/**
	 * Creates a <em>schemeless</em> {@link FastenURI} from given raw (i.e., properly escaped)
	 * fine-grained components.
	 *
	 * <p>
	 * No check is performed on the correctness of the components.
	 *
	 * @param rawForge the forge, or {@code null}.
	 * @param rawProduct the product, or {@code null}.
	 * @param rawVersion the version, or {@code null}.
	 * @param rawPath the path.
	 * @throws IllegalArgumentException if the argument does not satisfy the further constraints of a
	 *             {@link FastenURI}.
	 * @see #create(String, String, String)
	 */

	public static FastenURI createSchemeless(final String rawForge, final String rawProduct, final String rawVersion, final String rawPath) {
		return create(new StringBuilder(), rawForge, rawProduct, rawVersion, rawPath);
	}

	/**
	 * Creates a {@link FastenURI} from given raw (i.e., properly escaped) coarse-grained components.
	 *
	 * <p>
	 * No check is performed on the correctness of the components.
	 *
	 * @param rawForgeProductVersion forge, product, and version, combined, or {@code null}.
	 * @param rawNamespace the namespace, or {@code null}.
	 * @param rawEntity the entity, or {@code null}.
	 * @throws IllegalArgumentException if the argument does not satisfy the further constraints of a
	 *             {@link FastenURI}.
	 * @see #create(String, String, String, String, String)
	 */
	public static FastenURI create(final String rawForgeProductVersion, final String rawNamespace, final String rawEntity) {
		return new FastenURI(URI.create("fasten://" + rawForgeProductVersion + "/" + rawNamespace + "/" + rawEntity));
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

      protected void validateRawForge() {
	   if (rawForge != null && rawForge.indexOf('$') >= 0) throw new IllegalArgumentException("Version / forge inverted or mixed");
      }

      protected void validateRawVersion() {
	   if (rawVersion != null && rawVersion.indexOf('$') >= 0) throw new IllegalArgumentException("More than one version");
      }

      protected void validateRawProduct() {
          if (rawProduct != null && rawProduct.length() == 0) throw new IllegalArgumentException("The product cannot be empty");
      }

      protected void validateRawNamespace() {
          if (rawNamespace != null && rawNamespace.length() == 0) throw new IllegalArgumentException("The namespace cannot be empty");
      }

      protected void validateRawEntity() {
          if (rawEntity != null && rawEntity.length() == 0) throw new IllegalArgumentException("The entity part cannot be empty");
          if (rawEntity != null && rawEntity.indexOf(':') >= 0) throw new IllegalArgumentException("The entity part cannot contain colons");
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

	/** Relativizes the provided FASTEN URI with respected to this FASTEN URI.
	 *
	 * <p>The definition of relativization for FASTEN URIs is slightly more general than
	 * that used by {@link URI#relativize(URI)}: equal prefixes formed by
	 * {@code forge-product-version} and possibly {@code namespace} between this URI
	 * and the provided URI will be erased by the relativization process. In particular,
	 * this URI needs not be a prefix of the provided URI to relativize successfully.
	 *
	 * <p>It is guaranteed that u is a suffix of v.resolve(v.relativize(u)).
	 * <p>It is guaranteed that v.relativize(u) is exactly v.relativize(v.resolve(u)) (modulo, possibly, the scheme part).
	 *
	 * @param u a FASTEN URI.
	 * @return {@code u} relativized to this FASTEN URI.
	 */
	public FastenURI relativize(final FastenURI u) {
		if (rawNamespace == null) throw new IllegalStateException("You cannot relativize without a namespace");
		final String rawAuthority = u.uri.getRawAuthority();
		// There is an authority and it doesn't match: return u
		if (rawAuthority != null && ! rawAuthority.equals(uri.getRawAuthority())) return u;
		// Matching authorities, or no authority, and there's a namespace, and it doesn't match: return namespace + entity
		if (u.rawNamespace != null && ! rawNamespace.equals(u.rawNamespace)) return FastenURI.create("/" + u.rawNamespace + "/" +  u.rawEntity);
		// Matching authorities, or no authority, matching namespaces, or no namespace: return entity
		return FastenURI.create(u.getRawEntity());
	}

	public String getScheme() {
		return uri.getScheme();
	}

	/** Returns the {@linkplain URI#getPath() path} of this FASTEN URI.
	 *
	 * <p>WARNING: contrarily to {@link URI}, we return {@code null} for empty paths.
	 *
	 * @return the path, or {@code null} if there is no path.
	 */
	public String getPath() {
		final String path = uri.getPath();
		assert path != null;
		return path.length() == 0 ? null : path;
	}

	/** Returns the {@linkplain URI#getRawPath() raw path} of this FASTEN URI.
	 *
	 * <p>WARNING: contrarily to {@link URI}, we return {@code null} for empty paths.
	 *
	 * @return the raw path, or {@code null} if there is no path.
	 */
	public String getRawPath() {
		final String path = uri.getRawPath();
		assert path != null;
		return path.length() == 0 ? null : path;
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

	/** A no-op canonicalization method.
	 *
	 * @return this {@link FastenURI}.
	 */
	public FastenURI canonicalize() {
		return this;
	}

	public FastenURI decanonicalize() {
		return this;
	}
}
