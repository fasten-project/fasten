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

package eu.fasten.analyzer.javacgopal.version3.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MavenCoordinateTest {

    @Test
    void constructorTest() {
        MavenCoordinate coordinate1 = new MavenCoordinate("group", "artifact", "version");
        MavenCoordinate coordinate2 = new MavenCoordinate(new ArrayList<>(Collections
                .singletonList("repo")), "group", "artifact", "version");

        assertEquals("group", coordinate1.getGroupID());
        assertEquals(coordinate1.getGroupID(), coordinate2.getGroupID());

        assertEquals("artifact", coordinate1.getArtifactID());
        assertEquals(coordinate1.getArtifactID(), coordinate2.getArtifactID());

        assertEquals("version", coordinate1.getVersionConstraint());
        assertEquals(coordinate1.getVersionConstraint(), coordinate2.getVersionConstraint());

        assertEquals(new ArrayList<>(Collections
                        .singletonList("https://repo.maven.apache.org/maven2/")),
                coordinate1.getMavenRepos());
        assertNotEquals(coordinate1.getMavenRepos(), coordinate2.getMavenRepos());

        var repos = new ArrayList<>(Collections.singletonList("repo"));
        coordinate1.setMavenRepos(repos);

        assertEquals(coordinate1.getMavenRepos(), coordinate2.getMavenRepos());
    }

    @Test
    void fromString() {
        var coordinate = MavenCoordinate.fromString("GroupID:ArtifactID:Version");

        assertEquals("GroupID", coordinate.getGroupID());
        assertEquals("ArtifactID", coordinate.getArtifactID());
        assertEquals("Version", coordinate.getVersionConstraint());
    }

    @Test
    void getProduct() {
        var coordinate = MavenCoordinate.fromString("GroupID:ArtifactID:Version");
        assertEquals("GroupID:ArtifactID", coordinate.getProduct());
    }

    @Test
    void getCoordinate() {
        var coordinate = MavenCoordinate.fromString("GroupID:ArtifactID:Version");
        assertEquals("GroupID:ArtifactID:Version", coordinate.getCoordinate());
    }

    @Test
    void toURL() {
        var coordinate = MavenCoordinate.fromString("GroupID:ArtifactID:Version");
        assertEquals("repo/GroupID/ArtifactID/Version", coordinate.toURL("repo/"));
    }

    @Test
    void toJarUrl() {
        var coordinate = MavenCoordinate.fromString("GroupID:ArtifactID:Version");
        assertEquals("repo/GroupID/ArtifactID/Version/ArtifactID-Version.jar",
                coordinate.toJarUrl("repo/"));
    }

    @Test
    void toPomUrl() {
        var coordinate = MavenCoordinate.fromString("GroupID:ArtifactID:Version");
        assertEquals("repo/GroupID/ArtifactID/Version/ArtifactID-Version.pom",
                coordinate.toPomUrl("repo/"));
    }

    @Test
    public void testResolveDependenciesWithProfile() throws IOException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("testpom.txt"))
                .getFile()).getAbsolutePath();

        FileInputStream inputStream = new FileInputStream(file);
        var pomText = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

        MavenCoordinate.MavenResolver resolver = Mockito.mock(MavenCoordinate.MavenResolver.class);
        Mockito.when(resolver.downloadPom(Mockito.any())).thenReturn(java.util.Optional.of(pomText));
        Mockito.doCallRealMethod().when(resolver).getDependencies(Mockito.any());

        var deps = resolver.getDependencies(new MavenCoordinate("coordinate", "artifact", "version"));
        assertNotNull(deps);
        assertEquals("mvn", deps.get(0).get(0).forge);
        assertEquals("mvn", deps.get(0).get(1).forge);
        assertEquals("org.slf4j:slf4j-simple", deps.get(0).get(0).product);
        assertEquals("org.dom4j:dom4j", deps.get(0).get(1).product);
        assertEquals("[1.7.30]", deps.get(0).get(0).constraints.get(0).toString());
        assertEquals("[*]", deps.get(0).get(1).constraints.get(0).toString());
    }
}