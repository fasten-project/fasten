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

import java.util.HashSet;

import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Pom;

public class MavenDependencyResolverMediationTest extends AbstractMavenDependencyResolverTest {

    @Test
    public void directDependency() {
        add(BASE, "b:1");
        assertResolution(BASE, "b:1");
    }

    @Test
    public void transitiveDependency() {
        add(BASE, "b:1");
        add("b:1", "c:1");
        assertResolution(BASE, "b:1", "c:1");
    }

    @Test
    public void mediationDirectDep1() {
        add(BASE, "c:1", "b:1");
        add("b:1", "c:2");
        assertResolution(BASE, "b:1", "c:1");
    }

    @Test
    public void mediationDirectDep2() {
        add(BASE, "b:1", "c:1");
        add("b:1", "c:2");
        assertResolution(BASE, "b:1", "c:1");
    }

    @Test
    public void mediationSameChildLevel1() {
        add(BASE, "b:1", "c:1");
        add("b:1", "d:1");
        add("c:1", "d:2");
        assertResolution(BASE, "b:1", "c:1", "d:1");
    }

    @Test
    public void mediationSameChildLevel2() {
        add(BASE, "c:1", "b:1");
        add("b:1", "d:1");
        add("c:1", "d:2");
        assertResolution(BASE, "b:1", "c:1", "d:2");
    }

    @Test
    public void mediationCloserChildLevel1() {
        add(BASE, "b:1", "c:1");
        add("b:1", "x:1");
        add("c:1", "cc:1");
        add("cc:1", "x:2");
        assertResolution(BASE, "b:1", "x:1", "c:1", "cc:1");
    }

    @Test
    public void mediationCloserChildLevel2() {
        add(BASE, "c:1", "b:1");
        add("b:1", "x:1");
        add("c:1", "cc:1");
        add("cc:1", "x:2");
        assertResolution(BASE, "c:1", "cc:1", "b:1", "x:1");
    }

    @Test
    public void cycleDirect() {
        add(BASE, "a:1");
        add("a:1", BASE);
        danglingGAVs.clear();
        assertResolution(BASE, "a:1");
    }

    @Test
    public void cycleTransitive() {
        add(BASE, "a:1");
        add("a:1", "b:1");
        add("b:1", BASE);
        danglingGAVs.clear();
        assertResolution(BASE, "a:1", "b:1");
    }

    @Test
    public void cycleBranches() {
        add(BASE, "a:1", "b:1");
        add("a:1", "b:1");
        add("b:1", "a:1");
        assertResolution(BASE, "a:1", "b:1");
    }

    private void add(String from, String... tos) {
        danglingGAVs.remove(from);
        var pom = new Pom();
        var parts = from.split(":");
        pom.groupId = parts[0];
        pom.artifactId = parts[0];
        pom.version = parts[1];

        for (String to : tos) {
            danglingGAVs.add(to);
            var partsTo = to.split(":");
            var d = new Dependency(partsTo[0], partsTo[0], partsTo[1]);
            pom.dependencies.add(d);
        }

        data.add(pom);
    }

    protected void addDangling() {
        for (var gav : new HashSet<>(danglingGAVs)) {
            add(gav);
        }
    }
}