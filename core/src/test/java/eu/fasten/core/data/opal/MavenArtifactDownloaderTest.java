/*
 * Copyright 2021 Delft University of Technology
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
package eu.fasten.core.data.opal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.maven.utils.MavenUtilities;

public class MavenArtifactDownloaderTest {

	@Test
    void downloadJarEmptyRepos() {
        var coordinate = new MavenCoordinate("group", "artifact", "version", "jar");
        coordinate.setMavenRepos(new ArrayList<>());

        assertThrows(MissingArtifactException.class, () -> new MavenArtifactDownloader(coordinate).downloadArtifact(null));
    }

    @Test
    void downloadJarWrongRepos() {
        var coordinate = new MavenCoordinate("group", "artifact", "version", "jar");
        coordinate.setMavenRepos(new ArrayList<>(Collections.singletonList("repo")));

        assertThrows(MissingArtifactException.class, () -> new MavenArtifactDownloader(coordinate).downloadArtifact(null));
    }

    @Test
    void downloadJarPomPackaging() {
        var coordinate = new MavenCoordinate("group", "artifact", "version", "pom");
        coordinate.setMavenRepos(new ArrayList<>(Collections.singletonList("repo")));

        assertThrows(MissingArtifactException.class, () -> new MavenArtifactDownloader(coordinate).downloadArtifact(null));
    }

    @Test
    void downloadJarWrongPackaging() throws MissingArtifactException {
        var coordinate = new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.30", "wrongPackagingType");

        assertNotNull(new MavenArtifactDownloader(coordinate).downloadArtifact(MavenUtilities.MAVEN_CENTRAL_REPO));
    }
}