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

public abstract class PartialCallGraph {

    private static final Logger logger = LoggerFactory.getLogger(PartialCallGraph.class);

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
     * Creates {@link PartialCallGraph} with the given data.
     *
     * @param forge          the forge.
     * @param product        the product.
     * @param version        the version.
     * @param timestamp      the timestamp (in seconds from UNIX epoch); optional: if not present,
     *                       it is set to -1.
     * @param cgGenerator    The name of call graph generator that generated this call graph.
     */
    protected PartialCallGraph(final String forge, final String product, final String version,
                               final long timestamp, final String cgGenerator) {
        this.forge = forge;
        this.product = product;
        this.version = version;
        this.productVersion = this.product + "$" + this.version;
        this.timestamp = timestamp;
        this.uri = FastenURI.create("fasten://" + forge + "!" + product + "$" + version);
        this.forgelessUri = FastenURI.create("fasten://" + product + "$" + version);
        this.cgGenerator = cgGenerator;
    }

    /**
     * Creates {@link PartialCallGraph} for the given JSONObject.
     *
     * @param json JSONObject of a revision call graph.
     */
    protected PartialCallGraph(final JSONObject json) throws JSONException {
        this.forge = json.getString("forge");
        this.product = json.getString("product");
        this.version = json.getString("version");
        this.productVersion = this.product + "$" + this.version;
        this.timestamp = getTimeStamp(json);
        this.uri = FastenURI.create("fasten://" + forge + "!" + product + "$" + version);
        this.forgelessUri = FastenURI.create("fasten://" + product + "$" + version);
        this.cgGenerator = json.getString("generator");

    }

    public String getCgGenerator() {
        return cgGenerator;
    }

    public abstract <T> T getGraph();

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
     * Produces the JSON representation of this {@link PartialCallGraph}.
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
        return result;
    }

    /**
     * Returns a string representation of the revision.
     *
     * @return String representation of the revision.
     */
    public abstract String getRevisionName();

}
