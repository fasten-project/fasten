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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.VersionConstraint;

public class MavenDependentsResolverVersionRangeTest extends AbstractMavenDependentsResolverTest {

    @Test
    public void softConstraint() {
        add("dest:1");
        add("a:1", $("dest", "1"));
        assertDependents("dest:1", "a:1");
    }

    @Test
    public void hardConstraint() {
        add("dest:1");
        add("a:1", $("dest", "[1]"));
        assertDependents("dest:1", "a:1");
    }

    @Test
    public void simpleRange() {
        add("dest:1.2");
        add("a:1", $("dest", "[1,2]"));
        assertDependents("dest:1.2", "a:1");
    }

    @Test
    public void multiRange() {
        add("dest:1.2");
        add("a:1", $("dest", "[1.0]", "[1.1,1.3]"));
        assertDependents("dest:1.2", "a:1");
    }

    @Test
    public void transitiveMultiRange() {
        add("dest:1.2");
        add("a:1", $("dest", "[1.0]", "[1.1,1.3]"));
        add("b:1", $("a", "[1,2]"));
        assertDependents("dest:1.2", "a:1", "b:1");
    }

    private void add(String from, Dep... tos) {
        var pom = new Pom();
        var parts = from.split(":");
        pom.groupId = parts[0];
        pom.artifactId = parts[0];
        pom.version = parts[1];

        for (var to : tos) {
            var d = new Dependency(to.coord, to.coord, "0");
            d.versionConstraints = to.vcs;
            pom.dependencies.add(d);
        }

        data.add(pom);
    }

    private static Dep $(String coord, String... vcs) {
        var dep = new Dep();
        dep.coord = coord;
        dep.vcs = Arrays.stream(vcs) //
                .map(VersionConstraint::init) //
                .collect(Collectors.toSet());
        return dep;
    }

    private static class Dep {
        public String coord;
        public Set<VersionConstraint> vcs;
    }
}