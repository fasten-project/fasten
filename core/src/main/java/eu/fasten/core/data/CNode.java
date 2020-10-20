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
import java.util.List;
import org.json.JSONObject;
import org.json.JSONArray;

public class CNode extends Node {

    private List<String> files;

    /**
     * Creates {@link CNode} from a FastenURI and metadata.
     *
     * @param uri      FastenURI corresponding to this CNode
     * @param metadata metadata associated with this CNode
     * @param files    a list with the files of this CNode. In most cases, there
     *                 is only one file in the list. Currently, we handle files
     *                 as if it is always at most one file.
     */
    public CNode(final FastenURI uri, final Map<String, Object> metadata, final List<String> files) {
        super(uri, metadata);
        this.files = files;
    }

    public List<String> getFiles() {
        return files;
    }

    /**
     * Returns an empty String if there isn't any file, otherwise returns the
     * first file.
     */
    public String getFile() {
        if (files.isEmpty())
            return "";
        return files.get(0);
    }

    /**
     * Converts this {@link CNode} object to its JSON representation.
     *
     * @return the corresponding JSON representation.
     */
    public JSONObject toJSON() {
        final var result = new JSONObject();

        result.put("metadata", new JSONObject(metadata));
        result.put("files", new JSONArray(files));
        result.put("uri", uri.toString());

        return result;
    }
}
