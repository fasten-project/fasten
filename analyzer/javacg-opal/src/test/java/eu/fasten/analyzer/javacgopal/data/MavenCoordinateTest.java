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

package eu.fasten.analyzer.javacgopal.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;

import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.maven.utils.MavenUtilities;
import org.junit.jupiter.api.Test;

class MavenCoordinateTest {

    @Test
    void constructorTest() {
        MavenCoordinate coordinate1 = new MavenCoordinate("group", "artifact", "version", "jar");

        assertEquals("group", coordinate1.getGroupID());

        assertEquals("artifact", coordinate1.getArtifactID());

        assertEquals("version", coordinate1.getVersionConstraint());

        assertEquals(new ArrayList<>(Collections
                        .singletonList("https://repo.maven.apache.org/maven2/")),
                coordinate1.getMavenRepos());
    }

    @Test
    void fromString() {
        var coordinate = MavenCoordinate.fromString("GroupID:ArtifactID:Version", "jar");

        assertEquals("GroupID", coordinate.getGroupID());
        assertEquals("ArtifactID", coordinate.getArtifactID());
        assertEquals("Version", coordinate.getVersionConstraint());
    }

    @Test
    void getProduct() {
        var coordinate = MavenCoordinate.fromString("GroupID:ArtifactID:Version", "jar");
        assertEquals("GroupID:ArtifactID", coordinate.getProduct());
    }

    @Test
    void getCoordinate() {
        var coordinate = MavenCoordinate.fromString("GroupID:ArtifactID:Version", "jar");
        assertEquals("GroupID:ArtifactID:Version", coordinate.getCoordinate());
    }

    @Test
    void toURL() {
        var coordinate = MavenCoordinate.fromString("GroupID:ArtifactID:Version", "jar");
        assertEquals("repo/GroupID/ArtifactID/Version/ArtifactID-Version.jar", coordinate.toProductUrl("repo/", "jar"));
    }

    // ------------------
    // Maven Resolver Tests
    // ------------------

    @Test
    void downloadJarEmptyRepos() {
        var coordinate = new MavenCoordinate("group", "artifact", "version", "jar");
        coordinate.setMavenRepos(new ArrayList<>());
        var resolver = new MavenCoordinate.MavenResolver();

        assertThrows(MissingArtifactException.class, () -> resolver.downloadArtifact(coordinate, null));
    }

    @Test
    void downloadJarWrongRepos() {
        var coordinate = new MavenCoordinate("group", "artifact", "version", "jar");
        coordinate.setMavenRepos(new ArrayList<>(Collections.singletonList("repo")));
        var resolver = new MavenCoordinate.MavenResolver();

        assertThrows(MissingArtifactException.class, () -> resolver.downloadArtifact(coordinate, null));
    }

    @Test
    void downloadJarPomPackaging() {
        var coordinate = new MavenCoordinate("group", "artifact", "version", "pom");
        coordinate.setMavenRepos(new ArrayList<>(Collections.singletonList("repo")));
        var resolver = new MavenCoordinate.MavenResolver();

        assertThrows(MissingArtifactException.class, () -> resolver.downloadArtifact(coordinate, null));
    }

    @Test
    void downloadJarWrongPackaging() throws MissingArtifactException {
        var coordinate = new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.30", "wrongPackagingType");
        var resolver = new MavenCoordinate.MavenResolver();

        assertNotNull(resolver.downloadArtifact(coordinate, MavenUtilities.MAVEN_CENTRAL_REPO));
    }
}