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

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.EnumMap;

import org.json.JSONObject;
import org.json.JSONException;

/**
 * We use the key function in the JSON to get the information we want to save
 * in classHierarchy.
 *
 * The data structure contains a map with CScopes to a map of functions'
 * URIs to a map of NodeIds to CNodes.
 */
public class ExtendedRevisionCCallGraph extends ExtendedRevisionCallGraph<EnumMap<CScope, Map<String, Map<Integer, CNode>>>> {
    static {
        classHierarchyJSONKey = "functions";
    }
    public String architecture;

    /**
     * Creates {@link ExtendedRevisionCCallGraph} with the given builder.
     *
     * @param builder builder for {@link ExtendedRevisionCCallGraph}
     */
    public ExtendedRevisionCCallGraph(final ExtendedBuilder<EnumMap<CScope, Map<String, Map<Integer, CNode>>>> builder) {
        super(builder);
        ExtendedBuilderC cBuilder = (ExtendedBuilderC) builder;
        this.architecture = cBuilder.getArchitecture();
    }

    /**
     * Creates {@link ExtendedRevisionCCallGraph} with the given data.
     *
     * @param forge          the forge.
     * @param product        the product.
     * @param version        the version.
     * @param timestamp      the timestamp (in seconds from UNIX epoch); optional: if not present,
     *                       it is set to -1.
     * @param nodeCount      number of nodes
     * @param cgGenerator    The name of call graph generator that generated this call graph.
     * @param classHierarchy class hierarchy of this revision including all classes of the revision
     *                       <code> Map<{@link FastenURI}, {@link CallType}> </code>
     * @param graph          the call graph (no control is done on the graph) {@link Graph}
     */
    public ExtendedRevisionCCallGraph(final String forge, final String product, final String version,
                                     final long timestamp, int nodeCount, final String cgGenerator,
                                     final EnumMap<CScope, Map<String, Map<Integer, CNode>>>classHierarchy,
                                     final Graph graph) {
        super(forge, product, version, timestamp, nodeCount, cgGenerator, classHierarchy, graph);
    }

    /**
     * Creates {@link ExtendedRevisionCCallGraph} with the given data.
     *
     * @param forge          the forge.
     * @param product        the product.
     * @param version        the version.
     * @param timestamp      the timestamp (in seconds from UNIX epoch); optional: if not present,
     *                       it is set to -1.
     * @param nodeCount      number of nodes
     * @param cgGenerator    The name of call graph generator that generated this call graph.
     * @param classHierarchy class hierarchy of this revision including all classes of the revision
     *                       <code> Map<{@link FastenURI}, {@link CallType}> </code>
     * @param graph          the call graph (no control is done on the graph) {@link Graph}
     * @param architecture   the architecture.
     */
    public ExtendedRevisionCCallGraph(final String forge, final String product, final String version,
                                     final long timestamp, int nodeCount, final String cgGenerator,
                                     final EnumMap<CScope, Map<String, Map<Integer, CNode>>>classHierarchy,
                                     final Graph graph, final String architecture) {
        super(forge, product, version, timestamp, nodeCount, cgGenerator, classHierarchy, graph);
        this.architecture = architecture;
    }

    /**
     * Creates {@link ExtendedRevisionCallGraph} for the given JSONObject.
     *
     * @param json JSONObject of a revision call graph.
     */
    public ExtendedRevisionCCallGraph(final JSONObject json) throws JSONException {
        super(json, ExtendedRevisionCCallGraph.class);
        this.architecture = json.has("architecture") ? json.getString("architecture") : null;
    }

    /**
     * Creates builder to build {@link ExtendedRevisionCCallGraph}.
     *
     * @return created builder
     */
    public static ExtendedBuilderC extendedBuilder() {
        return new ExtendedBuilderC();
    }

    /**
     * Helper method to parse methods.
     *
     * @param json JSONObject that contains methods.
     */
    public static Map<Integer, CNode> parseMethods(final JSONObject json) {
        final Map<Integer, CNode> methods = new HashMap<>();
        for (final var methodId : json.keySet()) {
            final var method = json.getJSONObject(methodId);
            final var uri = FastenURI.create(method.getString("uri"));
            final var metadata = method.getJSONObject("metadata").toMap();
            // Convert JSONArray to List<String>
            List<String> files = new ArrayList<String>();
            for(int i=0; i < method.getJSONArray("files").length(); i++)
                files.add(method.getJSONArray("files").getString(i));
            final var node = new CNode(uri, metadata, files);
            methods.put(Integer.parseInt(methodId), node);
        }
        return methods;
    }

    /**
     * Helper method to parse methods.
     *
     * @param json JSONObject that contains methods.
     * @param complex boolean json has one more level.
     */
    public static Map<String, Map<Integer, CNode>> parseMethods(final JSONObject json, final boolean complex) {
        final Map<String, Map<Integer, CNode>> methods = new HashMap<>();
        if (complex) {
            // El could be binary or product.
            for (final var el : json.keySet()) {
                methods.put(el, parseMethods(json.getJSONObject(el).getJSONObject("methods")));
            }
        } else {
            methods.put("", parseMethods(json));
        }
        return methods;
    }

    /**
     * Creates a class hierarchy for the given JSONObject.
     *
     * @param cha JSONObject of a cha.
     */
    public EnumMap<CScope, Map<String, Map<Integer, CNode>>> getCHAFromJSON(final JSONObject cha) {
        final HashMap<CScope, Map<String, Map<Integer, CNode>>> methods = new HashMap<>();

        final var internal = cha.getJSONObject("internal");
        final var external = cha.getJSONObject("external");
        // Parse internal binaries
        final var internalBinaries = internal.getJSONObject("binaries");
        methods.put(CScope.internalBinary, parseMethods(internalBinaries, true));
        // Parse internal static functions
        final var internalStatic = internal.getJSONObject("static_functions").getJSONObject("methods");
        methods.put(CScope.internalStaticFunction, parseMethods(internalStatic, false));
        // Parse external product functions
        final var externalProducts = external.getJSONObject("products");
        methods.put(CScope.externalProduct, parseMethods(externalProducts, true));
        // Parse external static functions
        final var externalStatic = external.getJSONObject("static_functions");
        methods.put(CScope.externalStaticFunction, parseMethods(externalStatic, true));
        // Parse external undefined functions
        final var externalUndefined = external.getJSONObject("undefined").getJSONObject("methods");
        methods.put(CScope.externalUndefined, parseMethods(externalUndefined, false));
        return new EnumMap<>(methods);
    }

    /**
     * Returns the map of all the methods of this object.
     *
     * @return a Map of method ids and their corresponding {@link FastenURI}
     */
    @Override
    public Map<Integer, CNode> mapOfAllMethods() {
        Map<Integer, CNode> result = new HashMap<>();

        for (final var name : this.getClassHierarchy().get(CScope.internalBinary).entrySet())
            for (final var method : name.getValue().entrySet())
                result.put(method.getKey(), method.getValue());

        for (final var name : this.getClassHierarchy().get(CScope.internalStaticFunction).entrySet())
            for (final var method : name.getValue().entrySet())
                result.put(method.getKey(), method.getValue());

        for (final var name : this.getClassHierarchy().get(CScope.externalProduct).entrySet())
            for (final var method : name.getValue().entrySet())
                result.put(method.getKey(), method.getValue());

        for (final var name : this.getClassHierarchy().get(CScope.externalUndefined).entrySet())
            for (final var method : name.getValue().entrySet())
                result.put(method.getKey(), method.getValue());

        for (final var name : this.getClassHierarchy().get(CScope.externalStaticFunction).entrySet())
            for (final var method : name.getValue().entrySet())
                result.put(method.getKey(), method.getValue());

        return result;
    }

    /**
     * Produces the JSON of methods
     *
     * @param CScope of the cha
     */
    public static JSONObject methodsToJSON(final Map<CScope, Map<String, Map<Integer, CNode>>> cha, CScope scope) {
        final var result = new JSONObject();
        final var methods = new JSONObject();
        for (final var entry : cha.get(scope).get("").entrySet())
            methods.put(entry.getKey().toString(), entry.getValue().toJSON());
        result.put("methods", methods);
        return result;
    }

    /**
     * Produces the JSON of methods
     *
     * @param scope of the cha
     * @param complex boolean value to handle complex scopes
     */
    public static JSONObject methodsToJSON(final Map<CScope, Map<String, Map<Integer, CNode>>> cha, CScope scope, boolean complex) {
        if (complex) {
            final var result = new JSONObject();
            for (final var element : cha.get(scope).entrySet()) {
                var intermediate = new JSONObject();
                for (final var entry : element.getValue().entrySet()) {
                    intermediate.put(entry.getKey().toString(), entry.getValue().toJSON());
                }
                var methods = new JSONObject();
                methods.put("methods", intermediate);
                result.put(element.getKey().toString(), methods);
            }
            return result;
        } else {
            return methodsToJSON(cha, scope);
        }
    }

    /**
     * Produces the JSON representation of class hierarchy.
     *
     * @param cha class hierarchy
     * @return the JSON representation
     */
    public JSONObject classHierarchyToJSON(final EnumMap<CScope, Map<String, Map<Integer, CNode>>> cha) {
        final var result = new JSONObject();
        final var internal = new JSONObject();
        final var external = new JSONObject();
        final var internalBinaries = methodsToJSON(cha, CScope.internalBinary, true);
        final var internalStaticFunctions = methodsToJSON(cha, CScope.internalStaticFunction);
        final var externalProducts = methodsToJSON(cha, CScope.externalProduct, true);
        final var externalStraticFunctions = methodsToJSON(cha, CScope.externalStaticFunction, true);
        final var externalUndefined = methodsToJSON(cha, CScope.externalUndefined);

        internal.put("binaries", internalBinaries);
        internal.put("static_functions", internalStaticFunctions);
        external.put("products", externalProducts);
        external.put("undefined", externalUndefined);
        external.put("static_functions", externalStraticFunctions);
        result.put("internal", internal);
        result.put("external", external);

        return result;
    }

    /**
     * Returns a string representation of the revision.
     *
     * @return String representation of the revision.
     */
    public String getRevisionName() {
        return this.product + "_" + this.architecture + "_" + this.version;
    }
}
