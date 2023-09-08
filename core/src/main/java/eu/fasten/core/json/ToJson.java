/*
 * Copyright 2022 Delft University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fasten.core.json;

import org.json.JSONObject;

import dev.c0ps.maven.data.Revision;

public class ToJson {

    public static JSONObject map(Revision r) {
        var json = new JSONObject();
        json.put("id", r.id);
        json.put("groupId", r.getGroupId());
        json.put("artifactId", r.getArtifactId());
        json.put("version", r.version.toString());
        json.put("createdAt", r.createdAt.getTime());
        return json;
    }
}