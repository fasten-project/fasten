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

public abstract class Node {


    /**
     * FastenURI corresponding to this Node.
     */
    protected FastenURI uri;

    /**
     * Metadata associated with this Node.
     */
    protected Map<String, Object> metadata;

    /**
     * Creates {@link Node} from a FastenURI and metadata.
     *
     * @param uri      FastenURI corresponding to this Node
     * @param metadata metadata associated with this Node
     */
    public Node(final FastenURI uri, final Map<String, Object> metadata) {
        this.uri = uri;
        this.metadata = metadata;
    }

    public FastenURI getUri() {
        return uri;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Get entity from the FastenURI.
     *
     * @return entity
     */
    public String getEntity() {
        return this.uri.getRawEntity();
    }
}
