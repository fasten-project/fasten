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

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import eu.fasten.core.utils.FastenUriUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * For each class in the revision, class hierarchy keeps a {@link JavaType} that is accessible by
 * the {@link FastenURI} of the class as a key.
 *
 * @implNote each method in the revision has a unique id in this CHA.
 */
public class ExtendedRevisionJavaCallGraph extends ExtendedRevisionCallGraph<EnumMap<JavaScope,
    Map<String, JavaType>>> {
    static {
        classHierarchyJSONKey = "cha";
    }


    /**
     * Creates {@link ExtendedRevisionJavaCallGraph} with the given builder.
     *
     * @param builder builder for {@link ExtendedRevisionJavaCallGraph}
     */
    public ExtendedRevisionJavaCallGraph(final ExtendedBuilder<EnumMap<JavaScope, Map<String,
        JavaType>>> builder) {
        super(builder);
    }

    /**
     * Creates {@link ExtendedRevisionJavaCallGraph} with the given data.
     *
     * @param forge          the forge.
     * @param product        the product.
     * @param version        the version.
     * @param timestamp      the timestamp (in seconds from UNIX epoch); optional: if not present,
     *                       it is set to -1.
     * @param nodeCount      number of nodes
     * @param cgGenerator    The name of call graph generator that generated this call graph.
     * @param classHierarchy class hierarchy of this revision including all classes of the revision
     *                       <code> Map<{@link FastenURI}, {@link JavaType}> </code>
     * @param graph          the call graph (no control is done on the graph) {@link Graph}
     */
    public ExtendedRevisionJavaCallGraph(final String forge, final String product, final String version,
                                         final long timestamp, int nodeCount, final String cgGenerator,
                                         final EnumMap<JavaScope,Map<String, JavaType>> classHierarchy,
                                         final Graph graph) {
        super(forge, product, version, timestamp, nodeCount, cgGenerator, classHierarchy, graph);
    }


    /**
     * Creates {@link ExtendedRevisionCallGraph} for the given JSONObject.
     *
     * @param json JSONObject of a revision call graph.
     */
    public ExtendedRevisionJavaCallGraph(final JSONObject json) throws JSONException {
        super(json, ExtendedRevisionJavaCallGraph.class);
        this.graph = new JavaGraph(json.getJSONArray("call-sites"));
    }

    /**
     * Creates builder to build {@link ExtendedRevisionJavaCallGraph}.
     *
     * @return created builder
     */
    public static ExtendedBuilderJava extendedBuilder() {
        return new ExtendedBuilderJava();
    }

    public JavaGraph getGraph() {
        return (JavaGraph) this.graph;
    }

    /**
     * Creates a class hierarchy for the given JSONObject.
     *
     * @param cha JSONObject of a cha.
     */
    public EnumMap<JavaScope, Map<String, JavaType>> getCHAFromJSON(final JSONObject cha) {
        final Map<String, JavaType> internals = new HashMap<>();
        final Map<String, JavaType> externals = new HashMap<>();
        final Map<String, JavaType> resolved = new HashMap<>();

        final var internalTypes = cha.getJSONObject("internalTypes");
        for (final var key : internalTypes.keySet()) {
            internals.put(key,
                new JavaType(key, internalTypes.getJSONObject(key)));
        }
        final var externalTypes = cha.getJSONObject("externalTypes");
        for (final var key : externalTypes.keySet()) {
            externals.put(key, new JavaType(key, externalTypes.getJSONObject(key)));
        }
        final var resolvedTypes = cha.getJSONObject("resolvedTypes");
        for (final var key : resolvedTypes.keySet()) {
            resolved.put(key, new JavaType(key, resolvedTypes.getJSONObject(key)));
        }

        return new EnumMap<>(Map.of(JavaScope.internalTypes, internals,
            JavaScope.externalTypes, externals,
            JavaScope.resolvedTypes, resolved));
    }

    /**
     * Returns the map of all the methods of this object.
     *
     * @return a Map of method ids and their corresponding {@link FastenURI}
     */
    @Override
    public Int2ObjectMap<JavaNode> mapOfAllMethods() {
        Int2ObjectMap<JavaNode> result = new Int2ObjectOpenHashMap<>();
        for (final var aClass : this.getClassHierarchy().get(JavaScope.internalTypes).entrySet()) {
            result.putAll(aClass.getValue().getMethods());
        }
        for (final var aClass : this.getClassHierarchy().get(JavaScope.externalTypes).entrySet()) {
            result.putAll(aClass.getValue().getMethods());
        }
        for (final var aClass : this.getClassHierarchy().get(JavaScope.resolvedTypes).entrySet()) {
            result.putAll(aClass.getValue().getMethods());
        }
        return result;
    }

    /**
     * Returns the BiMap of all resolved methods of this object.
     * Note: external nodes are not considered resolved, since they don't have product and version.
     * Also ids are local to rcg object.
     *
     * @return a BiMap method ids and their corresponding fully qualified {@link FastenURI}
     */
    public BiMap<Integer, String> mapOfFullURIStrings(){
        final BiMap<Integer, String> result = HashBiMap.create();
        for (final var aClass : this.getClassHierarchy().get(JavaScope.internalTypes).entrySet()) {
            putMethodsOfType(result, aClass.getValue().getMethods());
        }
        for (final var aClass : this.getClassHierarchy().get(JavaScope.resolvedTypes).entrySet()) {
            putMethodsOfType(result, aClass.getKey(),
                aClass.getValue().getMethods());
        }
        return result;
    }

    private void putMethodsOfType(final BiMap<Integer, String> result, final String type,
                                  final Map<Integer, JavaNode> methods) {
        for (final var nodeEntry : methods.entrySet()) {
            final var typeUri = FastenURI.create(type);
            final var fullUri = FastenUriUtils.generateFullFastenUri(Constants.mvnForge, typeUri.getProduct(),
                typeUri.getVersion(), nodeEntry.getValue().getUri().toString());
            if (!result.inverse().containsKey(fullUri)) {
                result.put(nodeEntry.getKey(), fullUri);
            }
        }
    }

    private void putMethodsOfType(final BiMap<Integer, String> result, final Map<Integer, JavaNode> methods) {
        for (final var nodeEntry : methods.entrySet()) {
            final var fullUri = FastenUriUtils.generateFullFastenUri(Constants.mvnForge, this.product,
                this.version, nodeEntry.getValue().getUri().toString());
            if (!result.inverse().containsKey(fullUri)) {
                result.put(nodeEntry.getKey(), fullUri);
            }
        }
    }

    public Int2ObjectMap<JavaType> externalNodeIdToTypeMap() {
        final Int2ObjectMap<JavaType> result = new Int2ObjectOpenHashMap<>();
        this.classHierarchy.get(JavaScope.externalTypes).values().parallelStream().forEach(type -> {
            type.getMethods().keySet().forEach(key -> {
                synchronized (result) {
                    result.put(key, type);
                }
            });
        });
        return result;
    }

    public Int2ObjectMap<JavaType> internalNodeIdToTypeMap() {
        final Int2ObjectMap<JavaType> result = new Int2ObjectOpenHashMap<>();
        this.classHierarchy.get(JavaScope.internalTypes).values().parallelStream().forEach(type -> {
            type.getMethods().keySet().forEach(key -> {
                synchronized (result) {
                    result.put(key, type);
                }
            });
        });
        return result;
    }

    public Int2ObjectMap<String> nodeIDtoTypeNameMap() {
        final Int2ObjectMap<String> result = new Int2ObjectOpenHashMap<>();
        for (final var aClass : classHierarchy.get(JavaScope.internalTypes).entrySet()) {
            for (final var nodeEntry : aClass.getValue().getMethods().entrySet()) {
                result.put(nodeEntry.getKey(), aClass.getKey());
            }
        }
        for (final var aClass : classHierarchy.get(JavaScope.externalTypes).entrySet()) {
            for (final var nodeEntry : aClass.getValue().getMethods().entrySet()) {
                result.put(nodeEntry.getKey(), aClass.getKey());
            }
        }
        for (final var aClass : classHierarchy.get(JavaScope.resolvedTypes).entrySet()) {
            for (final var nodeEntry : aClass.getValue().getMethods().entrySet()) {
                result.put(nodeEntry.getKey(), aClass.getKey());
            }
        }
        return result;
    }

    /**
     * Produces the JSON representation of class hierarchy.
     *
     * @param cha class hierarchy
     * @return the JSON representation
     */
    public JSONObject classHierarchyToJSON(final EnumMap<JavaScope, Map<String, JavaType>> cha) {
        final var result = new JSONObject();
        final var internalTypes = new JSONObject();
        final var externalTypes = new JSONObject();
        final var resolvedTypes = new JSONObject();

        for (final var entry : cha.get(JavaScope.internalTypes).entrySet()) {
            internalTypes.put(entry.getKey(), entry.getValue().toJSON());
        }
        for (final var entry : cha.get(JavaScope.externalTypes).entrySet()) {
            externalTypes.put(entry.getKey(), entry.getValue().toJSON());
        }
        for (final var entry : cha.get(JavaScope.resolvedTypes).entrySet()) {
            resolvedTypes.put(entry.getKey(), entry.getValue().toJSON());
        }
        result.put("internalTypes", internalTypes);
        result.put("externalTypes", externalTypes);
        result.put("resolvedTypes", resolvedTypes);

        return result;
    }

    /**
     * Returns a string representation of the revision.
     *
     * @return String representation of the revision.
     */
    public String getRevisionName() {
        final String groupId = this.product.split(Constants.mvnCoordinateSeparator)[0];
        final String artifactId = this.product.split(Constants.mvnCoordinateSeparator)[1];
        return artifactId + "_" + groupId + "_" + this.version;
    }

    public JSONObject toJSON() {
        final var result = new JSONObject();
        result.put("forge", forge);
        result.put("product", product);
        result.put("version", version);
        result.put("generator", cgGenerator);
        if (timestamp >= 0) {
            result.put("timestamp", timestamp);
        }
        result.put(classHierarchyJSONKey, classHierarchyToJSON(classHierarchy));
        result.put("call-sites", graph.toJSON());
        result.put("nodes", nodeCount);

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ExtendedRevisionCallGraph<?> that = (ExtendedRevisionCallGraph<?>) o;

        if (nodeCount != that.nodeCount) {
            return false;
        }
        if (timestamp != that.timestamp) {
            return false;
        }
        if (classHierarchy != null ? !classHierarchy.equals(that.classHierarchy) :
            that.classHierarchy != null) {
            return false;
        }
        if (graph != null ? !graph.equals(that.graph) : that.graph != null) {
            return false;
        }
        if (forge != null ? !forge.equals(that.forge) : that.forge != null) {
            return false;
        }
        if (product != null ? !product.equals(that.product) : that.product != null) {
            return false;
        }
        if (version != null ? !version.equals(that.version) : that.version != null) {
            return false;
        }
        if (uri != null ? !uri.equals(that.uri) : that.uri != null) {
            return false;
        }
        if (forgelessUri != null ? !forgelessUri.equals(that.forgelessUri) :
            that.forgelessUri != null) {
            return false;
        }
        return cgGenerator != null ? cgGenerator.equals(that.cgGenerator) :
            that.cgGenerator == null;
    }

    @Override
    public int hashCode() {
        int result = classHierarchy != null ? classHierarchy.hashCode() : 0;
        result = 31 * result + nodeCount;
        result = 31 * result + (graph != null ? graph.hashCode() : 0);
        result = 31 * result + (forge != null ? forge.hashCode() : 0);
        result = 31 * result + (product != null ? product.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
        result = 31 * result + (uri != null ? uri.hashCode() : 0);
        result = 31 * result + (forgelessUri != null ? forgelessUri.hashCode() : 0);
        result = 31 * result + (cgGenerator != null ? cgGenerator.hashCode() : 0);
        return result;
    }

    /**
     * Converts an {@link ExtendedRevisionJavaCallGraph} into a {@link DirectedGraph} using as global
     * identifiers the local identifiers.
     *
     * @param erjcg an {@link ExtendedRevisionJavaCallGraph}.
     * @return a directed graph based on the local identifiers of {@code erjcg}.
     */
    public static DirectedGraph toLocalDirectedGraph(final ExtendedRevisionJavaCallGraph erjcg) {
        MergedDirectedGraph dg = new MergedDirectedGraph();
        erjcg.getClassHierarchy().get(JavaScope.internalTypes).forEach((key, value) -> value.getMethods().keySet().forEach(dg::addInternalNode));
        erjcg.getClassHierarchy().get(JavaScope.resolvedTypes).forEach((key, value) -> value.getMethods().keySet().forEach(dg::addInternalNode));

        return dg;
    }
}
