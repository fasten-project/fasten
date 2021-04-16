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

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ExtendedRevisionCallGraph<A> {

    private static final Logger logger = LoggerFactory.getLogger(ExtendedRevisionCallGraph.class);

    /**
     * Each implementation of ExtendedRevisionCallGraph provides a different
     * data structure for saving the classHierarchy.
     *
     * Some languages that don't have a class hierarchy save in this field
     * information about methods. For example, see ExtendedRevisionCCallGraph.
     */
    protected A classHierarchy;

    /**
     * Different languages may save the class hierarchy in a different JSON key.
     *
     * For instance, in C revision call graph format, a key called functions
     * contains the information to be saved in the classHierarchy field.
     */
    protected static String classHierarchyJSONKey = "cha";

    /**
     * The number of nodes in a revision call graph.
     */
    protected int nodeCount;

    /**
     * Includes all the edges of the revision call graph (internal, external,
     * and resolved).
     */
    protected Graph graph;

    /**
     * The forge.
     */
    public String forge;

    /**
     * The product.
     */
    public String product;

    /**
     * The version.
     */
    public String version;

    /**
     * The product concatenated with version, separated by $.
     */
    public String productVersion;

    /**
     * The timestamp (if specified, or -1) in seconds from UNIX Epoch.
     */
    public long timestamp;

    /**
     * The URI of this revision.
     */
    public FastenURI uri;

    /**
     * The forgeless URI of this revision.
     */
    public FastenURI forgelessUri;

    /**
     * Keeps the name of call graph generator that generated this revision call graph.
     */
    protected String cgGenerator;

    /**
     * Dummy Constructor, we need it to create objects by using an ExtendedBuilder.
     */
    protected ExtendedRevisionCallGraph() {}

    /**
     * Creates {@link ExtendedRevisionCallGraph} with the given builder.
     *
     * @param builder builder for {@link ExtendedRevisionCallGraph}
     */
    protected ExtendedRevisionCallGraph(final ExtendedBuilder<A> builder) {
        this.forge = builder.getForge();
        this.product = builder.getProduct();
        this.version = builder.getVersion();
        this.productVersion = this.product + "$" + this.version;
        this.timestamp = builder.getTimeStamp();
        this.uri = FastenURI.create("fasten://" + forge + "!" + product + "$" + version);
        this.forgelessUri = FastenURI.create("fasten://" + product + "$" + version);
        this.cgGenerator = builder.getCgGenerator();
        this.classHierarchy = builder.getClassHierarchy();
        this.graph = builder.getGraph();
        this.nodeCount = builder.getNodeCount();
    }

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
     * @param classHierarchy The class hierarchy
     * @param graph          the call graph (no control is done on the graph) {@link Graph}
     */
    protected ExtendedRevisionCallGraph(final String forge, final String product, final String version,
                                     final long timestamp, int nodeCount, final String cgGenerator,
                                     final A classHierarchy,
                                     final Graph graph) {
        this.forge = forge;
        this.product = product;
        this.version = version;
        this.productVersion = this.product + "$" + this.version;
        this.timestamp = timestamp;
        this.uri = FastenURI.create("fasten://" + forge + "!" + product + "$" + version);
        this.forgelessUri = FastenURI.create("fasten://" + product + "$" + version);
        this.cgGenerator = cgGenerator;
        this.classHierarchy = classHierarchy;
        this.nodeCount = nodeCount;
        this.graph = graph;
    }

    /**
     * Creates {@link ExtendedRevisionCallGraph} for the given JSONObject.
     *
     * @param json JSONObject of a revision call graph.
     */
    protected ExtendedRevisionCallGraph(final JSONObject json, Class rcgClass) throws JSONException {
        if (rcgClass.getName().equals(ExtendedRevisionCCallGraph.class.getName())) {
            classHierarchyJSONKey = "functions";
        } else if (rcgClass.getName().equals(ExtendedRevisionPythonCallGraph.class.getName())) {
            classHierarchyJSONKey = "modules";
        } else {
            classHierarchyJSONKey = "cha";
        }
        this.forge = json.getString("forge");
        this.product = json.getString("product");
        this.version = json.getString("version");
        this.productVersion = this.product + "$" + this.version;
        this.timestamp = getTimeStamp(json);
        this.uri = FastenURI.create("fasten://" + forge + "!" + product + "$" + version);
        this.forgelessUri = FastenURI.create("fasten://" + product + "$" + version);
        this.cgGenerator = json.getString("generator");
        if (!rcgClass.getName().equals(ExtendedRevisionJavaCallGraph.class.getName())) {
            this.graph = new Graph(json.getJSONObject("graph"));
        }
        this.classHierarchy = getCHAFromJSON(json.getJSONObject(classHierarchyJSONKey));
        this.nodeCount = json.getInt("nodes");
    }

    public String getCgGenerator() {
        return cgGenerator;
    }

    public A getClassHierarchy() {
        return classHierarchy;
    }

    public Graph getGraph() {
        return graph;
    }

    public int getNodeCount() {
        return nodeCount;
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
     * Creates a class hierarchy for the given JSONObject.
     *
     * @param cha JSONObject of a cha.
     */
    public abstract A getCHAFromJSON(final JSONObject cha);

    /**
     * Returns the map of all the methods of this object.
     *
     * @return a Map of method ids and their corresponding {@link FastenURI}
     */
    public abstract <T> T mapOfAllMethods();

    /**
     * Produces the JSON representation of class hierarchy.
     *
     * @param cha class hierarchy
     * @return the JSON representation
     */
    public abstract JSONObject classHierarchyToJSON(final A cha);

    /**
     * Checks whether this {@link ExtendedRevisionCallGraph} is empty, e.g. has no calls.
     *
     * @return true if this {@link ExtendedRevisionCallGraph} is empty
     */
    public boolean isCallGraphEmpty() {
        if (this.graph instanceof JavaGraph) {
            return ((JavaGraph) graph).getCallSites().isEmpty();
        } else {
            return this.graph.getInternalCalls().isEmpty()
                    && this.graph.getExternalCalls().isEmpty()
                    && this.graph.getResolvedCalls().isEmpty();
        }
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
        result.put(classHierarchyJSONKey, classHierarchyToJSON(classHierarchy));
        result.put("graph", graph.toJSON());
        result.put("nodes", nodeCount);

        return result;
    }

    /**
     * Returns a string representation of the revision.
     *
     * @return String representation of the revision.
     */
    public abstract String getRevisionName();

}
