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
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Pom;

public class MavenDependencyResolverDepMgmtTest extends AbstractMavenDependencyResolverTest {

    @Test
    public void directDependency() {
        add($(BASE, "a:1"), "a");
        addMultiple("a:1");
        assertDepSet(BASE, "a:1");
    }

    @Test
    public void directDependencyWithVersion() {
        add($(BASE, "a:1"), "a:2");
        addMultiple("a:1", "a:2");
        assertDepSet(BASE, "a:2");
    }

    @Test
    public void transitiveDependency() {
        add($(BASE, "b:1"), "a:1");
        add($("a:1"), "b");
        addMultiple("b:1");
        assertDepSet(BASE, "a:1", "b:1");
    }

    @Test
    public void transitiveDependencyWithVersion() {
        add($(BASE, "b:1"), "a:1");
        add($("a:1"), "b:2");
        addMultiple("b:1", "b:2");
        assertDepSet(BASE, "a:1", "b:1");
    }

    private void add(Pom2 from, String... tos) {
        var pom = new Pom();
        if (!from.coord.contains(":")) {
            from.coord = from.coord + ":";
        }
        var parts = from.coord.split(":", -1);
        pom.groupId = parts[0];
        pom.artifactId = parts[0];
        pom.version = parts[1];

        for (var dm : from.depMgmt) {
            var partsTo = dm.split(":");
            var d = new Dependency(partsTo[0], partsTo[0], partsTo[1]);
            pom.dependencyManagement.add(d);
        }

        for (String to : tos) {
            if (!to.contains(":")) {
                to = to + ":";
            }
            var partsTo = to.split(":", -1);
            var d = new Dependency(partsTo[0], partsTo[0], partsTo[1]);
            pom.dependencies.add(d);
        }

        data.add(pom);
    }

    private void addMultiple(String... coords) {
        for (var coord : coords) {
            if (!coord.contains(":")) {
                throw new RuntimeException("coordinates need a version");
            }
            if (addedAV.contains(coord)) {
                throw new RuntimeException("Already added (G)AVs must not be contained in addMultiple");
            }
            add($(coord));
        }
    }

    private Pom2 $(String from, String... manageds) {
        addedAV.add(from);
        var pom = new Pom2();
        pom.coord = from;
        pom.depMgmt = Arrays.stream(manageds).collect(Collectors.toList());
        return pom;
    }

    private static class Pom2 {
        public String coord;
        public List<String> depMgmt = new LinkedList<>();
    }
}