package eu.fasten.analyzer.javacgwala.data;

import java.io.Serializable;
import java.nio.file.Path;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;

public final class MavenResolvedCoordinate extends MavenCoordinate implements Serializable {
    public final Path jarPath;

    /**
     * Construct maven resolve coordinate based on groupID, artifactID, version and
     * a path the jar file.
     *
     * @param groupId    Group ID
     * @param artifactId Artifact ID
     * @param version    Version
     * @param jarPath    Path to jar file
     */
    public MavenResolvedCoordinate(String groupId, String artifactId,
                                   String version, Path jarPath) {
        super(groupId, artifactId, version);
        this.jarPath = jarPath;
    }

    /**
     * Create new {@link MavenResolvedCoordinate} given a {@link MavenResolvedArtifact}.
     *
     * @param artifact Maven resolved artifact
     * @return New Maven Resolved Coordinate
     */
    public static MavenResolvedCoordinate of(MavenResolvedArtifact artifact) {
        return new MavenResolvedCoordinate(
                artifact.getCoordinate().getGroupId(),
                artifact.getCoordinate().getArtifactId(),
                artifact.getCoordinate().getVersion(),
                artifact.as(Path.class));
    }
}
