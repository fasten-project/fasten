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
package eu.fasten.core.maven.data;

import static java.util.stream.IntStream.range;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import eu.fasten.core.json.ObjectMapperBuilder;

public class PomTest {

    @Test
    public void defaults() {
        var sut = pb().pom();
        assertNull(sut.artifactId);
        assertNull(sut.artifactRepository);
        assertNull(sut.commitTag);
        assertNotNull(sut.dependencies);
        assertNotNull(sut.dependencyManagement);
        assertEquals("mvn", sut.forge);
        assertNull(sut.groupId);
        assertNull(sut.packagingType);
        assertNull(sut.parentCoordinate);
        assertNull(sut.projectName);
        assertEquals(-1L, sut.releaseDate);
        assertNull(sut.repoUrl);
        assertNull(sut.sourcesUrl);
        assertNull(sut.version);
    }

    @Test
    public void equalityDefault() {
        var a = pb().pom();
        var b = pb().pom();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityNonDefault() {
        var a = pbX().pom();
        var b = pbX().pom();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffArtifact() {
        var a = pb().pom();
        var b = pb().artifactId("a").pom();

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffArtifactRepo() {
        var a = pb().pom();
        var b = pb().artifactRepository("ar").pom();

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffCommitTag() {
        var a = pb().pom();
        var b = pb().commitTag("c").pom();

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffDep() {
        var a = pb().pom();
        var b = pb().addDependency(someDependency("d")).pom();

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffDepMgmt() {
        var a = pb().pom();
        var b = pb().addDependencyManagement(someDependency("e")).pom();

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffGroup() {
        var a = pb().pom();
        var b = pb().groupId("g").pom();

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffPackaging() {
        var a = pb().pom();
        var b = pb().packagingType("t").pom();

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffParent() {
        var a = pb().pom();
        var b = pb().parentCoordinate("p").pom();

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffName() {
        var a = pb().pom();
        var b = pb().projectName("p").pom();

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffReleaseDate() {
        var a = pb().pom();
        var b = pb().releaseDate(123).pom();

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffRepoUrl() {
        var a = pb().pom();
        var b = pb().repoUrl("r").pom();

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffSourcesUrl() {
        var a = pb().pom();
        var b = pb().sourcesUrl("s").pom();

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffVersion() {
        var a = pb().pom();
        var b = pb().version("v").pom();

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hasToString() {
        var actual = pb().pom().toString();

        assertTrue(actual.contains(Pom.class.getSimpleName()));
        assertTrue(actual.contains("\n"));
        assertTrue(actual.split("\n")[0].contains("@"));
        assertTrue(actual.contains("artifactId"));
    }

    @Test
    public void canBeCloned() {
        var a = pbX().pom();
        var b = a.clone().pom();
        assertEquals(a, b);
        assertNotSame(a, b);

        assertEquals(a.dependencies, b.dependencies);
        assertNotSame(a.dependencies, b.dependencies);

        assertEquals(a.dependencyManagement, b.dependencyManagement);
        assertNotSame(a.dependencyManagement, b.dependencyManagement);
    }

    @Test
    public void dependencyOrderIsPreserved() throws JsonProcessingException {
        var pb = new PomBuilder();
        range(0, 100).forEach(num -> {
            pb.dependencies.add(new Dependency("g", "a", String.valueOf(num)));
        });
        var sut = pb.pom();

        var check = 0;
        for (var d : sut.dependencies) {
            var v = assertSingleVersionConstraint(d);
            assertEquals(check++, Integer.valueOf(v));
        }

        check = 0;
        for (var d : jsonRoundtrip(sut).dependencies) {
            var v = assertSingleVersionConstraint(d);
            assertEquals(check++, Integer.valueOf(v));
        }
    }

    @Test
    public void toCoordinate() {
        var actual = pbX().pom().toCoordinate();
        var expected = "g:a:h:n";
        assertEquals(expected, actual);
    }

    @Test
    public void toGAV() {
        var actual = pbX().pom().toGAV();
        var expected = new GAV("g", "a", "n");
        assertEquals(expected, actual);
    }

    @Test
    public void toGAVIsStable() {
        var a = pbX().pom().toGAV();
        var b = pbX().pom().toGAV();
        assertSame(a, b);
    }

    @Test
    public void toGA() {
        var actual = pbX().pom().toGA();
        var expected = new GA("g", "a");
        assertEquals(expected, actual);
    }

    @Test
    public void toGAIsStable() {
        var a = pbX().pom().toGA();
        var b = pbX().pom().toGA();
        assertSame(a, b);
    }

    @Test
    public void toProduct() {
        var actual = pbX().pom().toProduct();
        var expected = new MavenProduct("g", "a");
        assertEquals(expected, actual);
    }

    @Test
    public void toRevision() {
        var actual = pbX().pom().toRevision();
        var expected = new Revision("g", "a", "n", new Timestamp(123));
        assertEquals(expected, actual);
    }

    private static PomBuilder pb() {
        return new PomBuilder();
    }

    private static PomBuilder pbX() {
        var pb = new PomBuilder();
        pb.artifactId = "a";
        pb.artifactRepository = "b";
        pb.commitTag = "c";
        pb.dependencies.add(someDependency("d"));
        pb.dependencyManagement.add(someDependency("e"));
        pb.groupId = "g";
        pb.packagingType = "h";
        pb.parentCoordinate = "i";
        pb.projectName = "j";
        pb.releaseDate = 123;
        pb.repoUrl = "k";
        pb.sourcesUrl = "m";
        pb.version = "n";
        return pb;
    }

    private static Dependency someDependency(String name) {
        return new Dependency("dep", name, "0.0.1");
    }

    private static Pom jsonRoundtrip(Pom sut) throws JsonProcessingException, JsonMappingException {
        var om = new ObjectMapperBuilder().build();
        var json = om.writeValueAsString(sut);
        // make sure custom serializers are registered
        assertTrue(json.contains("\"v\":[\"0\"]"));
        var sut2 = om.readValue(json, Pom.class);
        assertEquals(sut, sut2);
        return sut2;
    }

    private static String assertSingleVersionConstraint(Dependency d) {
        var cs = d.getVersionConstraints();
        assertEquals(1, cs.size());
        return cs.iterator().next().getSpec();
    }
}