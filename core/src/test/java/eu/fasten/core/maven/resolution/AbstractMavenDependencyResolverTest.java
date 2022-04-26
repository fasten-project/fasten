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
import static org.junit.Assert.fail;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;

import eu.fasten.core.maven.data.ResolvedRevision;

public class AbstractMavenDependencyResolverTest {

    protected static final String BASE = "base:1";

    protected Set<String> danglingGAVs;
    protected Set<String> addedAV;

    protected MavenDependencyData data;
    protected MavenDependencyResolver sut;
    protected ResolverConfig config;

    @BeforeEach
    public void setupAbstract() {
        addedAV = new HashSet<>();
        data = new MavenDependencyData();
        sut = new MavenDependencyResolver();
        sut.setData(data);
        config = new ResolverConfig();
        danglingGAVs = new HashSet<>();
    }

    protected void addDangling() {
        // override when needed
    }

    protected Set<ResolvedRevision> assertResolution(String... gavs) {
        addDangling();
        var baseParts = gavs[0].split(":");
        var base = String.format("%s:%s:%s", baseParts[0], baseParts[0], baseParts[1]);
        var actuals = sut.resolve(Set.of(base), config);
        var expecteds = Arrays.stream(gavs) //
                .map(gav -> gav.split(":")) //
                .map(parts -> new ResolvedRevision(-1, parts[0], parts[0], parts[1], new Timestamp(-1L), COMPILE)) //
                .collect(Collectors.toSet());

        assertEquals(expecteds, actuals);
        return actuals;
    }

    private static void assertEquals(Set<ResolvedRevision> expecteds,
                                    Set<ResolvedRevision> actuals) {
        if (!expecteds.equals(actuals)) {
            var sb = new StringBuilder();
            sb.append("Expected:\n");
            for (var e : sort(expecteds)) {
                sb.append("- ").append(e).append("\n");
            }
            sb.append("But was:\n");
            for (var a : sort(actuals)) {
                sb.append("- ").append(a).append("\n");
            }
            fail(sb.toString());
        }
    }

    private static Set<String> sort(Set<ResolvedRevision> rs) {
        return rs.stream() //
                .map(r -> String.format("%s:%s:%s", r.getGroupId(), r.getArtifactId(), r.version)) //
                .collect(Collectors.toCollection(TreeSet::new));
    }
}