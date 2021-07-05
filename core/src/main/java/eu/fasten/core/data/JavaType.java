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

import java.util.*;

import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONObject;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * Each type is a class or an interface.
 */
public class JavaType {

    /**
     * The FASTEN URI of this Java type.
     */
    private final String uri;

    /**
     * The source file name of this type.
     */
    private final String sourceFileName;

    /**
     * Methods of this type and their unique ids (unique within the same artifact).
     */
    private final Int2ObjectMap<JavaNode> methods;
    private final Object2IntMap<JavaNode> javaNodes;

    /**
     * Classes that this type inherits from in the order of instantiation.
     */
    private final LinkedList<FastenURI> superClasses;
    private final Map<String, JavaNode> definedMethods;

    public Map<String, JavaNode> getDefinedMethods() {
        return definedMethods;
    }

    /**
     * Interfaces that this type or its super classes implement.
     */
    private final List<FastenURI> superInterfaces;

    /**
     * Access modifier of this JavaType.
     */
    private final String access;

    /**
     * Flag indicating if this JavaType is final.
     */
    private final boolean isFinal;

    private final Map<String, List<Pair<String, String>>> annotations;

    /**
     * Creates {@link JavaType} for the given data.
     *
     * @param sourceFile      the name of this type's source file
     * @param methods         a map of methods in this type with their indexed by their ids.
     * @param defineds        a map of all defined methods of a type to their signature.
     * @param superClasses    classes that this type extends.
     * @param superInterfaces interfaces that this type implements.
     * @param access          access modifier
     * @param isFinal         true if the Type is final
     */
    public JavaType(final String uri, final String sourceFile, final Int2ObjectMap<JavaNode> methods,
                    final Map<String, JavaNode> defineds,
                    final LinkedList<FastenURI> superClasses,
                    final List<FastenURI> superInterfaces, final String access,
                    final boolean isFinal, Map<String, List<Pair<String, String>>> annotations) {
        this.uri = uri;
        this.sourceFileName = sourceFile;
        this.methods = methods;
        this.javaNodes = new Object2IntOpenHashMap<>();
        methods.forEach((x, y) -> javaNodes.put(y, x));
        ;
        javaNodes.defaultReturnValue(-1);
        this.definedMethods = defineds;
        this.superClasses = superClasses;
        this.superInterfaces = superInterfaces;
        this.access = access;
        this.isFinal = isFinal;
        this.annotations = annotations;
    }

    /**
     * Creates {@link JavaType} for the given JSONObject.
     *
     * @param type JSONObject of a type including its source file name, map of methods, super
     *             classes and super interfaces.
     */
    public JavaType(final String uri, final JSONObject type) {
        this.uri = uri;
        this.sourceFileName = type.getString("sourceFile");

        final var methodsJson = type.getJSONObject("methods");
        this.methods = new Int2ObjectOpenHashMap<>();
        this.javaNodes = new Object2IntOpenHashMap<>();
        javaNodes.defaultReturnValue(-1);
        this.definedMethods = new HashMap<>();
        for (final var methodKey : methodsJson.keySet()) {
            final var nodeJson = methodsJson.getJSONObject(methodKey);

            final var metadata = nodeJson.getJSONObject("metadata");
            final var node = new JavaNode(FastenURI.create(nodeJson.getString("uri")), metadata.toMap());
            final int k = Integer.parseInt(methodKey);
            this.methods.put(k, node);
            this.javaNodes.put(node, k);
            if (!metadata.isEmpty()) {
                if (metadata.getBoolean("defined")) {
                    definedMethods.put(node.getSignature(), node);
                }
            }
        }

        final var superClassesJSON = type.getJSONArray("superClasses");
        this.superClasses = new LinkedList<>();
        final int numberOfSuperClasses = superClassesJSON.length();
        for (int i = 0; i < numberOfSuperClasses; i++) {
            this.superClasses.add(FastenURI.create(superClassesJSON.getString(i)));
        }

        final var superInterfacesJSON = type.getJSONArray("superInterfaces");
        this.superInterfaces = new ArrayList<>();
        final int numberOfSuperInterfaces = superInterfacesJSON.length();
        for (int i = 0; i < numberOfSuperInterfaces; i++) {
            this.superInterfaces.add(FastenURI.create(superInterfacesJSON.getString(i)));
        }
        this.access = type.getString("access");
        this.isFinal = type.getBoolean("final");

        if (type.has("annotations")) {
            var annotations = new HashMap<String, List<Pair<String, String>>>();
            var annotationsJson = type.getJSONObject("annotations");
            for (var annotation : annotationsJson.keySet()) {
                var valueList = new ArrayList<Pair<String, String>>();
                var valueArray = annotationsJson.optJSONArray(annotation);
                for (int i = 0; i < valueArray.length(); i++) {
                    var value = valueArray.getJSONArray(i);
                    valueList.add(Pair.of(value.getString(0), value.getString(1)));
                }
                annotations.put(annotation, valueList);
            }
            this.annotations = annotations;
        } else {
            this.annotations = new HashMap<>();
        }
    }

    public String getUri() {
        return uri;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public Int2ObjectMap<JavaNode> getMethods() {
        return this.methods;
    }

    public LinkedList<FastenURI> getSuperClasses() {
        return superClasses;
    }

    public List<FastenURI> getSuperInterfaces() {
        return superInterfaces;
    }

    public String getAccess() {
        return access;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public Map<String, List<Pair<String, String>>> getAnnotations() {
        return annotations;
    }

    /**
     * Returns the integer associated to the given JavaNode.
     *
     * @param node a {@link JavaNode}.
     * @return the associated integer key, or &minus;1 if {@code node} is not in the map.
     */
    public int getMethodKey(final JavaNode node) {
        return javaNodes.getInt(node);
    }

    /**
     * Puts a JavaNode to the list of methods of this {@link JavaType}.
     *
     * @param node new node to add
     * @param key  the key corresponding to this JavaNode
     */
    public void addMethod(final JavaNode node, final int key) {
        methods.put(key, node);
        javaNodes.put(node, key);
    }

    /**
     * Converts all the values of a given Map to String.
     *
     * @param map map of id-s and corresponding JavaNodes
     */
    public static Int2ObjectMap<JSONObject> toMapOfString(final Int2ObjectMap<JavaNode> map) {
        final Int2ObjectMap<JSONObject> methods = new Int2ObjectOpenHashMap<>();
        for (final var entry : map.entrySet()) {
            final JSONObject node = new JSONObject();
            node.put("uri", entry.getValue().getUri());
            node.put("metadata", new JSONObject(entry.getValue().getMetadata()));
            methods.put(entry.getKey(), node);
        }
        return methods;
    }

    /**
     * Converts elements of a given list to String.
     *
     * @param list a list of elements to be converted
     */
    public static List<String> toListOfString(final List<?> list) {
        final List<String> result = new ArrayList<>();
        for (final var fastenURI : list) {
            result.add(fastenURI.toString());
        }
        return result;
    }

    /**
     * Get all defined methods.
     *
     * @param signature method signature
     * @return optional map of all defined methods
     */
    public Optional<Map.Entry<Integer, JavaNode>> getDefined(String signature) {
        return methods.entrySet()
                .stream()
                .filter(node -> (Boolean) node.getValue().metadata.get("defined"))
                .filter(node -> node.getValue().getSignature().equals(signature))
                .findAny();
    }

    /**
     * Converts this {@link JavaType} object to its JSON representation.
     *
     * @return the corresponding JSON representation.
     */
    public JSONObject toJSON() {
        final var result = new JSONObject();

        result.put("methods", toMapOfString(this.methods));
        result.put("superClasses", toListOfString(this.superClasses));
        result.put("superInterfaces", toListOfString(this.superInterfaces));
        result.put("sourceFile", this.sourceFileName);
        result.put("access", this.access);
        result.put("final", this.isFinal);

        var annotationsJson = new JSONObject();
        for (var annotationEntry : this.annotations.entrySet()) {
            var jArray = new JSONArray();
            for (var annotationValue : annotationEntry.getValue()) {
                var jValue = new JSONArray();
                jValue.put(annotationValue.getLeft());
                jValue.put(annotationValue.getRight());
                jArray.put(jValue);
            }
            annotationsJson.put(annotationEntry.getKey(), jArray);
        }
        result.put("annotations", annotationsJson);

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JavaType javaType = (JavaType) o;

        if (isFinal != javaType.isFinal) return false;
        if (!Objects.equals(uri, javaType.uri)) return false;
        if (!Objects.equals(sourceFileName, javaType.sourceFileName))
            return false;
        if (!Objects.equals(methods, javaType.methods))
            return false;
        if (!Objects.equals(javaNodes, javaType.javaNodes))
            return false;
        if (!Objects.equals(superClasses, javaType.superClasses))
            return false;
        if (!Objects.equals(definedMethods, javaType.definedMethods))
            return false;
        if (!Objects.equals(superInterfaces, javaType.superInterfaces))
            return false;
        if (!Objects.equals(access, javaType.access))
            return false;
        return Objects.equals(annotations, javaType.annotations);
    }

    @Override
    public int hashCode() {
        int result = uri != null ? uri.hashCode() : 0;
        result = 31 * result + (sourceFileName != null ? sourceFileName.hashCode() : 0);
        result = 31 * result + (methods != null ? methods.hashCode() : 0);
        result = 31 * result + (javaNodes != null ? javaNodes.hashCode() : 0);
        result = 31 * result + (superClasses != null ? superClasses.hashCode() : 0);
        result = 31 * result + (definedMethods != null ? definedMethods.hashCode() : 0);
        result = 31 * result + (superInterfaces != null ? superInterfaces.hashCode() : 0);
        result = 31 * result + (access != null ? access.hashCode() : 0);
        result = 31 * result + (isFinal ? 1 : 0);
        result = 31 * result + (annotations != null ? annotations.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "JavaType{" +
                "uri='" + uri + '\'' +
                ", sourceFileName='" + sourceFileName + '\'' +
                ", methods=" + methods +
                ", javaNodes=" + javaNodes +
                ", superClasses=" + superClasses +
                ", definedMethods=" + definedMethods +
                ", superInterfaces=" + superInterfaces +
                ", access='" + access + '\'' +
                ", isFinal=" + isFinal +
                ", annotations=" + annotations +
                '}';
    }
}
