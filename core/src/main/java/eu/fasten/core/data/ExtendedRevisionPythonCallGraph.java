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

//Map<PythonScope, Map<String, PythonType>>
public class ExtendedRevisionPythonCallGraph extends ExtendedRevisionCallGraph<EnumMap<PythonScope, Map<String, PythonType>>> {
    static {
        classHierarchyJSONKey = "modules";
    }

    /**
     * Creates {@link ExtendedRevisionPythonCallGraph} with the given builder.
     *
     * @param builder builder for {@link ExtendedRevisionCCallGraph}
     */
    public ExtendedRevisionPythonCallGraph(final ExtendedBuilder<EnumMap<PythonScope, Map<String, PythonType>>> builder) {
        super(builder);
    }

    /**
     * Creates {@link ExtendedRevisionPythonCallGraph} with the given data.
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
    public ExtendedRevisionPythonCallGraph(final String forge, final String product, final String version,
                                     final long timestamp, int nodeCount, final String cgGenerator,
                                     final EnumMap<PythonScope, Map<String, PythonType>>classHierarchy,
                                     final Graph graph) {
        super(forge, product, version, timestamp, nodeCount, cgGenerator, classHierarchy, graph);
    }

    /**
     * Creates {@link ExtendedRevisionCallGraph} for the given JSONObject.
     *
     * @param json JSONObject of a revision call graph.
     */
    public ExtendedRevisionPythonCallGraph(final JSONObject json) throws JSONException {
        super(json, ExtendedRevisionPythonCallGraph.class);
    }

    /**
     * Creates builder to build {@link ExtendedRevisionCCallGraph}.
     *
     * @return created builder
     */
    public static ExtendedBuilderPython extendedBuilder() {
        return new ExtendedBuilderPython();
    }

    /**
     * Creates a class hierarchy for the given JSONObject.
     *
     * @param cha JSONObject of a cha.
     */
    public EnumMap<PythonScope, Map<String, PythonType>> getCHAFromJSON(final JSONObject cha) {
        final Map<PythonScope, Map<String, PythonType>> methods = new HashMap<>();

        final var internal = cha.getJSONObject("internal");
        final var external = cha.getJSONObject("external");

        methods.put(PythonScope.internal, parseModules(internal));
        methods.put(PythonScope.external, parseModules(external));

        return new EnumMap<>(methods);
    }

    /**
     * Helper method to parse modules.
     *
     * @param json JSONObject that contains methods.
     */
    public static Map<String, PythonType> parseModules(final JSONObject json) {
        final Map<String, PythonType> modules = new HashMap<>();

        for (final var module : json.keySet()) {
            final var typeJson = json.getJSONObject(module);
            final var type = new PythonType(typeJson);
            modules.put(module, type);
        }
        return modules;
    }

    /**
     * Returns the map of all the methods of this object.
     *
     * @return a Map of method ids and their corresponding {@link FastenURI}
     */
    @Override
    public Map<Integer, PythonNode> mapOfAllMethods() {
        Map<Integer, PythonNode> result = new HashMap<>();
        for (final var aClass : this.getClassHierarchy().get(PythonScope.internal).entrySet()) {
            result.putAll(aClass.getValue().getMethods());
        }
        for (final var aClass : this.getClassHierarchy().get(PythonScope.external).entrySet()) {
            result.putAll(aClass.getValue().getMethods());
        }
        return result;
    }

    /**
     * Produces the JSON representation of class hierarchy.
     *
     * @param cha class hierarchy
     * @return the JSON representation
     */
    public JSONObject classHierarchyToJSON(final EnumMap<PythonScope, Map<String, PythonType>> cha) {
        final var result = new JSONObject();

        final var internal = methodsToJSON(cha.get(PythonScope.internal));
        final var external = methodsToJSON(cha.get(PythonScope.external));

        result.put("internal", internal);
        result.put("external", external);

        return result;
    }

    /**
     * Produces the JSON of methods
     *
     * @param types the python types
     */
    public static JSONObject methodsToJSON(final Map<String, PythonType> types) {
        final var result = new JSONObject();
        for (final var type : types.entrySet()) {
            result.put(type.getKey(), type.getValue().toJSON());
        }
        return result;
    }

    /**
     * Returns a string representation of the revision.
     *
     * @return String representation of the revision.
     */
    public String getRevisionName() {
        return this.product + "_" + this.version;
    }
}
