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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.Revision;

public class MavenDependentsResolverIncludeOptionalTest {

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
    public void disabledByDefault() {
        assertFalse(config.alwaysIncludeOptional);
    }

    @Test
    public void ifUnsetIncludeDirectOptionalDeps() {
        add("a:1", opt(DEST));
        assertDependents(DEST, "a:1");
    }

    @Test
    public void ifUnsetDoNotIncludeTransitiveOptionalDeps() {
        add("a:1", m("b:1"));
        add("b:1", opt(DEST));
        assertDependents(DEST, "b:1");
    }

    @Test
    public void whenSetIncludeDirectOptionalDeps() {
        config.alwaysIncludeOptional = true;
        add("a:1", opt(DEST));
        assertDependents(DEST, "a:1");
    }

    @Test
    public void whenSetIncludeTransitiveOptionalDeps() {
        config.alwaysIncludeOptional = true;
        add("a:1", m("b:1"));
        add("b:1", opt(DEST));
        assertDependents(DEST, "a:1", "b:1");
    }

    private void add(String from, Dep... tos) {
        var pom = new Pom();
        var parts = from.split(":");
        pom.groupId = parts[0];
        pom.artifactId = parts[0];
        pom.version = parts[1];

        for (var to : tos) {
            var partsTo = to.coord.split(":");
            var d = new Dependency(partsTo[0], partsTo[0], partsTo[1]);
            d.optional = to.isOptional;
            pom.dependencies.add(d);
        }

        data.add(pom);
    }

    private void assertDependents(String shortTarget, String... depSet) {
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

    private static Dep m(String coord) {
        var dep = new Dep();
        dep.coord = coord;
        dep.isOptional = false;
        return dep;
    }

    private static Dep opt(String coord) {
        var dep = new Dep();
        dep.coord = coord;
        dep.isOptional = true;
        return dep;
    }

    private static class Dep {
        public String coord;
        public boolean isOptional;
    }
}