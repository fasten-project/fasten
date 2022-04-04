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

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.Revision;

public class MavenDependencyResolverDepthTest {

    private static final String BASE = "base:1";

    private Set<String> danglingGAVs;
    private MavenDependencyData data;
    private MavenDependencyResolver sut;

    private ResolverConfig config;

    @BeforeEach
    public void setup() {
        danglingGAVs = new HashSet<>();
        data = new MavenDependencyData();
        sut = new MavenDependencyResolver();
        sut.setData(data);
        config = new ResolverConfig();
    }

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

    private void addDangling() {
        for (var gav : new HashSet<>(danglingGAVs)) {
            add(gav);
        }
    }

    private void assertDepSet(String... gavs) {
        addDangling();
        var baseParts = BASE.split(":");
        var base = String.format("%s:%s:%s", baseParts[0], baseParts[0], baseParts[1]);
        var actuals = sut.resolve(Set.of(base), config);
        var expecteds = Arrays.stream(gavs) //
                .map(gav -> gav.split(":")) //
                .map(parts -> new Revision(parts[0], parts[0], parts[1], new Timestamp(-1L))) //
                .collect(Collectors.toSet());
        assertEquals(expecteds, actuals);
    }
}