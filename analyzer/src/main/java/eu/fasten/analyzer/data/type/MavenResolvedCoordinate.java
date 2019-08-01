package eu.fasten.analyzer.data.type;

import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class MavenResolvedCoordinate extends MavenCoordinate implements Serializable {
    public final Path jarPath;

    public MavenResolvedCoordinate(String groupId, String artifactId, String version, Path jarPath) {
        super(groupId, artifactId, version);
        this.jarPath = jarPath;
    }


    public static MavenResolvedCoordinate of(MavenResolvedArtifact artifact) {
        return new MavenResolvedCoordinate(
                artifact.getCoordinate().getGroupId(),
                artifact.getCoordinate().getArtifactId(),
                artifact.getCoordinate().getVersion(),
                artifact.as(Path.class));
    }

    public static MavenResolvedCoordinate of(IdeaSingleEntryLibraryDependency d) {
        GradleModuleVersion mod = d.getGradleModuleVersion();
        return new MavenResolvedCoordinate(
                mod.getGroup(),
                mod.getName(),
                mod.getVersion(),
                Paths.get(d.getFile().toURI()));
    }
}
