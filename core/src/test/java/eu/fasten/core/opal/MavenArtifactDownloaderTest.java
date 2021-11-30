package eu.fasten.core.opal;

import eu.fasten.core.data.opal.MavenArtifactDownloader;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.maven.utils.MavenUtilities;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MavenArtifactDownloaderTest {

    @Test
    void downloadJarEmptyRepos() {
        var coordinate = new MavenCoordinate("group", "artifact", "version", "jar");
        coordinate.setMavenRepos(new ArrayList<>());
        var resolver = new MavenArtifactDownloader();

        assertThrows(MissingArtifactException.class, () -> resolver.downloadArtifact(coordinate, null));
    }

    @Test
    void downloadJarWrongRepos() {
        var coordinate = new MavenCoordinate("group", "artifact", "version", "jar");
        coordinate.setMavenRepos(new ArrayList<>(Collections.singletonList("repo")));
        var resolver = new MavenArtifactDownloader();

        assertThrows(MissingArtifactException.class, () -> resolver.downloadArtifact(coordinate, null));
    }

    @Test
    void downloadJarPomPackaging() {
        var coordinate = new MavenCoordinate("group", "artifact", "version", "pom");
        coordinate.setMavenRepos(new ArrayList<>(Collections.singletonList("repo")));
        var resolver = new MavenArtifactDownloader();

        assertThrows(MissingArtifactException.class, () -> resolver.downloadArtifact(coordinate, null));
    }

    @Test
    void downloadJarWrongPackaging() throws MissingArtifactException {
        var coordinate = new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.30", "wrongPackagingType");
        var resolver = new MavenArtifactDownloader();

        assertNotNull(resolver.downloadArtifact(coordinate, MavenUtilities.MAVEN_CENTRAL_REPO));
    }


}
