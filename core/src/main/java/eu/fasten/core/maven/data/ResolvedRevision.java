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
package eu.fasten.core.maven.data;

import java.sql.Timestamp;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

public class ResolvedRevision extends Revision {

    private static final long serialVersionUID = -8029991532778209365L;

    public Scope scope;

    public ResolvedRevision() {}

    public ResolvedRevision(Revision r, Scope s) {
        this.id = r.id;
        this.groupId = r.groupId;
        this.artifactId = r.artifactId;
        this.version = r.version;
        this.createdAt = r.createdAt;
        this.scope = s;
    }

    public ResolvedRevision(long id, String groupId, String artifactId, String version, Timestamp createdAt,
            Scope scope) {
        this.id = id;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = new DefaultArtifactVersion(version);
        this.createdAt = createdAt;
        this.scope = scope;
    }
}