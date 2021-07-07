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

package eu.fasten.analyzer.javacgopal.data.analysis;

import com.google.common.collect.Lists;
import eu.fasten.core.data.JavaNode;
import eu.fasten.core.data.JavaType;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.Node;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.opalj.br.ClassHierarchy;
import org.opalj.br.DeclaredMethod;
import org.opalj.br.Method;
import org.opalj.br.ObjectType;
import scala.collection.JavaConverters;

/**
 * Analyze OPAL types.
 */
public class OPALType {

    private final String sourceFileName;
    private final Map<Method, Integer> methods;
    private final LinkedList<ObjectType> superClasses;
    private final List<ObjectType> superInterfaces;
    private final String access;
    private final boolean isFinal;
    private final Map<String, List<Pair<String, String>>> annotations;

    /**
     * Creates {@link OPALType} for the given data.
     *
     * @param methods         a map of methods in this type together with their ids.
     * @param superClasses    a {@link LinkedList} of classes that this type extends.
     * @param superInterfaces a list of interfaces that this type implements.
     * @param sourceFileName  name of the source file that this type belongs to.
     */
    public OPALType(final Map<Method, Integer> methods, final LinkedList<ObjectType> superClasses,
                    final List<ObjectType> superInterfaces, final String sourceFileName,
                    final String access, final boolean isFinal,
                    final Map<String, List<Pair<String, String>>> annotations) {
        this.methods = methods;
        this.superClasses = superClasses;
        this.superInterfaces = superInterfaces;
        this.sourceFileName = sourceFileName;
        this.access = access;
        this.isFinal = isFinal;
        this.annotations = annotations;
    }

    public Map<Method, Integer> getMethods() {
        return methods;
    }

    public LinkedList<ObjectType> getSuperClasses() {
        return superClasses;
    }

    public List<ObjectType> getSuperInterfaces() {
        return superInterfaces;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public Map<String, List<Pair<String, String>>> getAnnotations() {
        return annotations;
    }

    /**
     * Get a map of {@link FastenURI} of Type and
     * corresponding {@link JavaType}.
     *
     * @param projectHierarchy class hierarchy of the project
     * @param methods          methods belonging to this type
     * @param klass            object type
     * @return map of FastenURI and corresponding Types
     */
    public static Map<String, JavaType> getType(ClassHierarchy projectHierarchy,
                                               final Map<DeclaredMethod, Integer> methods,
                                               final ObjectType klass) {
        final var superTypes = extractSuperClasses(projectHierarchy, klass);

        final LinkedList<FastenURI> superClassesURIs;
        if (superTypes != null) {
            superClassesURIs = toURIClasses(superTypes);
        } else {
            superClassesURIs = new LinkedList<>();
        }

        String uri = OPALMethod.getTypeURI(klass).toString();
		return Map.of(uri,
                new JavaType(uri, "", toURIDeclaredMethods(methods), new HashMap<>(),superClassesURIs,
                        toURIInterfaces(extractSuperInterfaces(projectHierarchy, klass)),
                        "", false, new HashMap<>()));
    }

    /**
     * Get a map of {@link FastenURI} of Type and
     * corresponding {@link JavaType}.
     *
     * @param type  OPAL type
     * @param klass object type
     * @return map of FastenURI and corresponding Types
     */
    public static Pair<String, JavaType> getType(final OPALType type, final ObjectType klass) {
        final LinkedList<FastenURI> superClassesURIs;
        if (type.getSuperClasses() != null) {
            superClassesURIs = toURIClasses(type.getSuperClasses());
        } else {
            superClassesURIs = new LinkedList<>();
        }
        final var methodMaps = getMethodMaps(type.methods);
        final String uri = OPALMethod.getTypeURI(klass).toString();
		return MutablePair.of(uri,
            new JavaType(uri, type.sourceFileName, methodMaps.getRight(),
                methodMaps.getLeft(), superClassesURIs, toURIInterfaces(type.superInterfaces),
                type.access, type.isFinal, type.getAnnotations()));
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
    public static Pair<Map<String, JavaNode>, Int2ObjectMap<JavaNode>> getMethodMaps(final Map<Method,
            Integer> methods) {
        final Int2ObjectMap<JavaNode> nodes = new Int2ObjectOpenHashMap<>();
        final Map<String, JavaNode> defs = new HashMap<>();

        for (final var entry : methods.entrySet()) {
            final var method = entry.getKey();
            final var defined = method.instructionsOption().isDefined();
            final var node = new JavaNode(getUri(method), Map.of("first",
                    getFirstLine(method),
                    "last", getLastLine(method),
                    "defined", defined,
                    "access", getAccessModifier(method)));
            if (defined) {
                defs.put(node.getSignature(), node);
            }
            nodes.put(entry.getValue(), node);
        }
        return MutablePair.of(defs, nodes);
    }

    /**
     * Convert a map of {@link DeclaredMethod} to a Map of
     * {@link Node}.
     *
     * @param methods map of methods to convert
     * @return Map of Nodes
     */
    public static Int2ObjectMap<JavaNode> toURIDeclaredMethods(
            final Map<DeclaredMethod, Integer> methods) {
        final Int2ObjectMap<JavaNode> result = new Int2ObjectOpenHashMap<>();

        for (final var entry : methods.entrySet()) {
            final var method = entry.getKey();
            result.put(entry.getValue(), new JavaNode(OPALMethod.toCanonicalSchemelessURI(null,
                    method.declaringClassType(), method.name(),
                    method.descriptor()), new HashMap<>()));
        }
        return result;
    }

    /**
     * Converts a chain of interfaces to Fasten URIs.
     *
     * @param types interfaces
     * @return list of URIs
     */
    public static List<FastenURI> toURIInterfaces(final List<ObjectType> types) {
        final List<FastenURI> result = new ArrayList<>();
        types.forEach(objectType -> result.add(OPALMethod.getTypeURI(objectType)));
        return result;
    }

    /**
     * Converts a chain of classes to Fasten URIs.
     *
     * @param types chain of types
     * @return list of URIs
     */
    public static LinkedList<FastenURI> toURIClasses(final LinkedList<ObjectType> types) {
        final LinkedList<FastenURI> result = new LinkedList<>();
        types.forEach(objectType -> result.add(OPALMethod.getTypeURI(objectType)));
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
    public static Int2ObjectMap<JavaNode> toURIMethods(final Map<Method, Integer> methods) {
        final Int2ObjectMap<JavaNode> result = new Int2ObjectOpenHashMap<>();

        for (final var entry : methods.entrySet()) {
            final var method = entry.getKey();
            result.put(entry.getValue(), new JavaNode(getUri(method),
                    Map.of("first", getFirstLine(method),
                            "last", getLastLine(method),
                            "defined", method.instructionsOption().isDefined(),
                            "access", getAccessModifier(method))));
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

        if (method != null) {
            if (method.body().isDefined()) {
                if (method.body().get().lineNumberTable() != null)
                    if(method.body().get().lineNumberTable().isDefined()) {
                    return method.body().get().lineNumberTable().get().lineNumbers().head()
                        .lineNumber();
                }
            }
        }
        return "notFound";

    }

    /**
     * Get the last line of the method.
     *
     * @param method method
     * @return last line of the method
     */
    private static Object getLastLine(Method method) {
        if (method != null) {
            if (method.body().isDefined()) {
                if (method.body().get().lineNumberTable() != null)
                    if(method.body().get().lineNumberTable().isDefined()) {
                        return method.body().get().lineNumberTable().get().lineNumbers().last()
                            .lineNumber();
                    }
            }
        }
        return "notFound";
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
     * @return A {@link LinkedList} of {@link ObjectType} as super classes of the passed type.
     */
    public static LinkedList<ObjectType> extractSuperClasses(final ClassHierarchy classHierarchy,
                                                             final ObjectType currentClass)
            throws NoSuchElementException {
        var superClassesList = new LinkedList<ObjectType>();
        if (classHierarchy.supertypes(currentClass).nonEmpty()) {
            final var superClasses =
                    classHierarchy.allSuperclassTypesInInitializationOrder(currentClass).s();
            if (superClasses != null) {
                superClasses.reverse().foreach(superClassesList::add);
                return superClassesList;
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
