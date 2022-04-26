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

import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.VersionConstraint;

public class MavenDependencyResolverVersionRangeTest extends AbstractMavenDependencyResolverTest {

    // PLEASE NOTE: This behavior is a workaround until we support proper dependency
    // resolution. The approach is to select the most recent match in the "closest"
    // definition, which is the 20/80 solution, but not what Maven is doing.

    @Test
    public void directDependencyIncl() {
        add(BASE, "x:[1.1-1.2]");
        addXs();

        assertResolution(BASE, "x:1.2.0");
    }

    @Test
    public void directDependencyExcl() {
        add(BASE, "x:[1.1-1.2)");
        addXs();

        assertResolution(BASE, "x:1.1.9");
    }

    @Test
    public void directDependencyMultiRange() {
        add(BASE, "x:[1.1-1.2],[1.7-1.8],[1.3-1.4]");
        addXs();

        assertResolution(BASE, "x:1.8.0");
    }

    @Test
    public void transDependencyIncl() {
        add(BASE, "a:1");
        add("a:1", "x:[1.1-1.2]");
        addXs();

        assertResolution(BASE, "a:1", "x:1.2.0");
    }

    @Test
    public void transDependencyExcl() {
        add(BASE, "a:1");
        add("a:1", "x:[1.1.0-1.2.0)");
        addXs();

        assertResolution(BASE, "a:1", "x:1.1.9");
    }

    @Test
    public void nonRangeOverridesLaterRange() {
        add(BASE, "a:1", "b:1");
        add("a:1", "x:2");
        add("x:2");
        add("b:1", "x:[1.1-1.2]");
        addXs();

        assertResolution(BASE, "a:1", "b:1", "x:2");
    }

    @Test
    public void rangeExcludesLaterNonRanges() {
        add(BASE, "a:1", "b:1");
        add("a:1", "x:[1.1-1.2]");
        add("b:1", "x:2");
        add("x:2");
        addXs();

        assertResolution(BASE, "a:1", "b:1", "x:1.2.0");
    }

    @Test
    public void closerRangeWins() {
        add(BASE, "a:1", "b:1");
        add("a:1", "x:[1.0-1.1]");
        add("b:1", "x:[1.2-1.3]");
        addXs();

        assertResolution(BASE, "a:1", "b:1", "x:1.1");
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
                var vcs = Arrays.stream(partsTo[1].split(",")) //
                        .map(vc -> vc.replace('-', ',')) //
                        .map(VersionConstraint::new) //
                        .collect(Collectors.toSet());
                d.setVersionConstraints(vcs);
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
}