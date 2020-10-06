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

import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ExtendedRevisionCallGraph<A> {

    private static final Logger logger = LoggerFactory.getLogger(ExtendedRevisionCallGraph.class);

    /**
     * For each class in the revision, class hierarchy keeps a {@link Type} that is accessible by
     * the {@link FastenURI} of the class as a key.
     *
     * @implNote each method in the revision has a unique id in this CHA.
     */
    protected A classHierarchy;

    protected static String classHierarchyJSONKey = "cha";

    /**
     * The number of nodes in a revision call graph.
     */
    protected int nodeCount;

    /**
     * Includes all the edges of the revision call graph (internal & external).
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
     * Creates {@link ExtendedRevisionJavaCallGraph} with the given builder.
     *
     * @param builder builder for {@link ExtendedRevisionJavaCallGraph}
     */
    protected ExtendedRevisionCallGraph(final ExtendedBuilder<A> builder) {
        this.forge = builder.getForge();
        this.product = builder.getProduct();
        this.version = builder.getVersion();
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
     * @param classHierarchy class hierarchy of this revision including all classes of the revision
     *                       <code> Map<{@link FastenURI}, {@link Type}> </code>
     * @param graph          the call graph (no control is done on the graph) {@link Graph}
     */
    protected ExtendedRevisionCallGraph(final String forge, final String product, final String version,
                                     final long timestamp, int nodeCount, final String cgGenerator,
                                     final A classHierarchy,
                                     final Graph graph) {
        this.forge = forge;
        this.product = product;
        this.version = version;
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
    protected ExtendedRevisionCallGraph(final JSONObject json) throws JSONException {
        this.forge = json.getString("forge");
        this.product = json.getString("product");
        this.version = json.getString("version");
        this.timestamp = getTimeStamp(json);
        this.uri = FastenURI.create("fasten://" + forge + "!" + product + "$" + version);
        this.forgelessUri = FastenURI.create("fasten://" + product + "$" + version);
        this.cgGenerator = json.getString("generator");
        this.graph = new Graph(json.getJSONObject("graph"));
        this.classHierarchy = getCHAFromJSON(json.getJSONObject(this.classHierarchyJSONKey));
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
        return this.graph.getInternalCalls().isEmpty()
                && this.graph.getExternalCalls().isEmpty()
                && this.graph.getResolvedCalls().isEmpty();
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
        result.put(this.classHierarchyJSONKey, classHierarchyToJSON(classHierarchy));
        result.put("graph", graph.toJSON());
        result.put("nodes", nodeCount);

        return result;
    }
}
