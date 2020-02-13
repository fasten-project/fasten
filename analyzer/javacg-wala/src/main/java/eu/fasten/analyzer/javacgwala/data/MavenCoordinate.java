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


package eu.fasten.analyzer.javacgwala.data;

import java.io.Serializable;
import java.util.Objects;

public class MavenCoordinate implements Serializable {

    public final String artifactId;
    public final String groupId;
    public final String version;

    /**
     * Construct {@link MavenCoordinate} from groupID, artifactID, and version.
     *
     * @param groupId    GroupID
     * @param artifactId ArtifactID
     * @param version    Version
     */
    public MavenCoordinate(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;

    }

    /**
     * Build {@link MavenCoordinate} given string representing an artifact in a canonical form.
     *
     * @param identifier Identifier
     * @return New Maven Coordinate
     */
    public static MavenCoordinate of(String identifier) {
        String[] segments = identifier.split(":");
        assert segments.length == 3;
        return new MavenCoordinate(segments[0], segments[1], segments[2]);

    }

    /**
     * Get a string representation of this Maven Coordinate.
     *
     * @return Artifact representation in canonical form
     */
    public String getCanonicalForm() {
        return String.join(":",
                this.groupId,
                this.artifactId,
                this.version);
    }

    @Override
    public String toString() {
        return "MavenCoordinate(" + this.groupId + ","
                + this.artifactId + ","
                + this.version + ")";
    }

    @Override
    public boolean equals(Object o) {
        // self check
        if (this == o) {
            return true;
        }
        // null check
        if (o == null) {
            return false;
        }
        // type check and cast
        if (getClass() != o.getClass()) {
            return false;
        }
        MavenCoordinate coord = (MavenCoordinate) o;
        return
                Objects.equals(this.groupId, coord.groupId)
                        && Objects.equals(this.artifactId, coord.artifactId)
                        && Objects.equals(this.version, coord.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.groupId, this.artifactId, this.version);
    }
}
