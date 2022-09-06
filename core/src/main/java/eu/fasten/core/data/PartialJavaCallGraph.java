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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
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
public class PartialJavaCallGraph extends PartialCallGraph {

    public static final String classHierarchyJSONKey = "cha";

    public void setClassHierarchy(
        final EnumMap<JavaScope, Map<String, JavaType>> classHierarchy) {
        this.classHierarchy = classHierarchy;
    }

    public void setGraph(final JavaGraph graph) {
        this.graph = graph;
    }

    protected EnumMap<JavaScope, Map<String, JavaType>> classHierarchy;

    /**
     * Includes all the edges of the revision call graph (internal, external,
     * and resolved).
     */
    protected JavaGraph graph;

    /**
     * Creates {@link PartialJavaCallGraph} with the given data.
     *
     * @param forge          the forge.
     * @param product        the product.
     * @param version        the version.
     * @param timestamp      the timestamp (in seconds from UNIX epoch); optional: if not present,
     *                       it is set to -1.
     * @param cgGenerator    The name of call graph generator that generated this call graph.
     * @param classHierarchy class hierarchy of this revision including all classes of the revision
     *                       <code> Map<{@link FastenURI}, {@link JavaType}> </code>
     * @param graph          the call graph (no control is done on the graph) {@link CPythonGraph}
     */
    public PartialJavaCallGraph(final String forge, final String product, final String version,
                                final long timestamp, final String cgGenerator,
                                final EnumMap<JavaScope,Map<String, JavaType>> classHierarchy,
                                final JavaGraph graph) {
        super(forge, product, version, timestamp, cgGenerator);
        this.classHierarchy = classHierarchy;
        this.graph = graph;
    }


    /**
     * Creates {@link PartialCallGraph} for the given JSONObject.
     *
     * @param json JSONObject of a revision call graph.
     */
    public PartialJavaCallGraph(final JSONObject json) throws JSONException {
        super(json);
        this.graph = new JavaGraph(json.getJSONArray("call-sites"));
        this.classHierarchy = getCHAFromJSON(json.getJSONObject(classHierarchyJSONKey));
    }

    @Override
    public JavaGraph getGraph() {
        return this.graph;
    }

    public Map<it.unimi.dsi.fastutil.ints.IntIntPair, Map<Object, Object>> getCallSites(){
        return this.getGraph().getCallSites();
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

    @Override
    public int getNodeCount() {
        return this.mapOfAllMethods().size();
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
        final var result = super.toJSON();
        result.put(classHierarchyJSONKey, classHierarchyToJSON(classHierarchy));
        result.put("call-sites", graph.toJSON());

        return result;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PartialJavaCallGraph &&
            EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    public EnumMap<JavaScope, Map<String, JavaType>> getClassHierarchy() {
    return classHierarchy;
    }

    /**
     * Checks whether this {@link PartialCallGraph} is empty, e.g. has no calls.
     *
     * @return true if this {@link PartialCallGraph} is empty
     */
    public boolean isCallGraphEmpty() {
        return graph.getCallSites().isEmpty();
    }

}
