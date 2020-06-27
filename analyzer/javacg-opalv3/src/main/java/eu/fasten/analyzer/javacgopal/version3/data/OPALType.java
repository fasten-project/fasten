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

package eu.fasten.analyzer.javacgopal.version3.data;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import eu.fasten.analyzer.javacgopal.version3.ExtendedRevisionCallGraphV3;
import eu.fasten.analyzer.javacgopal.version3.scalawrapper.JavaToScalaConverter;
import eu.fasten.core.data.FastenURI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.opalj.br.ClassHierarchy;
import org.opalj.br.DeclaredMethod;
import org.opalj.br.Method;
import org.opalj.br.ObjectType;
import org.opalj.collection.immutable.Chain;
import scala.collection.JavaConverters;

/**
 * Analyze OPAL types.
 */
public class OPALType {

    private final String sourceFileName;
    private final Map<Method, Integer> methods;
    private final Chain<ObjectType> superClasses;
    private final List<ObjectType> superInterfaces;

    /**
     * Creates {@link OPALType} for the given data.
     *
     * @param methods         a map of methods in this type together with their ids.
     * @param superClasses    a {@link Chain} of classes that this type extends.
     * @param superInterfaces a list of interfaces that this type implements.
     * @param sourceFileName  name of the source file that this type belongs to.
     */
    public OPALType(final Map<Method, Integer> methods, final Chain<ObjectType> superClasses,
                    final List<ObjectType> superInterfaces, final String sourceFileName) {
        this.methods = methods;
        this.superClasses = superClasses;
        this.superInterfaces = superInterfaces;
        this.sourceFileName = sourceFileName;
    }

    public Map<Method, Integer> getMethods() {
        return methods;
    }

    public Chain<ObjectType> getSuperClasses() {
        return superClasses;
    }

    public List<ObjectType> getSuperInterfaces() {
        return superInterfaces;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    /**
     * Get a map of {@link FastenURI} of Type and
     * corresponding {@link ExtendedRevisionCallGraphV3.Type}.
     *
     * @param projectHierarchy class hierarchy of the project
     * @param methods          methods belonging to this type
     * @param aClass           object type
     * @return map of FastenURI and corresponding Types
     */
    public static Map<FastenURI, ExtendedRevisionCallGraphV3.Type> getType(ClassHierarchy projectHierarchy,
                                                                           final Map<DeclaredMethod, Integer> methods,
                                                                           final ObjectType aClass) {
        final var superTypes = extractSuperClasses(projectHierarchy, aClass);

        final LinkedList<FastenURI> superClassesURIs;
        if (superTypes != null) {
            superClassesURIs = toURIClasses(superTypes);
        } else {
            superClassesURIs = new LinkedList<>();
        }

        return Map.of(OPALMethod.getTypeURI(aClass),
                new ExtendedRevisionCallGraphV3.Type("",
                        toURIDeclaredMethods(methods),
                        superClassesURIs,
                        toURIInterfaces(extractSuperInterfaces(projectHierarchy, aClass))));
    }

    /**
     * Get a map of {@link FastenURI} of Type and
     * corresponding {@link ExtendedRevisionCallGraphV3.Type}.
     *
     * @param type   OPAL type
     * @param aClass object type
     * @return map of FastenURI and corresponding Types
     */
    public static Map<FastenURI, ExtendedRevisionCallGraphV3.Type> getType(final OPALType type,
                                                                           final ObjectType aClass) {
        final LinkedList<FastenURI> superClassesURIs;
        if (type.getSuperClasses() != null) {
            superClassesURIs = toURIClasses(type.getSuperClasses());
        } else {
            superClassesURIs = new LinkedList<>();
        }

        return Map.of(OPALMethod.getTypeURI(aClass),
                new ExtendedRevisionCallGraphV3.Type(type.getSourceFileName(),
                        toURIMethods(type.getMethods()),
                        superClassesURIs,
                        toURIInterfaces(type.getSuperInterfaces())));
    }

    /**
     * Convert a map of {@link DeclaredMethod} to a BiMap of
     * {@link ExtendedRevisionCallGraphV3.Node}.
     *
     * @param methods map of methods to convert
     * @return BiMap of Nodes
     */
    public static BiMap<Integer, ExtendedRevisionCallGraphV3.Node> toURIDeclaredMethods(
            final Map<DeclaredMethod, Integer> methods) {
        final BiMap<Integer, ExtendedRevisionCallGraphV3.Node> result = HashBiMap.create();

        for (final var entry : methods.entrySet()) {
            final var method = entry.getKey();
            result.put(entry.getValue(), new ExtendedRevisionCallGraphV3.Node(
                    OPALMethod.toCanonicalSchemelessURI(null,
                            method.declaringClassType(), method.name(),
                            method.descriptor()), new HashMap<>()));
        }
        return result;
    }

    /**
     * Converts a chain of interfaces to Fasten URIs
     *
     * @param types interfaces
     * @return list of URIs
     */
    public static List<FastenURI> toURIInterfaces(final List<ObjectType> types) {
        final List<FastenURI> result = new ArrayList<>();
        for (final var aClass : types) {
            result.add(OPALMethod.getTypeURI(aClass));
        }
        return result;
    }

    /**
     * Converts a chain of classes to Fasten URIs
     *
     * @param types chain of types
     * @return list of URIs
     */
    public static LinkedList<FastenURI> toURIClasses(final Chain<ObjectType> types) {
        final LinkedList<FastenURI> result = new LinkedList<>();

        types.foreach(JavaToScalaConverter.asScalaFunction1(
                klass -> result.add(OPALMethod.getTypeURI((ObjectType) klass))));

        return result;
    }

    /**
     * Converts a {@link Map} of {@link Method} to a Map of {@link FastenURI}. And also
     * shifts the keys and values.
     *
     * @param methods {@link Method} are keys and their unique id in the artifact are
     *                values.
     * @return A Map in which the unique id of each method in the artifact is the key and the
     * {@link FastenURI} of the method is the value.
     */
    public static BiMap<Integer, ExtendedRevisionCallGraphV3.Node> toURIMethods(
            final Map<Method, Integer> methods) {
        final BiMap<Integer, ExtendedRevisionCallGraphV3.Node> result = HashBiMap.create();

        for (final var entry : methods.entrySet()) {
            final var method = entry.getKey();
            result.put(entry.getValue(), new ExtendedRevisionCallGraphV3.Node(getUri(method),
                    Map.of("first", getFirstLine(method),
                            "last", getLastLine(method),
                            "defined", method.instructionsOption().isDefined(),
                            "access: ", getAccessModifier(method))));
        }
        return result;
    }

    /**
     * Converts method to a canonical schemeless URI.
     *
     * @param method method
     * @return method URI
     */
    private static FastenURI getUri(Method method) {
        return OPALMethod.toCanonicalSchemelessURI(null, method.declaringClassFile().thisType(),
                method.name(), method.descriptor());
    }

    /**
     * Get the first line of the method.
     *
     * @param method method
     * @return last line of the method
     */
    private static Object getFirstLine(Method method) {
        return method.body().nonEmpty()
                ? method.body().get().firstLineNumber().getOrElse(() -> "") : "notFound";
    }

    /**
     * Get the last line of the method.
     *
     * @param method method
     * @return last line of the method
     */
    private static Object getLastLine(Method method) {
        return method.body().nonEmpty()
                ? method.body().get().lineNumber(method.body().get().codeSize()).getOrElse(() -> "")
                : "notFound";
    }

    /**
     * Get access modifier for of a method or else 'notFound'.
     *
     * @param method method
     * @return access modifier
     */
    private static String getAccessModifier(Method method) {
        if (method.isPrivate()) {
            return "private";
        } else if (method.isPublic()) {
            return "public";
        } else if (method.isPackagePrivate()) {
            return "packagePrivate";
        } else if (method.isProtected()) {
            return "protected";
        }
        return "notFound";
    }

    /**
     * Extract super classes of a given type from a given CHA.
     *
     * @param classHierarchy class hierarchy of the artifact to be checked for super classes
     * @param currentClass   type that to be checked for super classes
     * @return A {@link Chain} of {@link ObjectType} as super classes of the passed type.
     */
    public static Chain<ObjectType> extractSuperClasses(final ClassHierarchy classHierarchy,
                                                        final ObjectType currentClass)
            throws NoSuchElementException {
        if (classHierarchy.supertypes(currentClass).nonEmpty()) {
            final var superClasses =
                    classHierarchy.allSuperclassTypesInInitializationOrder(currentClass).s();
            if (superClasses != null) {
                return superClasses.reverse();
            }
        }
        return null;
    }

    /**
     * Extract super Interfaces of a given type from a given CHA.
     *
     * @param classHierarchy class hierarchy of the artifact to be checked for super interfaces
     * @param currentClass   type that to be checked for super interfaces
     * @return A list of {@link ObjectType} as super interfaces of the passed type.
     */
    public static List<ObjectType> extractSuperInterfaces(final ClassHierarchy classHierarchy,
                                                          final ObjectType currentClass) {
        return Lists.newArrayList(JavaConverters.asJavaIterable(classHierarchy
                .allSuperinterfacetypes(currentClass, false)));
    }
}
