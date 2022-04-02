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

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.data.VersionConstraint;

public class MavenDependencyResolverVersionRangeTest {

    // PLEASE NOTE: This behavior is a workaround until we support proper dependency
    // resolution. The approach is to select the most recent match in the "closest"
    // definition, which is the 20/80 solution, but not what Maven is doing.

    private static final String BASE = "base:1";

    private MavenDependencyData data;
    private MavenDependencyResolver sut;

    @BeforeEach
    public void setup() {
        data = new MavenDependencyData();
        sut = new MavenDependencyResolver();
        sut.setData(data);
    }

    @Test
    public void directDependencyIncl() {
        add(BASE, "x:[1.1-1.2]");
        addXs();

        assertDepSet(BASE, "x:1.2.0");
    }

    @Test
    public void directDependencyExcl() {
        add(BASE, "x:[1.1-1.2)");
        addXs();

        assertDepSet(BASE, "x:1.1.9");
    }

    @Test
    public void directDependencyMultiRange() {
        add(BASE, "x:[1.1-1.2],[1.7-1.8],[1.3-1.4]");
        addXs();

        assertDepSet(BASE, "x:1.8.0");
    }

    @Test
    public void transDependencyIncl() {
        add(BASE, "a:1");
        add("a:1", "x:[1.1-1.2]");
        addXs();

        assertDepSet(BASE, "a:1", "x:1.2.0");
    }

    @Test
    public void transDependencyExcl() {
        add(BASE, "a:1");
        add("a:1", "x:[1.1.0-1.2.0)");
        addXs();

        assertDepSet(BASE, "a:1", "x:1.1.9");
    }

    @Test
    public void nonRangeOverridesLaterRange() {
        add(BASE, "a:1", "b:1");
        add("a:1", "x:2");
        add("x:2");
        add("b:1", "x:[1.1-1.2]");
        addXs();

        assertDepSet(BASE, "a:1", "b:1", "x:2");
    }

    @Test
    public void rangeExcludesLaterNonRanges() {
        add(BASE, "a:1", "b:1");
        add("a:1", "x:[1.1-1.2]");
        add("b:1", "x:2");
        add("x:2");
        addXs();

        assertDepSet(BASE, "a:1", "b:1", "x:1.2.0");
    }

    @Test
    public void closerRangeWins() {
        add(BASE, "a:1", "b:1");
        add("a:1", "x:[1.0-1.1]");
        add("b:1", "x:[1.2-1.3]");
        addXs();

        assertDepSet(BASE, "a:1", "b:1", "x:1.1");
    }

    private void add(String from, String... tos) {
        var pom = new Pom();
        var parts = from.split(":");
        pom.groupId = parts[0];
        pom.artifactId = parts[0];
        pom.version = parts[1];

        for (var to : tos) {
            var partsTo = to.split(":");
            var d = new Dependency(partsTo[0], partsTo[0], partsTo[1]);

            boolean isConstraint = partsTo[1].contains("[") || partsTo[1].contains("(");
            if (isConstraint) {
                d.versionConstraints.clear();

                var vcs = Arrays.stream(partsTo[1].split(",")) //
                        .map(vc -> vc.replace('-', ',')) //
                        .map(VersionConstraint::new) //
                        .collect(Collectors.toSet());

                d.versionConstraints.addAll(vcs);

            }
            pom.dependencies.add(d);
        }

        data.add(pom);
    }

    private void addXs() {
        for (var i = 0; i < 3; i++) {
            for (var j = 0; j < 10; j++) {
                for (var k = 0; k < 10; k++) {
                    add(format("x:%d.%d.%d", i, j, k));
                }
            }
        }
        add("x:3.0.0");
    }

    private void assertDepSet(String... gavs) {
        var baseParts = BASE.split(":");
        var base = String.format("%s:%s:%s", baseParts[0], baseParts[0], baseParts[1]);
        var actuals = sut.resolve(Set.of(base), new ResolverConfig());
        var expecteds = Arrays.stream(gavs) //
                .map(gav -> gav.split(":")) //
                .map(parts -> new Revision(parts[0], parts[0], parts[1], new Timestamp(-1L))) //
                .collect(Collectors.toSet());
        assertEquals(expecteds, actuals);
    }
}