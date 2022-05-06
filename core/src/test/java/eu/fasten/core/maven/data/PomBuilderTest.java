/*
 * Copyright 2022 Delft University of Technology
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
package eu.fasten.core.maven.data;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.data.Constants;

public class PomBuilderTest {

    private static final Dependency SOME_DEP = new Dependency("g", "a", "1");
    private static final Dependency SOME_DEP2 = new Dependency("g2", "a2", "2");

    private PomBuilder sut;

    @BeforeEach
    public void setup() {
        sut = new PomBuilder();
    }

    @Test
    public void defaults() {
        var pom = sut.pom();

        assertEquals(0L, pom.id);
        assertEquals(Constants.mvnForge, pom.forge);

        assertNull(pom.artifactId);
        assertNull(pom.groupId);
        assertNull(pom.packagingType);
        assertNull(pom.version);

        assertNull(pom.parentCoordinate);

        assertEquals(-1L, pom.releaseDate);
        assertNull(pom.projectName);

        assertEquals(Set.of(), pom.dependencies);
        assertEquals(Set.of(), pom.dependencyManagement);

        assertNull(pom.repoUrl);
        assertNull(pom.commitTag);
        assertNull(pom.sourcesUrl);
        assertNull(pom.artifactRepository);
    }

    @Test
    public void nonDefaults() {

        sut.groupId = "g";
        sut.artifactId = "a";
        sut.packagingType = "pt";
        sut.version = "1.2.3";

        sut.parentCoordinate = "pc";

        sut.releaseDate = 234;
        sut.projectName = "pn";

        sut.dependencies.add(SOME_DEP);
        sut.dependencyManagement.add(SOME_DEP2);

        sut.repoUrl = "ru";
        sut.commitTag = "ct";
        sut.sourcesUrl = "su";
        sut.artifactRepository = "ar";

        var pom = sut.pom();

        assertEquals(0L, pom.id);
        assertEquals(Constants.mvnForge, pom.forge);

        assertEquals("a", pom.artifactId);
        assertEquals("g", pom.groupId);
        assertEquals("pt", pom.packagingType);
        assertEquals("1.2.3", pom.version);

        assertEquals("pc", pom.parentCoordinate);

        assertEquals(234L, pom.releaseDate);
        assertEquals("pn", pom.projectName);

        assertEquals(Set.of(SOME_DEP), pom.dependencies);
        assertEquals(Set.of(SOME_DEP2), pom.dependencyManagement);

        assertEquals("ru", pom.repoUrl);
        assertEquals("ct", pom.commitTag);
        assertEquals("su", pom.sourcesUrl);
        assertEquals("ar", pom.artifactRepository);
    }

    @Test
    public void setGroupId() {
        var pom = sut.groupId("g").pom();
        assertEquals("g", pom.groupId);
    }

    @Test
    public void setArtifactId() {
        var pom = sut.artifactId("a").pom();
        assertEquals("a", pom.artifactId);
    }

    @Test
    public void setPackagingType() {
        var pom = sut.packagingType("jar").pom();
        assertEquals("jar", pom.packagingType);
    }

    @Test
    public void setVersion() {
        var pom = sut.version("1").pom();
        assertEquals("1", pom.version);
    }

    @Test
    public void setParentCoordinate() {
        var pom = sut.parentCoordinate("p").pom();
        assertEquals("p", pom.parentCoordinate);
    }

    @Test
    public void setReleaseDate() {
        var pom = sut.releaseDate(123L).pom();
        assertEquals(123L, pom.releaseDate);
    }

    @Test
    public void setProjectName() {
        var pom = sut.projectName("pn").pom();
        assertEquals("pn", pom.projectName);
    }

    @Test
    public void addDependency() {
        var pom = sut.addDependency(SOME_DEP).pom();
        assertEquals(Set.of(SOME_DEP), pom.dependencies);
    }

    @Test
    public void setDependency() {
        var deps = new LinkedHashSet<Dependency>();
        deps.add(SOME_DEP);
        var pom = sut.setDependencies(deps).pom();
        assertEquals(Set.of(SOME_DEP), pom.dependencies);
    }

    @Test
    public void addDependencyManagement() {
        var pom = sut.addDependencyManagement(SOME_DEP).pom();
        assertEquals(Set.of(SOME_DEP), pom.dependencyManagement);
    }

    @Test
    public void setDependencyManagement() {
        var deps = new LinkedHashSet<Dependency>();
        deps.add(SOME_DEP);
        var pom = sut.setDependencyManagement(deps).pom();
        assertEquals(Set.of(SOME_DEP), pom.dependencyManagement);
    }

    @Test
    public void setRepoUrl() {
        var pom = sut.repoUrl("r").pom();
        assertEquals("r", pom.repoUrl);
    }

    @Test
    public void setCommitTag() {
        var pom = sut.commitTag("p").pom();
        assertEquals("p", pom.commitTag);
    }

    @Test
    public void setSourcesUrl() {
        var pom = sut.sourcesUrl("p").pom();
        assertEquals("p", pom.sourcesUrl);
    }

    @Test
    public void setArtifactRepository() {
        var pom = sut.artifactRepository("ar").pom();
        assertEquals("ar", pom.artifactRepository);
    }
}