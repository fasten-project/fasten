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

/** A class representing a Fasten URI for the Python language; it has to be considered experimental
 *  until the BNF for such URIs is set in stone. */

public class FastenPythonURI extends FastenURI {
    protected final String namespace;
    protected final String identifier;
    protected final boolean isFunction;

    public FastenPythonURI(final String s) {
        this(URI.create(s));
    }

    public FastenPythonURI(final URI uri) {
        super(uri);

        // If rawEntity does not exist we cannot identify any of these values
        if (rawEntity == null) {
            namespace = null;
            identifier = null;
            isFunction = false;
            return;
        }

        String s;
        s = decode(rawEntity);

        final var openParenPos = s.indexOf('(');

        if (rawNamespace == null) {
            namespace = "";
            isFunction = false;
        }
        else {
            namespace = decode(rawNamespace);
            isFunction = openParenPos != -1;
        }

        if (isFunction) {
            // Function names should end with "()"
            if (s.length() == openParenPos + 1 || s.charAt(openParenPos + 1) != ')') {
                throw new IllegalArgumentException("Open parenthesis, but no closed parenthesis");
            }
            if (s.length() != openParenPos + 2) {
                throw new IllegalArgumentException("Garbage after closed parenthesis");
            }
            // Remove "()" suffix from the identifier
            identifier = s.substring(0, s.length() - 2);
        }
        else {
            identifier = s;
        }

        if (namespace.isEmpty()) {
            if (identifier.isEmpty()) {
                throw new IllegalArgumentException("Either namespace or identifier must be set");
            }
            if (rawProduct == null || rawProduct.isEmpty()) {
                throw new IllegalArgumentException("Either namespace or product must be set");
            }
        }
    }

    /**
     * Creates a {@link FastenPythonURI} from a string, with the same logic of {@link URI#create(String)}.
     * @param s a string specifying a {@link FastenPythonURI}.
     * @return a {@link FastenPythonURI}.
     */
    public static FastenPythonURI create(final String s) {
        return new FastenPythonURI(URI.create(s));
    }

    /**
     * Creates a {@link FastenPythonURI} from a {@link URI}.
     * @param uri a {@link URI} a specifying a {@link FastenPythonURI}.
     * @return a {@link FastenPythonURI}.
     * @throws IllegalArgumentException if the argument does not satisfy the further constraints of a {@link FastenPythonURI}.
     */
    public static FastenPythonURI create(final URI uri) {
        return new FastenPythonURI(uri);
    }

    /**
     * Creates a {@link FastenPythonURI} from components.
     *
     * <P>Please note that while {@link FastenURI} components must be provided <em>raw</em>, all
     * other components must not be.
     *
     * @param rawForge the forge, or {@code null}.
     * @param rawProduct the product, or {@code null}.
     * @param rawVersion the version, or {@code null}.
     * @param namespace the module namespace, if present, or {@code null} if unresolved.
     * @param identifier the full function namespace inside the module.
     * @param isFunction whether the returned URI refers to a variable or function.
     * @throws IllegalArgumentException if the argument does not satisfy the further constraints of a {@link FastenPythonURI}.
     */
    public static FastenPythonURI create(final String rawForge, final String rawProduct, final String rawVersion,
            final String namespace, final String identifier, final boolean isFunction) {
        final StringBuffer entitysb = new StringBuffer();
        final String rawNamespace;
        final FastenURI fastenURI;

        if (namespace == null && identifier == null) {
            throw new IllegalArgumentException("Both namespace and identifier can't be NULL.");
        }

        // unresolved namespace means an external call, so we need the product
        if (namespace == null && rawProduct == null) {
            throw new IllegalArgumentException("Either namespace or product must be set.");
        }

        entitysb.append(identifier);
        rawNamespace = namespace != null ? namespace : "";

        // if namespace is null we can't know if it is a function or not
        // since it is an external call
        if (namespace != null && isFunction) {
            entitysb.append("()");
        }

        // The code below is copied from FastenURI.create
        // because we want to use FastenPythonURI's validation
        // methods (empty namespaces and entitys are allowed).
        final StringBuilder urisb = new StringBuilder();
        final String rawEntity = entitysb.toString();
        urisb.append("fasten:");

        if (rawProduct != null) {
            urisb.append("//");
            if (rawForge != null) {
                urisb.append(rawForge + "!");
            }
            urisb.append(rawProduct);
            if (rawVersion != null) {
                urisb.append("$" + rawVersion);
            }
            urisb.append("/" + rawNamespace + "/" + rawEntity);
        }
        else {
            urisb.append("/" + rawNamespace + "/" + rawEntity);
        }

        return create(URI.create(urisb.toString()));
    }

    /** Returns the entity identifier associated with this FASTEN Python URI.
     *
     * @return the entity identifier associated with this FASTEN Python URI.
     */
    public String getIdentifier() {
        return identifier;
    }

    /** Returns whether this FASTEN Python URI represents a function.
     *
     * @return whether this FASTEN Python URI represents a function.
     */
    public boolean isFunction() {
        return isFunction;
    }

    /** Returns the namespace of this Fasten Python URI.
     *
     * @return the namespace of this Fasten Python URI.
     */
    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    protected void validateRawNamespace() {}

    @Override
    protected void validateRawEntity() {
        if (rawEntity != null && rawEntity.indexOf(':') >= 0) {
            throw new IllegalArgumentException("The entity part cannot contain colons");
        }
    }

    /** Relativizes the provided FASTEN Python URI with respect to this FASTEN Python URI.
     *
     * <p>Note that this method is only a convenient version of {@link FastenURI#relativize(FastenURI)},
     * which it overrides.
     * @param u a FASTEN Python URI.
     * @return {@code u} relativized to this FASTEN Python URI.
     * @see FastenURI#relativize(FastenURI)
     */
    @Override
    public FastenPythonURI relativize(final FastenURI u) {
        if (rawNamespace == null) {
            throw new IllegalStateException("You cannot relativize without a namespace");
        }

        // We can only relativize URIs that cotain both namespace and entity
        // meaning URIs assigned to inner-modular calls
        if (u.getRawNamespace() == null || u.getRawNamespace().isEmpty()) {
            throw new IllegalArgumentException("You cannot relativize a URI without a namespace");
        }
        if (u.getRawEntity() == null || u.getRawEntity().isEmpty()) {
            throw new IllegalArgumentException("You cannot relativize a URI without an entity");
        }

        final String rawAuthority = u.uri.getRawAuthority();
        // There is an authority and it doesn't match: return u
        if (rawAuthority != null && ! rawAuthority.equals(uri.getRawAuthority())) {
            return u instanceof FastenPythonURI ? (FastenPythonURI) u : create(u.uri);
        }

        return FastenPythonURI.create("/" + u.getRawNamespace() + "/" +  u.getRawEntity());
    }

    public FastenPythonURI resolve(final FastenPythonURI u) {
        // Standard resolution will work; might be more efficient
        return create(this.uri.resolve(u.uri));
    }

    @Override
    public FastenPythonURI canonicalize() {
        return FastenPythonURI.create(rawForge, rawProduct, rawVersion, namespace, identifier, isFunction);
    }
}
