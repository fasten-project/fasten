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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.json.JSONObject;

public class PythonType {

    /**
     * The source file name of this type.
     */
    private final String sourceFileName;

    /**
     * methods of this type and their unique ids.
     */
    private final BiMap<Integer, PythonNode> methods;

    /**
     * Creates {@link PythonType} for the given data.
     *
     * @param sourceFile      the name of this type's source file
     * @param methods      a map of methods in this type indexed by their ids.
     */
    public PythonType(final String sourceFile, final BiMap<Integer, PythonNode> methods) {
        this.sourceFileName = sourceFile;
        this.methods = methods;
    }

    /**
     * Creates {@link PythonType} for the given JSONObject.
     *
     * @param type JSONObject of a type including its source file name and map of namespaces
     */
    public PythonType(final JSONObject type) {
        this.sourceFileName = type.getString("sourceFile");

        final var methodsJson = type.getJSONObject("namespaces");
        this.methods = HashBiMap.create();
        for (final var methodKey : methodsJson.keySet()) {
            final var nodeJson = methodsJson.getJSONObject(methodKey);
            this.methods.put(Integer.parseInt(methodKey),
                    new PythonNode(FastenPythonURI.create(nodeJson.getString("namespace")), nodeJson.getJSONObject("metadata").toMap()));
        }
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public Map<Integer, PythonNode> getMethods() {
        return this.methods;
    }

    /**
     * Converts all the values of a given Map to String.
     *
     * @param map map of id-s and corresponding JavaNodes
     */
    public static Map<Integer, JSONObject> toMapOfString(final Map<Integer, PythonNode> map) {
        final Map<Integer, JSONObject> methods = new HashMap<>();
        for (final var entry : map.entrySet()) {
            final JSONObject node = new JSONObject();
            node.put("namespace", entry.getValue().getUri());
            node.put("metadata", new JSONObject(entry.getValue().getMetadata()));
            methods.put(entry.getKey(), node);
        }
        return methods;
    }

    /**
     * Converts this {@link PythonType} object to its JSON representation.
     *
     * @return the corresponding JSON representation.
     */
    public JSONObject toJSON() {
        final var result = new JSONObject();

        result.put("sourceFile", this.sourceFileName);
        result.put("namespaces", toMapOfString(this.methods));

        return result;
    }
}
