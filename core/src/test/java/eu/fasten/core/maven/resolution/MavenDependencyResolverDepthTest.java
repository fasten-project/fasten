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

import static eu.fasten.core.maven.resolution.ResolverDepth.DIRECT;
import static eu.fasten.core.maven.resolution.ResolverDepth.TRANSITIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;

import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Pom;

public class MavenDependencyResolverDepthTest extends AbstractMavenDependencyResolverTest {

    @Test
    public void defaultIsTransitive() {
        assertEquals(TRANSITIVE, config.depth);
    }

    @Test
    public void directDependency() {
        add(BASE, "a:1");
        assertDepSet(BASE, "a:1");
    }

    @Test
    public void transitiveDependency() {
        add(BASE, "a:1");
        add("a:1", "b:1");
        assertDepSet(BASE, "a:1", "b:1");
    }

    @Test
    public void onlyDirectDependency() {
        config.depth = DIRECT;
        add(BASE, "a:1");
        assertDepSet(BASE, "a:1");
    }

    @Test
    public void onlyDirectDependencyButTransitiveExists() {
        config.depth = DIRECT;
        add(BASE, "a:1");
        add("a:1", "b:1");
        assertDepSet(BASE, "a:1");
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