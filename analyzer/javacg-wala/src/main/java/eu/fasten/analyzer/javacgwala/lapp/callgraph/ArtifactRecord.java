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


package eu.fasten.analyzer.javacgwala.lapp.callgraph;

import java.util.Objects;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.eclipse.aether.artifact.Artifact;

public class ArtifactRecord {

    public final String groupId;
    public final String artifactId;
    private String version;

    /**
     * Constructs an artifact record based on its group ID, artifact ID, and version.
     *
     * @param groupId    Group ID
     * @param artifactId Artifact ID
     * @param version    Version
     */
    public ArtifactRecord(String groupId, String artifactId, String version) {
        this.groupId = Objects.requireNonNull(groupId, "groupId must not be null");
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId must not be null");
        this.version = version;
    }

    /**
     * Constructs an artifact record based on its group ID, artifact ID, and version represented
     * in a String format.
     *
     * @param identifier String representation of an artifact
     */
    public ArtifactRecord(String identifier) {
        Objects.requireNonNull(identifier);

        if (!isValidIdentifier(identifier)) {
            throw new IllegalArgumentException("Malformed identifier string");
        }

        String[] parts = identifier.split(":");

        this.groupId = parts[0];
        this.artifactId = parts[1];

        if (parts.length == 3) {
            this.version = parts[2];
        }
    }

    public String getVersion() {
        return this.version;
    }

    public String getUnversionedIdentifier() {
        return String.format("%s:%s", groupId, artifactId);
    }

    public String getIdentifier() {
        return getIdentifier(groupId, artifactId, version);
    }

    public static String getIdentifier(Artifact artifact) {
        return getIdentifier(artifact.getGroupId(),
                artifact.getArtifactId(), artifact.getVersion());
    }

    public static String getIdentifier(String groupId, String artifactId, String version) {
        return String.format("%s:%s:%s", groupId, artifactId, version);
    }

    /**
     * Check if String representation of artifact is valid.
     *
     * @param identifier Artifact identifier
     * @return True if identifier is valid
     */
    public static boolean isValidIdentifier(String identifier) {
        if (identifier == null) {
            return false;
        }

        String[] parts = identifier.split(":", 3);

        if (parts.length < 2) {
            return false;
        }

        if (parts[0].length() == 0
                || parts[1].length() == 0
                || parts.length == 3 && parts[2].length() == 0) {
            return false;
        }

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ArtifactRecord that = (ArtifactRecord) o;

        return new EqualsBuilder()
                .append(groupId, that.groupId)
                .append(artifactId, that.artifactId)
                .append(version, that.version)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(groupId)
                .append(artifactId)
                .append(version)
                .toHashCode();
    }
}
