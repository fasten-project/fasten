package eu.fasten.core.opal;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import eu.fasten.core.data.opal.MavenArtifactDownloader;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.maven.utils.MavenUtilities;

public class MavenArtifactDownloaderTest {

    @Test
    void downloadJarEmptyRepos() {
        var coordinate = new MavenCoordinate("group", "artifact", "version", "jar");
        coordinate.setMavenRepos(new ArrayList<>());
        var resolver = new MavenArtifactDownloader(coordinate);

        assertThrows(MissingArtifactException.class, () -> resolver.downloadArtifact(null));
    }

    @Test
    void downloadJarWrongRepos() {
        var coordinate = new MavenCoordinate("group", "artifact", "version", "jar");
        coordinate.setMavenRepos(new ArrayList<>(Collections.singletonList("repo")));
        var resolver = new MavenArtifactDownloader(coordinate);

        assertThrows(MissingArtifactException.class, () -> resolver.downloadArtifact(null));
    }

    @Test
    void downloadJarPomPackaging() {
        var coordinate = new MavenCoordinate("group", "artifact", "version", "pom");
        coordinate.setMavenRepos(new ArrayList<>(Collections.singletonList("repo")));
        var resolver = new MavenArtifactDownloader(coordinate);

        assertThrows(MissingArtifactException.class, () -> resolver.downloadArtifact(null));
    }

    @Test
    void downloadJarWrongPackaging() {
        var coordinate = new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.30", "wrongPackagingType");
        assertThrows(MissingArtifactException.class, () -> {
            new MavenArtifactDownloader(coordinate).downloadArtifact(MavenUtilities.MAVEN_CENTRAL_REPO);
        });
    }
}