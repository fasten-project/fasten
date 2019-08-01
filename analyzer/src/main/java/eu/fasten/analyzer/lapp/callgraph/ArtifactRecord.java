package eu.fasten.analyzer.lapp.callgraph;

import java.util.Objects;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.eclipse.aether.artifact.Artifact;

public class ArtifactRecord {

    public final String groupId;
    public final String artifactId;
    private String version;

    public ArtifactRecord(String groupId, String artifactId, String version) {

        this.groupId = Objects.requireNonNull(groupId, "groupId must not be null");
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId must not be null");
        this.version = version;
    }

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
                || (parts.length == 3 && parts[2].length() == 0)) {
            return false;
        }

        return true;
    }

    public static String getIdentifier(Artifact artifact) {
        return getIdentifier(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }

    public static String getIdentifier(String groupId, String artifactId, String version) {
        return String.format("%s:%s:%s", groupId, artifactId, version);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

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