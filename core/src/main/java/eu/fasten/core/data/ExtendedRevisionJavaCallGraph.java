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
import org.json.JSONObject;
import org.json.JSONException;

public class ExtendedRevisionJavaCallGraph extends ExtendedRevisionCallGraph<Map<JavaScope, Map<FastenURI, JavaType>>> {

    /**
     * Creates {@link ExtendedRevisionJavaCallGraph} with the given builder.
     *
     * @param builder builder for {@link ExtendedRevisionJavaCallGraph}
     */
    public ExtendedRevisionJavaCallGraph(final ExtendedBuilder<Map<JavaScope, Map<FastenURI, JavaType>>> builder) {
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
     *                       <code> Map<{@link FastenURI}, {@link Type}> </code>
     * @param graph          the call graph (no control is done on the graph) {@link Graph}
     */
    public ExtendedRevisionJavaCallGraph(final String forge, final String product, final String version,
                                     final long timestamp, int nodeCount, final String cgGenerator,
                                     final Map<JavaScope, Map<FastenURI, JavaType>> classHierarchy,
                                     final Graph graph) {
        super(forge, product, version, timestamp, nodeCount, cgGenerator, classHierarchy, graph);
    }

    /**
     * Creates {@link ExtendedRevisionCallGraph} for the given JSONObject.
     *
     * @param json JSONObject of a revision call graph.
     */
    public ExtendedRevisionJavaCallGraph(final JSONObject json) throws JSONException {
        super(json);
    }

    /**
     * Creates builder to build {@link ExtendedRevisionJavaCallGraph}.
     *
     * @return created builder
     */
    public static ExtendedBuilderJava extendedBuilder() {
        return new ExtendedBuilderJava();
    }

    /**
     * Creates a class hierarchy for the given JSONObject.
     *
     * @param cha JSONObject of a cha.
     */
    public Map<JavaScope, Map<FastenURI, JavaType>> getCHAFromJSON(final JSONObject cha) {
        final Map<FastenURI, JavaType> internals = new HashMap<>();
        final Map<FastenURI, JavaType> externals = new HashMap<>();
        final Map<FastenURI, JavaType> resolved = new HashMap<>();

        final var internalTypes = cha.getJSONObject("internalTypes");
        for (final var key : internalTypes.keySet()) {
            internals.put(FastenURI.create(key), new JavaType(internalTypes.getJSONObject(key)));
        }
        final var externalTypes = cha.getJSONObject("externalTypes");
        for (final var key : externalTypes.keySet()) {
            externals.put(FastenURI.create(key), new JavaType(externalTypes.getJSONObject(key)));
        }
        final var resolvedTypes = cha.getJSONObject("resolvedTypes");
        for (final var key : resolvedTypes.keySet()) {
            resolved.put(FastenURI.create(key), new JavaType(resolvedTypes.getJSONObject(key)));
        }

        return Map.of(JavaScope.internalTypes, internals,
                JavaScope.externalTypes, externals,
                JavaScope.resolvedTypes, resolved);
    }

    /**
     * Returns the map of all the methods of this object.
     *
     * @return a Map of method ids and their corresponding {@link FastenURI}
     */
    @Override
    public Map<Integer, JavaNode> mapOfAllMethods() {
        Map<Integer, JavaNode> result = new HashMap<>();
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
     * Produces the JSON representation of class hierarchy.
     *
     * @param cha class hierarchy
     * @return the JSON representation
     */
    public JSONObject classHierarchyToJSON(final Map<JavaScope, Map<FastenURI, JavaType>> cha) {
        final var result = new JSONObject();
        final var internalTypes = new JSONObject();
        final var externalTypes = new JSONObject();
        final var resolvedTypes = new JSONObject();

        for (final var entry : cha.get(JavaScope.internalTypes).entrySet()) {
            internalTypes.put(entry.getKey().toString(), entry.getValue().toJSON());
        }
        for (final var entry : cha.get(JavaScope.externalTypes).entrySet()) {
            externalTypes.put(entry.getKey().toString(), entry.getValue().toJSON());
        }
        for (final var entry : cha.get(JavaScope.resolvedTypes).entrySet()) {
            resolvedTypes.put(entry.getKey().toString(), entry.getValue().toJSON());
        }
        result.put("internalTypes", internalTypes);
        result.put("externalTypes", externalTypes);
        result.put("resolvedTypes", resolvedTypes);

        return result;
    }

}
