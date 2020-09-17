package eu.fasten.core.maven.data;

import eu.fasten.core.data.Constants;
import org.jboss.shrinkwrap.resolver.api.maven.PackagingType;

public class MavenCoordinate
        implements org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate {

    private String artifactId;
    private String groupId;
    private String version;

    public MavenCoordinate(String groupId, String artifactId, String version) {
        if (groupId == null) {
            throw new IllegalArgumentException("groupId cannot be null");
        }
        if (artifactId == null) {
            throw new IllegalArgumentException("artifactId cannot be null");
        }
        if (version == null) {
            throw new IllegalArgumentException("version cannot be null");
        }
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public MavenCoordinate(String mavenCoordinate) {
        if (!mavenCoordinate.matches(".+" + Constants.mvnCoordinateSeparator
                + ".+" + Constants.mvnCoordinateSeparator + ".+")) {
            throw new IllegalArgumentException("Maven coordinate must be in form of 'groupId"
                    + Constants.mvnCoordinateSeparator + "artifactId"
                    + Constants.mvnCoordinateSeparator + "version'");
        }
        var coordinates = mavenCoordinate.split(Constants.mvnCoordinateSeparator);
        this.groupId = coordinates[0];
        this.artifactId = coordinates[1];
        this.version = coordinates[2];
    }

    @Override
    public PackagingType getPackaging() {
        return null;
    }

    @Override
    public PackagingType getType() {
        return null;
    }

    @Override
    public String getClassifier() {
        return null;
    }

    @Override
    public String getVersion() {
        return this.version;
    }

    @Override
    public String getGroupId() {
        return this.groupId;
    }

    @Override
    public String getArtifactId() {
        return this.artifactId;
    }

    @Override
    public String toCanonicalForm() {
        return this.groupId + Constants.mvnCoordinateSeparator + this.artifactId
                + Constants.mvnCoordinateSeparator + this.version;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MavenCoordinate) {
            var other = (MavenCoordinate) o;
            return this.toCanonicalForm().equals(other.toCanonicalForm());
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return toCanonicalForm();
    }
}
