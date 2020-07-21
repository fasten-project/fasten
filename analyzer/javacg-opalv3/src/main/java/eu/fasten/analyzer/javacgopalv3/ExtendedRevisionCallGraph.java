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

package eu.fasten.analyzer.javacgopalv3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import eu.fasten.analyzer.javacgopalv3.data.analysis.OPALCallSite;
import eu.fasten.core.data.FastenURI;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtendedRevisionCallGraph {

    private static final Logger logger = LoggerFactory.getLogger(ExtendedRevisionCallGraph.class);

    /**
     * For each class in the revision, class hierarchy keeps a {@link Type} that is accessible by
     * the {@link FastenURI} of the class as a key.
     *
     * @implNote each method in the revision has a unique id in this CHA.
     */
    private final Map<Scope, Map<FastenURI, Type>> classHierarchy;

    private final int nodeCount;

    public enum Scope {
        internalTypes,
        externalTypes,
        resolvedTypes
    }

    public int getNodeCount() {
        return nodeCount;
    }

    /**
     * Includes all the edges of the revision call graph (internal & external).
     */
    private final Graph graph;


    /**
     * The forge.
     */
    public final String forge;
    /**
     * The product.
     */
    public final String product;
    /**
     * The version.
     */
    public final String version;
    /**
     * The timestamp (if specified, or -1) in seconds from UNIX Epoch.
     */
    public final long timestamp;
    /**
     * The URI of this revision.
     */
    public final FastenURI uri;
    /**
     * The forgeless URI of this revision.
     */
    public final FastenURI forgelessUri;
    /**
     * Keeps the name of call graph generator that generated this revision call graph.
     */
    private final String cgGenerator;

    /**
     * Creates {@link ExtendedRevisionCallGraph} with the given data.
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
    public ExtendedRevisionCallGraph(final String forge, final String product, final String version,
                                     final long timestamp, int nodeCount, final String cgGenerator,
                                     final Map<Scope, Map<FastenURI, Type>> classHierarchy,
                                     final Graph graph) {

        this.forge = forge;
        this.product = product;
        this.version = version;
        this.timestamp = timestamp;
        uri = FastenURI.create("fasten://" + forge + "!" + product + "$" + version);
        forgelessUri = FastenURI.create("fasten://" + product + "$" + version);
        this.cgGenerator = cgGenerator;
        this.classHierarchy = classHierarchy;
        this.nodeCount = nodeCount;
        this.graph = graph;
    }

    private ExtendedRevisionCallGraph(final ExtendedBuilder builder) {
        this.forge = builder.forge;
        this.product = builder.product;
        this.version = builder.version;
        this.timestamp = builder.timestamp;
        uri = FastenURI.create("fasten://" + forge + "!" + product + "$" + version);
        forgelessUri = FastenURI.create("fasten://" + product + "$" + version);
        this.cgGenerator = builder.cgGenerator;
        this.classHierarchy = builder.classHierarchy;
        this.graph = builder.graph;
        this.nodeCount = builder.nodeCount;
    }

    /**
     * Creates {@link ExtendedRevisionCallGraph} for the given JSONObject.
     *
     * @param json JSONObject of a revision call graph.
     */
    public ExtendedRevisionCallGraph(final JSONObject json) throws JSONException, IOException {

        this.forge = json.getString("forge");
        this.product = json.getString("product");
        this.version = json.getString("version");
        this.timestamp = getTimeStamp(json);
        uri = FastenURI.create("fasten://" + forge + "!" + product + "$" + version);
        forgelessUri = FastenURI.create("fasten://" + product + "$" + version);
        this.cgGenerator = json.getString("generator");
        this.graph = new Graph(json.getJSONObject("graph"));
        this.classHierarchy = getCHAFromJSON(json.getJSONObject("cha"));
        this.nodeCount = json.getInt("nodes");
    }

    /**
     * If timestamp is present in the JSON set it otherwise set it to -1.
     */
    private static long getTimeStamp(JSONObject json) {
        try {
            return json.getLong("timestamp");
        } catch (final JSONException exception) {
            logger.warn("No timestamp provided: assuming -1");
            return -1;
        }
    }

    /**
     * Creates builder to build {@link ExtendedRevisionCallGraph}.
     *
     * @return created builder
     */
    public static ExtendedBuilder extendedBuilder() {
        return new ExtendedBuilder();
    }

    /**
     * Creates a class hierarchy for the given JSONObject.
     *
     * @param cha JSONObject of a cha.
     */
    public static Map<Scope, Map<FastenURI, Type>> getCHAFromJSON(final JSONObject cha) throws IOException {

        final Map<FastenURI, Type> internals = new HashMap<>();
        final Map<FastenURI, Type> externals = new HashMap<>();
        final Map<FastenURI, Type> resolved = new HashMap<>();

        final var internalTypes = cha.getJSONObject("internalTypes");
        for (final var key : internalTypes.keySet()) {
            internals.put(FastenURI.create(key), new Type(internalTypes.getJSONObject(key)));
        }
        final var externalTypes = cha.getJSONObject("externalTypes");
        for (final var key : externalTypes.keySet()) {
            externals.put(FastenURI.create(key), new Type(externalTypes.getJSONObject(key)));
        }
        final var resolvedTypes = cha.getJSONObject("resolvedTypes");
        for (final var key : resolvedTypes.keySet()) {
            resolved.put(FastenURI.create(key), new Type(resolvedTypes.getJSONObject(key)));
        }

        return Map.of(Scope.internalTypes, internals,
                Scope.externalTypes, externals,
                Scope.resolvedTypes, resolved);
    }

    /**
     * Produces the JSON representation of this {@link ExtendedRevisionCallGraph}.
     *
     * @return the JSON representation.
     */
    public JSONObject toJSON() {

        final var result = new JSONObject();
        result.put("forge", forge);
        result.put("product", product);
        result.put("version", version);
        result.put("generator", cgGenerator);
        if (timestamp >= 0) {
            result.put("timestamp", timestamp);
        }
        result.put("cha", toJSON(classHierarchy));
        result.put("graph", graph.toJSON());
        result.put("nodes", nodeCount);

        return result;
    }

    /**
     * Produces the JSON representation of class hierarchy.
     *
     * @return the JSON representation.
     */
    public JSONObject toJSON(final Map<Scope, Map<FastenURI, Type>> cha) {

        final var result = new JSONObject();
        final var internalTypes = new JSONObject();
        final var externalTypes = new JSONObject();
        final var resolvedTypes = new JSONObject();

        for (final var entry : cha.get(Scope.internalTypes).entrySet()) {
            internalTypes.put(entry.getKey().toString(), entry.getValue().toJSON());
        }
        for (final var entry : cha.get(Scope.externalTypes).entrySet()) {
            externalTypes.put(entry.getKey().toString(), entry.getValue().toJSON());
        }
        for (final var entry : cha.get(Scope.resolvedTypes).entrySet()) {
            resolvedTypes.put(entry.getKey().toString(), entry.getValue().toJSON());
        }
        result.put("internalTypes", internalTypes);
        result.put("externalTypes", externalTypes);
        result.put("resolvedTypes", resolvedTypes);

        return result;

    }

    /**
     * Returns the map of all the methods of this object.
     *
     * @return a Map of method ids and their corresponding {@link FastenURI}
     */
    public Map<Integer, Node> mapOfAllMethods() {
        Map<Integer, Node> result = new HashMap<>();
        for (final var aClass : this.getClassHierarchy().get(Scope.internalTypes).entrySet()) {
            result.putAll(aClass.getValue().getMethods());
        }
        for (final var aClass : this.getClassHierarchy().get(Scope.externalTypes).entrySet()) {
            result.putAll(aClass.getValue().getMethods());
        }
        for (final var aClass : this.getClassHierarchy().get(Scope.resolvedTypes).entrySet()) {
            result.putAll(aClass.getValue().getMethods());
        }
        return result;
    }

    public boolean isCallGraphEmpty() {
        return this.graph.internalCalls.isEmpty() && this.graph.externalCalls.isEmpty() && this.graph.resolvedCalls.isEmpty();
    }

    public String getCgGenerator() {
        return cgGenerator;
    }

    public Map<Scope, Map<FastenURI, Type>> getClassHierarchy() {
        return classHierarchy;
    }

    public Graph getGraph() {
        return graph;
    }

    /**
     * Builder to build {@link ExtendedRevisionCallGraph}.
     */
    public static final class ExtendedBuilder {
        private String forge;
        private String product;
        private String version;
        private String cgGenerator;
        private long timestamp;
        private Map<Scope, Map<FastenURI, Type>> classHierarchy;
        private Graph graph;
        private int nodeCount;

        private ExtendedBuilder() {
        }

        public ExtendedBuilder nodeCount(final int nodeCount) {
            this.nodeCount = nodeCount;
            return this;
        }

        public ExtendedBuilder forge(final String forge) {
            this.forge = forge;
            return this;
        }

        public ExtendedBuilder product(final String product) {
            this.product = product;
            return this;
        }

        public ExtendedBuilder version(final String version) {
            this.version = version;
            return this;
        }

        public ExtendedBuilder cgGenerator(final String cgGenerator) {
            this.cgGenerator = cgGenerator;
            return this;
        }

        public ExtendedBuilder timestamp(final long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ExtendedBuilder graph(final ExtendedRevisionCallGraph.Graph graph) {
            this.graph = graph;
            return this;
        }

        public ExtendedBuilder classHierarchy(final Map<Scope, Map<FastenURI, Type>> cha) {
            this.classHierarchy = cha;
            return this;
        }

        public ExtendedRevisionCallGraph build() {
            return new ExtendedRevisionCallGraph(this);
        }
    }

    public static class Graph {

        /**
         * It keeps all the internal calls of the call graph using the ids of source and target
         * method. First element of the int[] is the id of the source method and the second one is
         * the target's id. Ids are available in the class hierarchy.
         */
        private final Map<List<Integer>, Map<Object, Object>> internalCalls;

        /**
         * External calls of the graph and key value metadata about each call. The {@link Pair}
         * keeps the id of source method in the left element and the {@link FastenURI} of the target
         * method in the right element. The meta data per call is stored as a map that keys and
         * values are {@link String}. For example in case of java for each call it can keep
         * (typeOfCall -> number_of_occurrence).
         */
        private final Map<List<Integer>, Map<Object, Object>> externalCalls;

        private final Map<List<Integer>, Map<Object, Object>> resolvedCalls;

        public Map<List<Integer>, Map<Object, Object>> getResolvedCalls() {
            return resolvedCalls;
        }

        public Graph(final Map<List<Integer>, Map<Object, Object>> internalCalls,
                     final Map<List<Integer>, Map<Object, Object>> externalCalls,
                     final Map<List<Integer>, Map<Object, Object>> resolvedCalls) {
            this.internalCalls = internalCalls;
            this.externalCalls = externalCalls;
            this.resolvedCalls = resolvedCalls;
        }

        /**
         * Creates {@link Graph} for the given JSONObject.
         *
         * @param graph JSONObject of a graph including its internal calls and external calls.
         */
        public Graph(final JSONObject graph) {
            this.internalCalls = extractCalls(graph, "internalCalls");
            this.externalCalls = extractCalls(graph, "externalCalls");
            this.resolvedCalls = extractCalls(graph, "resolvedCalls");
        }

        private Map<List<Integer>, Map<Object, Object>> extractCalls(JSONObject graph, String key) {
            final var internalCalls = graph.getJSONArray(key);
            final Map<List<Integer>, Map<Object, Object>> result = new HashMap<>();
            final int numberOfArcs = internalCalls.length();
            for (int i = 0; i < numberOfArcs; i++) {
                result.putAll(getCall(internalCalls.getJSONArray(i)));
            }
            return result;
        }

        public Graph(final HashMap<List<Integer>, Map<Object, Object>> internalCalls,
                     final HashMap<List<Integer>, Map<Object, Object>> externalCalls) {
            this.internalCalls = internalCalls;
            this.externalCalls = externalCalls;
            this.resolvedCalls = new HashMap<>();
        }

        public Map<List<Integer>, Map<Object, Object>> getCall(final JSONArray call) {
            final var callTypeJson = call.getJSONObject(2);
            final Map<Object, Object> callSite = new HashMap<>();
            for (String key : callTypeJson.keySet()) {
                final var cs = new OPALCallSite(callTypeJson.getJSONObject(key));
                final var pc = Integer.valueOf(key);
                callSite.put(pc, cs);
            }
            return Map.of(new ArrayList<>(Arrays.asList(Integer.valueOf(call.getString(0)),
                    Integer.valueOf(call.getString(1)))), callSite);
        }

        public Graph() {
            this.internalCalls = new HashMap<>();
            this.externalCalls = new HashMap<>();
            this.resolvedCalls = new HashMap<>();
        }

        /**
         * Converts a {@link Graph} object to its JSON representation.
         *
         * @param graph the {@link Graph} object to be converted.
         * @return the corresponding JSON representation.
         */
        public JSONObject toJSON(final Graph graph) {

            final var result = new JSONObject();
            final var internalCallsJSON = new JSONArray();
            for (final var entry : graph.internalCalls.entrySet()) {
                final var call = new JSONArray();
                call.put(entry.getKey().get(0).toString());
                call.put(entry.getKey().get(1).toString());
                call.put(new JSONObject(entry.getValue()));
                internalCallsJSON.put(call);
            }
            final var externalCallsJSON = new JSONArray();
            for (final var entry : graph.externalCalls.entrySet()) {
                final var call = new JSONArray();
                call.put(entry.getKey().get(0).toString());
                call.put(entry.getKey().get(1).toString());
                call.put(new JSONObject(entry.getValue()));
                externalCallsJSON.put(call);
            }

            final var resolvedCallsJSON = new JSONArray();
            for (final var entry : graph.resolvedCalls.entrySet()) {
                final var call = new JSONArray();
                call.put(entry.getKey().get(0).toString());
                call.put(entry.getKey().get(1).toString());
                call.put(new JSONObject(entry.getValue()));
                resolvedCallsJSON.put(call);
            }
            result.put("internalCalls", internalCallsJSON);
            result.put("externalCalls", externalCallsJSON);
            result.put("resolvedCalls", resolvedCallsJSON);
            return result;
        }

        public JSONObject toJSON() {
            return toJSON(this);
        }

        public Map<List<Integer>, Map<Object, Object>> getInternalCalls() {
            return internalCalls;
        }

        public Map<List<Integer>, Map<Object, Object>> getExternalCalls() {
            return externalCalls;
        }

        public int size() {
            return internalCalls.size() + externalCalls.size();
        }

        public void append(Graph graph) {
            this.internalCalls.putAll(graph.getInternalCalls());
            this.externalCalls.putAll(graph.getExternalCalls());
        }


    }

    public static class Node {

        final private FastenURI uri;
        final private Map<Object, Object> metadata;

        public Node(final FastenURI uri, final Map<Object, Object> metadata) {
            this.uri = uri;
            this.metadata = metadata;
        }

        public FastenURI getUri() {
            return uri;
        }

        public Map<Object, Object> getMetadata() {
            return metadata;
        }

        public String getEntity() {
            return this.uri.getEntity();
        }

        public String getClassName() {
            return getEntity().substring(0, getEntity().indexOf("."));
        }

        public String getMethodName() {
            return StringUtils.substringBetween(getEntity(), getClassName() + ".", "(");
        }

        public FastenURI changeName(final String className, final String methodName) {
            final var uri = this.getUri().toString().replace("/" + getClassName() + ".", "/" + className + ".");
            return FastenURI.create(uri.replace("." + getMethodName() + "(", "." + methodName + "("));
        }
    }

    /**
     * Each type is a class or an interface.
     */
    public static class Type {

        public Type(String sourceFileName) {

            this.sourceFileName = sourceFileName;
            this.methods = HashBiMap.create();
            this.superClasses = new LinkedList<>();
            this.superInterfaces = new ArrayList<>();
            this.access = "";
            this.isFinal = false;
        }

        public int addMethod(final Node node, final int key) {
            if (this.methods.containsValue(node)) {
                return this.methods.inverse().get(node);
            } else {
                this.methods.put(key, node);
                return key;
            }
        }

        /**
         * The source file name of this type.
         */
        private final String sourceFileName;

        /**
         * Methods of this type and their unique ids (unique within the same artifact).
         */
        private final BiMap<Integer, Node> methods;

        /**
         * Classes that this type inherits from in the order of instantiation.
         */
        private final LinkedList<FastenURI> superClasses;

        /**
         * Interfaces that this type or its super classes implement.
         */
        private final List<FastenURI> superInterfaces;

        private final String access;

        private final boolean isFinal;

        /**
         * Creates {@link Type} for the given data.
         *
         * @param sourceFile      the name of this type's source file
         * @param methods         a map of methods in this type with their indexed by their ids.
         * @param superClasses    classes that this type extends.
         * @param superInterfaces interfaces that this type implements.
         */
        public Type(final String sourceFile, final BiMap<Integer, Node> methods,
                    final LinkedList<FastenURI> superClasses,
                    final List<FastenURI> superInterfaces, final String access,
                    final boolean isFinal) {
            this.sourceFileName = sourceFile;
            this.methods = methods;
            this.superClasses = superClasses;
            this.superInterfaces = superInterfaces;
            this.access = access;
            this.isFinal = isFinal;
        }

        /**
         * Creates {@link Type} for the given JSONObject.
         *
         * @param type JSONObject of a type including its source file name, map of methods, super
         *             classes and super interfaces.
         */
        public Type(final JSONObject type) throws IOException {

            this.sourceFileName = type.getString("sourceFile");

            final var methodsJson = type.getJSONObject("methods");
            this.methods = HashBiMap.create();
            for (final var methodKey : methodsJson.keySet()) {
                final var nodeJson = methodsJson.getJSONObject(methodKey);
                this.methods.put(Integer.parseInt(methodKey),
                        new Node(FastenURI.create(nodeJson.getString("uri")),
                                new ObjectMapper().readValue(nodeJson.getJSONObject("metadata").toString(), Map.class)));
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
        }

        /**
         * Converts all the valuses of a given Map to String.
         */
        public static Map<Integer, JSONObject> toMapOfString(final Map<Integer, Node> map) {
            final Map<Integer, JSONObject> methods = new HashMap<>();
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
         */
        public static List<String> toListOfString(final List<?> list) {
            final List<String> result = new ArrayList<>();
            for (final var fastenURI : list) {
                result.add(fastenURI.toString());
            }
            return result;
        }

        /**
         * Converts a {@link Type} object to its JSON representation.
         *
         * @param type the {@link Type} object to be converted.
         * @return the corresponding JSON representation.
         */
        public JSONObject toJSON(final Type type) {
            final var result = new JSONObject();

            result.put("methods", toMapOfString(type.methods));
            result.put("superClasses", toListOfString(type.superClasses));
            result.put("superInterfaces", toListOfString(type.superInterfaces));
            result.put("sourceFile", type.sourceFileName);
            result.put("access", type.access);
            result.put("final", type.isFinal);

            return result;
        }

        public JSONObject toJSON() {
            return toJSON(this);
        }

        public String getSourceFileName() {
            return sourceFileName;
        }

        public Map<Integer, Node> getMethods() {
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

        @Override
        public String toString() {
            return "Type{"
                    + "sourceFileName='" + sourceFileName + '\''
                    + ", methods=" + methods
                    + ", superClasses=" + superClasses
                    + ", superInterfaces=" + superInterfaces
                    + ", access=" + access
                    + ", final=" + isFinal
                    + '}';
        }

        public Optional<Map.Entry<Integer, Node>> getDefined(String signature) {
            return methods.entrySet().stream().filter(node -> node.getValue().uri.getEntity().contains(signature)).findAny();
        }
    }
}
