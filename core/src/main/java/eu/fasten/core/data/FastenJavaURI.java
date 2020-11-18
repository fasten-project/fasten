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

import it.unimi.dsi.fastutil.chars.CharOpenHashSet;
import java.net.URI;

/**
 * A class representing a Fasten URI for the Java language; it has to be considered experimental
 * until the BNF for such URIs is set in stone.
 */
public class FastenJavaURI extends FastenURI {

    private final static long serialVersionUID = 1L;
    private final static FastenJavaURI[] NO_ARGS_ARRAY = new FastenJavaURI[0];
    private final static CharOpenHashSet typeChar = new CharOpenHashSet(new char[]{
            '-', '.', '_', '~', // unreserved
            '!', '$', '&', '\'', '*', ';', '=', // sub-delims-type
            '@'}, .5f);

    private final String className;
    private final String functionOrAttributeName;
    private final FastenJavaURI[] args;
    private final FastenJavaURI returnType;

    /**
     * Constructs a {@link FastenJavaURI} from a {@link String} uri.
     *
     * @param s a {@link String} a specifying a {@link FastenJavaURI}.
     */
    public FastenJavaURI(final URI s, final String className, final String functionName,
                         FastenJavaURI[] args, FastenJavaURI returnType) {
        super(s);
        this.className = className;
        this.functionOrAttributeName = functionName;
        this.args = args;
        this.returnType = returnType;
    }

    /**
     * Constructs a {@link FastenJavaURI} from a {@link String} uri.
     *
     * @param s a {@link String} a specifying a {@link FastenJavaURI}.
     */
    public FastenJavaURI(final String s) {
        super(URI.create(s));
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

        final var openParenPos = funcArgsTypeSpec.indexOf('(');
        if (openParenPos == -1) { // entity-attribute
            checkForCommasParenthesesOrDots(funcArgsTypeSpec);
            args = null;
            returnType = null;
            functionOrAttributeName = decode(funcArgsTypeSpec);
            return;
        }

        final String functionOrAttributeNameSpec = funcArgsTypeSpec.substring(0, openParenPos);
        checkForCommasParenthesesOrDots(functionOrAttributeNameSpec);
        functionOrAttributeName = decode(functionOrAttributeNameSpec);

        final var closedParenPos = funcArgsTypeSpec.indexOf(')');
        if (closedParenPos == -1) throw new IllegalArgumentException("Missing close parenthesis");

        final String returnTypeSpec = funcArgsTypeSpec.substring(closedParenPos + 1);
        checkForCommasAndParentheses(returnTypeSpec);
        returnType = FastenJavaURI.createWithoutFunction(decode(returnTypeSpec));

        final var argString = funcArgsTypeSpec.substring(openParenPos + 1, closedParenPos);
        if (argString.length() == 0) {
            args = NO_ARGS_ARRAY;
            return;
        }

        final var a = argString.split(",");
        args = new FastenJavaURI[a.length];
        for (int i = 0; i < a.length; i++) {
            checkForCommasAndParentheses(a[i]);
            args[i] = FastenJavaURI.createWithoutFunction(decode(a[i]));
        }
    }

    /**
     * Returns the name of the class associated with this FASTEN Java URI.
     *
     * @return the name of the class associated with this FASTEN Java URI
     */
    public String getClassName() {
        return className;
    }

    /**
     * Returns the name of the method or attribute associated with this FASTEN Java URI.
     *
     * @return the name of the method or attribute associated with this FASTEN Java URI
     */
    public String getEntityName() {
        return functionOrAttributeName;
    }

    /**
     * Returns the arguments of the method associated with this FASTEN Java URI, or {@code null}.
     *
     * @return the arguments of the method associated with this FASTEN Java URI
     */
    public FastenJavaURI[] getArgs() {
        return args.clone(); // defensive copy?
    }

    /**
     * Returns the return type of the method associated with this FASTEN Java URI, or {@code null}.
     *
     * @return the return type of the method associated with this FASTEN Java URI
     */
    public FastenJavaURI getReturnType() {
        return returnType;
    }

    /**
     * Creates a {@link FastenJavaURI} from components.
     *
     * <P>Please note that while {@link FastenURI} components must be provided <em>raw</em>, all
     * other components must not be.
     *
     * @param rawForge    the forge, or {@code null}.
     * @param rawProduct  the product, or {@code null}.
     * @param rawVersion  the version, or {@code null}.
     * @param rawTypeName the name of the type.
     * @param rawFunction the name of the function (if {@code returnType} is not null or attribute.
     * @param argTypes    the types of arguments of the function; an empty array or null if empty
     * @param returnType  the return type ({@code null} for attributes).
     * @return a {@link FastenJavaURI}.
     * @throws IllegalArgumentException if the argument does not satisfy the constraints
     */
    public static FastenJavaURI create(final String rawForge, final String rawProduct,
                                       final String rawVersion, final String rawNamespace,
                                       final String rawTypeName, final String rawFunction,
                                       final FastenJavaURI[] argTypes,
                                       final FastenJavaURI returnType) {
        final StringBuilder entitysb = new StringBuilder();
        final var typeName = validateURI(rawTypeName, true) ? rawTypeName : pctEncodeArg(rawTypeName);
        final var function = validateURI(rawFunction, true) ? rawFunction : pctEncodeArg(rawFunction);
        entitysb.append(typeName).append('.');
        entitysb.append(function);

        if (returnType != null) {
            entitysb.append('(');

            if (argTypes != null)
                for (int i = 0; i < argTypes.length; i++) {
                    if (i > 0) entitysb.append(',');
                    entitysb.append(pctEncodeArg(decode(argTypes[i].uri.toString())));
                }
            entitysb.append(')');
            entitysb.append(pctEncodeArg(decode(returnType.uri.toString())));

        } else if (argTypes != null && argTypes.length > 0)
            throw new IllegalArgumentException("You cannot specify argument types for an attribute");

        final var fastenURI = FastenURI.create(rawForge, rawProduct, rawVersion, rawNamespace, entitysb.toString());
        return new FastenJavaURI(fastenURI.uri, typeName, function, argTypes, returnType);
    }

    /**
     * Creates a {@link FastenJavaURI} from a String.
     *
     * @param s a string specifying a {@link FastenJavaURI}.
     * @return a {@link FastenJavaURI}.
     */
    public static FastenJavaURI create(final String s) {
        return new FastenJavaURI(s);
    }

    /**
     * Create a FastenJavaURI for uri-s without a function, e.g.
     * the following format: /name.space/ClassName
     *
     * @param s uri
     * @return a {@link FastenJavaURI}
     */
    public static FastenJavaURI createWithoutFunction(final String s) {
        var decoded = decode(s);
        var slashIndex = decoded.indexOf("/", 1);
        var namespace = decoded.substring(0, slashIndex + 1);
        var className = decoded.substring(slashIndex + 1);

        className = validateURI(className, false) ? className : pctEncodeArg(className);

        return new FastenJavaURI(namespace + className);
    }

    /**
     * Checks whether a uri contains commas or parentheses, and throws an IllegalArgumentException
     * if any of those characters were found.
     *
     * @param returnTypeSpec uri to check
     */
    private static void checkForCommasAndParentheses(final String returnTypeSpec) {
        if (containsCommasAndParentheses(returnTypeSpec))
            throw new IllegalArgumentException("No parentheses or commas are allowed in type components");
    }

    /**
     * Checks whether a uri contains commas or parentheses.
     *
     * @param returnTypeSpec uri to check
     * @return true if any commas or parentheses found
     */
    private static boolean containsCommasAndParentheses(final String returnTypeSpec) {
        return returnTypeSpec.indexOf(',') != -1
                || returnTypeSpec.indexOf('(') != -1
                || returnTypeSpec.indexOf(')') != -1;
    }

    /**
     * Checks whether a uri contains commas, parentheses, or dots, and throws an
     * IllegalArgumentException if any of those characters were found.
     *
     * @param returnTypeSpec uri to check
     */
    private static void checkForCommasParenthesesOrDots(final String returnTypeSpec) {
        if (containsCommasParenthesesOrDots(returnTypeSpec))
            throw new IllegalArgumentException("No parentheses, commas or dots are allowed in entity components");
    }

    /**
     * Checks whether a uri contains commas, parentheses, or dots.
     *
     * @param returnTypeSpec uri to check
     * @return true if any commas, parentheses, or dots found
     */
    private static boolean containsCommasParenthesesOrDots(final String returnTypeSpec) {
        return returnTypeSpec.indexOf('.') != -1
                || returnTypeSpec.indexOf(',') != -1
                || returnTypeSpec.indexOf('(') != -1
                || returnTypeSpec.indexOf(')') != -1;
    }

    /**
     * Validates a given uri.
     *
     * @param s        a uri to validate
     * @param checkDot true if dots are not permitted in the uri
     * @return true if validation passed
     */
    private static boolean validateURI(final String s, final boolean checkDot) {
        if (checkDot && containsCommasParenthesesOrDots(s)) {
            return false;
        } else if (containsCommasAndParentheses(s)) {
            return false;
        }
        try {
            URI.create(s);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Percent encode a uri.
     *
     * @param s non-encoded uri
     * @return pct-encoded uri
     */
    public static String pctEncodeArg(final String s) {
        // Encoding characters not in arg-char (see BNF)
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c < 0x7F && !Character.isLetterOrDigit(c) && !typeChar.contains(c))
                sb.append("%").append(String.format("%02X", Integer.valueOf(c)));
            else
                sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Relativizes the provided FASTEN Java URI with respected to this FASTEN Java URI.
     *
     * <p>Note that this method is only a convenient version of {@link FastenURI#relativize(FastenURI)},
     * which it overrides.
     *
     * @param u a FASTEN Java URI.
     * @return {@code u} relativized to this FASTEN Java URI.
     * @see FastenURI#relativize(FastenURI)
     */
    @Override
    public FastenJavaURI relativize(final FastenURI u) {
        if (rawNamespace == null)
            throw new IllegalStateException("You cannot relativize without a namespace");
        final String rawAuthority = u.uri.getRawAuthority();
        // There is an authority and it doesn't match: return u
        if (rawAuthority != null && !rawAuthority.equals(uri.getRawAuthority()))
            return u instanceof FastenJavaURI ? (FastenJavaURI) u : create(u.uri.toString());
        // Matching authorities, or no authority, and there's a namespace, and it doesn't match: return namespace + entity
        if (u.rawNamespace != null && !rawNamespace.equals(u.rawNamespace))
            return FastenJavaURI.create("/" + u.rawNamespace + "/" + u.rawEntity);
        // Matching authorities, or no authority, matching namespaces, or no namespace: return entity
        return FastenJavaURI.create(u.getRawEntity());
    }

    public FastenJavaURI derelativize(final FastenURI u) {
        if (u.rawNamespace == null) {
            return FastenJavaURI.create("/" + this.rawNamespace + "/" + u.rawEntity);
        }
        return FastenJavaURI.create(u.toString());
    }

    public FastenJavaURI decanonicalize() {
        final FastenJavaURI[] derelativizedArgs = new FastenJavaURI[args.length];

        for (int i = 0; i < args.length; i++) derelativizedArgs[i] = derelativize(args[i]);
        final FastenJavaURI derelativizedReturnType = derelativize(returnType);
        return FastenJavaURI.create(rawForge, rawProduct, rawVersion, rawNamespace, className, functionOrAttributeName, derelativizedArgs, derelativizedReturnType);
    }

    public FastenJavaURI resolve(final FastenJavaURI u) {
        // Standard resolution will work; might be more efficient
        return create(this.uri.resolve(u.uri).toString());
    }

    @Override
    public FastenJavaURI canonicalize() {
        final FastenJavaURI[] relativizedArgs = new FastenJavaURI[args.length];

        for (int i = 0; i < args.length; i++) relativizedArgs[i] = relativize(args[i]);
        final FastenJavaURI relativizedReturnType = relativize(returnType);
        return FastenJavaURI.create(rawForge, rawProduct, rawVersion, rawNamespace, className, functionOrAttributeName, relativizedArgs, relativizedReturnType);
    }
}
