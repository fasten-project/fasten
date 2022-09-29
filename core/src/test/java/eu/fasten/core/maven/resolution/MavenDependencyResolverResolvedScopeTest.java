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

import static eu.fasten.core.maven.data.Scope.COMPILE;
import static eu.fasten.core.maven.data.Scope.PROVIDED;
import static eu.fasten.core.maven.data.Scope.RUNTIME;
import static eu.fasten.core.maven.data.Scope.SYSTEM;
import static eu.fasten.core.maven.data.Scope.TEST;
import static eu.fasten.core.maven.data.VersionConstraint.parseVersionSpec;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.PomBuilder;
import eu.fasten.core.maven.data.Scope;

public class MavenDependencyResolverResolvedScopeTest extends AbstractMavenDependencyResolverTest {

    @BeforeEach
    public void setup() {
        add(BASE, //
                $("s:1", Scope.SYSTEM), //
                $("p:1", Scope.PROVIDED), //
                $("c:1", Scope.COMPILE), //
                $("r:1", Scope.RUNTIME), //
                $("t:1", Scope.TEST));

        add("s:1", //
                $("ss:1", Scope.SYSTEM), //
                $("sp:1", Scope.PROVIDED), //
                $("sc:1", Scope.COMPILE), //
                $("sr:1", Scope.RUNTIME), //
                $("st:1", Scope.TEST));

        add("p:1", //
                $("ps:1", Scope.SYSTEM), //
                $("pp:1", Scope.PROVIDED), //
                $("pc:1", Scope.COMPILE), //
                $("pr:1", Scope.RUNTIME), //
                $("pt:1", Scope.TEST));

        add("c:1", //
                $("cs:1", Scope.SYSTEM), //
                $("cp:1", Scope.PROVIDED), //
                $("cc:1", Scope.COMPILE), //
                $("cr:1", Scope.RUNTIME), //
                $("ct:1", Scope.TEST));

        add("r:1", //
                $("rs:1", Scope.SYSTEM), //
                $("rp:1", Scope.PROVIDED), //
                $("rc:1", Scope.COMPILE), //
                $("rr:1", Scope.RUNTIME), //
                $("rt:1", Scope.TEST));

        add("t:1", //
                $("ts:1", Scope.SYSTEM), //
                $("tp:1", Scope.PROVIDED), //
                $("tc:1", Scope.COMPILE), //
                $("tr:1", Scope.RUNTIME), //
                $("tt:1", Scope.TEST));
    }

    @Test
    public void doNotIncludeProvidedByDefault() {
        assertFalse(config.alwaysIncludeProvided);
    }

    @Test
    public void resolveCompile() {
        config.scope = Scope.COMPILE;
        assertScopedResolution(compile(BASE), //
                compile("c:1"), compile("cc:1"), system("cs:1"), //
                provided("p:1"), provided("pc:1"), provided("pr:1"), system("ps:1"), //
                system("s:1"));
    }

    @Test
    public void resolveRuntime() {
        config.scope = Scope.RUNTIME;
        assertScopedResolution(compile(BASE), //
                compile("c:1"), compile("cc:1"), runtime("cr:1"), //
                runtime("r:1"), runtime("rc:1"), runtime("rr:1"));
    }

    @Test
    public void resolveTest() {
        config.scope = Scope.TEST;
        assertScopedResolution(compile(BASE), //
                compile("c:1"), compile("cc:1"), runtime("cr:1"), system("cs:1"), //
                runtime("r:1"), runtime("rc:1"), runtime("rr:1"), system("rs:1"), //
                test("t:1"), test("tc:1"), test("tr:1"), system("ts:1"), //
                provided("p:1"), provided("pc:1"), provided("pr:1"), system("ps:1"), //
                system("s:1"));
    }

    @Test
    public void resolveCompileAlwaysProvided() {
        config.scope = Scope.COMPILE;
        config.alwaysIncludeProvided = true;
        assertScopedResolution(compile(BASE), //
                compile("c:1"), compile("cc:1"), system("cs:1"), /* + */ provided("cp:1"), //
                provided("p:1"), provided("pc:1"), provided("pr:1"), system("ps:1"), /* + */ provided("pp:1"), //
                system("s:1"));
    }

    @Test
    public void resolveRuntimeAlwaysProvided() {
        config.scope = Scope.RUNTIME;
        config.alwaysIncludeProvided = true;
        assertScopedResolution(compile(BASE), //
                compile("c:1"), compile("cc:1"), runtime("cr:1"), /* + */ provided("cp:1"), //
                runtime("r:1"), runtime("rc:1"), runtime("rr:1"), /* + */ provided("rp:1"), //
                /* + */ provided("p:1"), provided("pc:1"), provided("pr:1"), provided("pp:1"));
    }

    @Test
    public void resolveTestAlwaysProvided() {
        config.scope = Scope.TEST;
        config.alwaysIncludeProvided = true;
        assertScopedResolution(compile(BASE), //
                compile("c:1"), compile("cc:1"), runtime("cr:1"), system("cs:1"), /* + */ provided("cp:1"), //
                runtime("r:1"), runtime("rc:1"), runtime("rr:1"), system("rs:1"), /* + */ provided("rp:1"), //
                test("t:1"), test("tc:1"), test("tr:1"), system("ts:1"), /* + */ provided("tp:1"), //
                provided("p:1"), provided("pc:1"), provided("pr:1"), system("ps:1"), /* + */ provided("pp:1"), //
                system("s:1"));
    }

    private void add(String from, Dep... tos) {
        danglingGAVs.remove(from);
        var pb = new PomBuilder();
        var parts = from.split(":");
        pb.groupId = parts[0];
        pb.artifactId = parts[0];
        pb.version = parts[1];

        for (var to : tos) {
            danglingGAVs.add(to.coord);
            var partsTo = to.coord.split(":");
            var d = new Dependency(partsTo[0], partsTo[0], parseVersionSpec(partsTo[1]), Set.of(), to.scope, false,
                    "jar", "");
            pb.dependencies.add(d);
        }

        data.add(pb.pom());
    }

    @Override
    protected void addDangling() {
        for (var gav : new HashSet<>(danglingGAVs)) {
            add(gav);
        }
    }

    private void assertScopedResolution(Dep... deps) {
        var gavsSet = Arrays.stream(deps).map(d -> d.coord).collect(Collectors.toCollection(LinkedHashSet::new));
        var gavs = new String[gavsSet.size()];
        gavs = gavsSet.toArray(gavs);

        var actuals = assertResolution(gavs).stream() //
                .map(rr -> String.format("%s:%s:%s:%s", rr.getGroupId(), rr.getArtifactId(), rr.version, rr.scope)) //
                .collect(Collectors.toCollection(TreeSet::new));
        var expecteds = Arrays.stream(deps) //
                .skip(1) // remove target from expected set
                .map(d -> {
                    var parts = d.coord.split(":");
                    return String.format("%s:%s:%s:%s", parts[0], parts[0], parts[1], d.scope);
                }) //
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(expecteds, actuals);
    }

    protected static void assertEquals(TreeSet<String> expecteds, TreeSet<String> actuals) {
        if (!expecteds.equals(actuals)) {
            var sb = new StringBuilder();
            sb.append("Unexpected dependencies. Expected:\n");
            for (var e : expecteds) {
                sb.append("\t- ").append(e).append("\n");
            }
            sb.append("Was:\n");
            for (var a : actuals) {
                sb.append("\t- ").append(a).append("\n");
            }
            fail(sb.toString());
        }
    }

    private static Dep compile(String coord) {
        return $(coord, COMPILE);
    }

    private static Dep runtime(String coord) {
        return $(coord, RUNTIME);
    }

    private static Dep test(String coord) {
        return $(coord, TEST);
    }

    private static Dep provided(String coord) {
        return $(coord, PROVIDED);
    }

    private static Dep system(String coord) {
        return $(coord, SYSTEM);
    }

    private static Dep $(String coord, Scope scope) {
        var d = new Dep();
        d.coord = coord;
        d.scope = scope;
        return d;
    }

    private static class Dep {
        public String coord;
        public Scope scope;
    }
}