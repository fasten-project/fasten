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
package eu.fasten.core.maven.resolution;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.PomAnalysisResult;
import eu.fasten.core.maven.data.VersionConstraint;

public class MavenDependencyGraphTest {

    private static final long SOME_TIME = 123L;
    private MavenDependencyGraph sut;

    @BeforeEach
    public void setup() {
        sut = new MavenDependencyGraph();
    }

    @Test
    public void returnsNullIfEmpty() {
        var actual = find(SOME_TIME, "a:b", "1");
        assertNull(actual);
    }

    @Test
    public void returnsNullIfNoMatch() {
        addPom("b", "c", "1", SOME_TIME);
        var actual = find(SOME_TIME, "a:b", "1");
        assertNull(actual);
    }

    @Test
    public void returnsNullIfGroupMismatch() {
        addPom("a", "b", "1", SOME_TIME);
        var actual = find(SOME_TIME, "x:b", "1");
        assertNull(actual);
    }

    @Test
    public void returnsNullIfArtifactMismatch() {
        addPom("a", "b", "1", SOME_TIME);
        var actual = find(SOME_TIME, "a:x", "1");
        assertNull(actual);
    }

    @Test
    public void returnsNullIfVersionMismatch() {
        addPom("a", "b", "1", SOME_TIME);
        var actual = find(SOME_TIME, "a:b", "2");
        assertNull(actual);
    }

    @Test
    public void findsExactMatch() {
        var expected = addPom("a", "b", "1", SOME_TIME);
        var actual = find(SOME_TIME, "a:b", "1");
        assertEquals(expected, actual);
    }

    @Test
    public void findsExactMatchAmongMultipleVersions() {
        var expected = addPom("a", "b", "1", SOME_TIME);
        addPom("a", "b", "2", SOME_TIME);
        var actual = find(SOME_TIME, "a:b", "1");
        assertEquals(expected, actual);
    }

    @Test
    public void returnsNullIfExactMatchIsTooNew() {
        addPom("a", "b", "1", SOME_TIME + 1);
        var actual = find(SOME_TIME, "a:b", "1");
        assertNull(actual);
    }

    @Test
    public void cannotAddTheSamePomTwice() {
        addPom("a", "b", "1", SOME_TIME);
        var e = assertThrows(IllegalArgumentException.class, () -> {
            addPom("a", "b", "1", SOME_TIME);
        });
        assertEquals("Coordinate a:b:1 exists", e.getMessage());
    }

    @Test
    public void returnNullIfNoMatch_hardConstraint() {
        addPom("a", "b", "1.1", SOME_TIME);
        addPom("a", "b", "1.2", SOME_TIME);
        addPom("a", "b", "1.3", SOME_TIME);
        var actual = find(SOME_TIME, "a:b", "[1]");
        assertNull(actual);
    }

    @Test
    public void returnNullIfNoMatch_versionRange() {
        addPom("a", "b", "1.1", SOME_TIME);
        addPom("a", "b", "1.2", SOME_TIME);
        addPom("a", "b", "1.3", SOME_TIME);
        var actual = find(SOME_TIME, "a:b", "[1,1.1)");
        assertNull(actual);
    }

    @Test
    public void findsHighestMatch() {
        addPom("a", "b", "1.1", SOME_TIME);
        var expected = addPom("a", "b", "1.2", SOME_TIME);
        addPom("a", "b", "1.3", SOME_TIME);
        var actual = find(SOME_TIME, "a:b", "[1.1,1.3)");
        assertEquals(expected, actual);
    }

    @Test
    public void findsHighestMatchWithUnorderedConstraints() {
        addPom("a", "b", "1.1", SOME_TIME);
        addPom("a", "b", "1.2", SOME_TIME);
        addPom("a", "b", "1.3", SOME_TIME);
        addPom("a", "b", "1.4", SOME_TIME);
        var expected = addPom("a", "b", "1.5", SOME_TIME);
        addPom("a", "b", "1.6", SOME_TIME);
        var actual = find(SOME_TIME, "a:b", "[1.2,1.3]", "[1.4,1.5]", "[0,1.2]");
        assertEquals(expected, actual);
    }

    @Test
    public void findsNewestHighestMatch() {
        addPom("a", "b", "1.1", SOME_TIME - 1);
        var expected = addPom("a", "b", "1.2", SOME_TIME);
        addPom("a", "b", "1.3", SOME_TIME + 1);
        var actual = find(SOME_TIME, "a:b", "[1,2]");
        assertEquals(expected, actual);
    }

    private PomAnalysisResult find(long resolveAt, String ga, String... specs) {
        var vcs = Arrays.stream(specs) //
                .map(VersionConstraint::new) //
                .collect(Collectors.toSet());
        return sut.find(ga, vcs, resolveAt);
    }

    private PomAnalysisResult addPom(String g, String a, String v, long releaseDate) {
        var pom = new PomAnalysisResult();
        pom.groupId = g;
        pom.artifactId = a;
        pom.version = v;
        pom.releaseDate = releaseDate;

        sut.add(pom);
        return pom;
    }
}