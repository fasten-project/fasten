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
	/** The forge of the {@link #product} associated with this FastenURI, or {@code null} if the forge is not specified. */
	protected final String forge;
	/** The product associated with this FastenURI, or {@code null} if the product is not specified. */
	protected final String product;
	/** The {@link #product} version, or {@code null} if the version is not specified. */
	protected final String version;
	/** The module, or {@code null} if the module is not specified. */
	protected final String namespace;
	/** The language-dependent part, or {@code null} if the language-dependent part is not specified. */
	protected final String entity;

	protected FastenURI(final URI uri) {
		this.uri = uri;
		if (uri.getScheme() != null && ! "fasten".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Scheme, if specified, must be 'fasten'");
		final String forgeProductVersion = uri.getAuthority();

		if (forgeProductVersion == null) {
			forge = product = version = null;
		}
		else {
			final var exclPos = forgeProductVersion.indexOf('!');
			String productVersion;
			if (exclPos == -1) { // No forge
				forge = null;
				productVersion = forgeProductVersion;
			}
			else {
				forge = forgeProductVersion.substring(0,  exclPos);
				productVersion = forgeProductVersion.substring(exclPos + 1);
				if (productVersion.indexOf('!') >= 0) throw new IllegalArgumentException("More than one forge");
				if (forge.indexOf('$') >= 0) throw new IllegalArgumentException("Version / forge inverted or mixed");
			}

			final var dollarPos = productVersion.indexOf('$');
			if (dollarPos == -1) {
				product = productVersion;
				version = null;
			}
			else {
				product = productVersion.substring(0, dollarPos);
				version = productVersion.substring(dollarPos + 1);
				if (version.indexOf('$') >= 0) throw new IllegalArgumentException("More than one version");
			}

			if (product.length() == 0) throw new IllegalArgumentException("The product cannot be empty");
		}

		final var path = uri.getRawPath();

		if (path == null || path.length() == 0) {
			namespace = entity = null;
			return;
		}

		int slashPos;
		if (path.charAt(0) == '/') { // We have a namespace
			slashPos = path.indexOf('/', 1); // Skip first slash

			if (slashPos == -1)  throw new IllegalArgumentException("Missing entity");
			namespace = path.substring(1, slashPos);

			if (namespace.length() == 0) throw new IllegalArgumentException("The namespace cannot be empty");
			entity = path.substring(slashPos + 1);
		}
		else {
			namespace = null;
			entity = path;
		}

		if (entity.length() == 0) throw new IllegalArgumentException("The entity part cannot be empty");
		if (entity.indexOf(':') >= 0) throw new IllegalArgumentException("The entity part cannot contain colons");
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

	public String getForge() {
		return forge;
	}

	public String getProduct() {
		return product;
	}

	public String getVersion() {
		return version;
	}

	public String getEntity() {
		return entity;
	}

	public String getNamespace() {
		return namespace;
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

	public String toASCIIString() {
		return uri.toASCIIString();
	}

	// TODO: hash() / equals()
}
