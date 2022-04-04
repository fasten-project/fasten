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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.Revision;

public class MavenDependentsResolverBasicTest {

    private static final String DEST = "dest:1";

    private MavenDependentsData data;
    private MavenDependentsResolver sut;

    private ResolverConfig config;

    @BeforeEach
    public void setup() {
        data = new MavenDependentsData();
        sut = new MavenDependentsResolver(data);
        config = new ResolverConfig();
        add(DEST);
    }

    @Test
    public void direct() {
        add("a:1", DEST);
        assertDependents(DEST, "a:1");
    }

    @Test
    public void transitive() {
        add("a:1", DEST);
        add("b:1", "a:1");
        assertDependents(DEST, "a:1", "b:1");
    }

    @Test
    public void multipleBranches() {
        add("a:1", DEST);
        add("b:1", DEST);
        assertDependents(DEST, "a:1", "b:1");
    }

    @Test
    public void multipleBranchesTransitive() {
        add("a:1", DEST);
        add("aa:1", "a:1");
        add("b:1", DEST);
        add("bb:1", "b:1");
        assertDependents(DEST, "a:1", "aa:1", "b:1", "bb:1");
    }

    @Test
    public void cycleDirect() {
        add("a:1", DEST);
        add(DEST, "a:1");
        assertDependents(DEST, "a:1");
    }

    @Test
    public void cycleTransitive() {
        add("a:1", DEST);
        add("b:1", "a:1");
        add(DEST, "b:1");
        assertDependents(DEST, "a:1", "b:1");
    }

    @Test
    public void throwsWhenDestCannotBeFound() {
        var e = assertThrows(MavenResolutionException.class, () -> {
            sut.resolve("non:existing:1", config);
        });
        assertEquals("Cannot find coordinate non:existing:1", e.getMessage());
    }

    @Test
    public void illegalStateDepNotFound() {
        add("a:1", DEST);
        add("b:1", "a:1");
        add(DEST, "b:1");
        assertDependents(DEST, "a:1", "b:1");
    }

    private void add(String from, String... tos) {
        var pom = new Pom();
        var parts = from.split(":");
        pom.groupId = parts[0];
        pom.artifactId = parts[0];
        pom.version = parts[1];

        for (String to : tos) {
            var partsTo = to.split(":");
            var d = new Dependency(partsTo[0], partsTo[0], partsTo[1]);
            pom.dependencies.add(d);
        }

        data.add(pom);
    }

    private void assertDependents(String shortTarget, String... depSet) {
        assertTrue(depSet.length > 0);
        var targetParts = shortTarget.split(":");
        var target = String.format("%s:%s:%s", targetParts[0], targetParts[0], targetParts[1]);
        var actuals = sut.resolve(target, config);
        var expecteds = Arrays.stream(depSet) //
                .map(gav -> gav.split(":")) //
                .map(parts -> new Revision(parts[0], parts[0], parts[1], new Timestamp(-1L))) //
                .collect(Collectors.toSet());

        if (!expecteds.equals(actuals)) {
            var sb = new StringBuilder();
            sb.append("Expected:\n");
            for (var e : expecteds) {
                sb.append("- ").append(e.groupId).append(":").append(e.artifactId).append(":").append(e.version)
                        .append("\n");
            }
            sb.append("But was:\n");
            for (var a : actuals) {
                sb.append("- ").append(a.groupId).append(":").append(a.artifactId).append(":").append(a.version)
                        .append("\n");
            }
            fail(sb.toString());
        }
    }
}