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

import static eu.fasten.core.maven.resolution.ResolverConfig.resolve;
import static java.lang.String.format;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.resolution.IMavenResolver;
import eu.fasten.core.maven.resolution.ResolverConfig;

public class IMavenResolverTest {

    private static final String GID = "g";
    private static final String AID = "a";
    private static final String VERSION = "1";
    private static final Revision REV = new Revision(GID, AID, VERSION, new Timestamp(1234567890000L));

    private static final ResolverConfig SOME_CONFIG = resolve().at(23456789);
    private static final ResolverConfig DEFAULT_CONFIG = new ResolverConfig();

    private TestMavenResolver sut;

    @BeforeEach
    public void setup() {
        sut = new TestMavenResolver();
    }

    @Test
    public void strWithConfig_dep() {
        sut.resolveDependencies(GID, AID, VERSION, SOME_CONFIG);
        assertDependencies(SOME_CONFIG, REV);
    }

    @Test
    public void strWithConfig_deps() {
        var gavs = Set.of(toGAV(REV));
        sut.resolveDependencies(gavs, SOME_CONFIG);
        assertDependencies(SOME_CONFIG, REV);
    }

    @Test
    public void strWithConfig_dpts() {
        sut.resolveDependents(GID, AID, VERSION, SOME_CONFIG);
        assertDependents(SOME_CONFIG);
    }

    @Test
    public void strNoConfig_dep() {
        sut.resolveDependencies(GID, AID, VERSION);
        assertDependencies(DEFAULT_CONFIG, REV);
    }

    @Test
    public void strNoConfig_deps() {
        var gavs = Set.of(toGAV(REV));
        sut.resolveDependencies(gavs);
        assertDependencies(DEFAULT_CONFIG, REV);
    }

    @Test
    public void strNoConfig_dpts() {
        sut.resolveDependents(GID, AID, VERSION);
        assertDependents(DEFAULT_CONFIG);
    }

    @Test
    public void revWithConfig_dep() {
        sut.resolveDependencies(REV, SOME_CONFIG);
        assertDependencies(SOME_CONFIG, REV);
    }

    @Test
    public void revWithConfig_deps() {
        sut.resolveDependenciesForRevisions(Set.of(REV), SOME_CONFIG);
        assertDependencies(SOME_CONFIG, REV);
    }

    @Test
    public void revWithConfig_dpts() {
        sut.resolveDependents(REV, SOME_CONFIG);
        assertDependents(SOME_CONFIG);
    }

    @Test
    public void revNoConfig_dep() {
        sut.resolveDependencies(REV);
        assertDependencies(DEFAULT_CONFIG, REV);
    }

    @Test
    public void revNoConfig_deps() {
        sut.resolveDependenciesForRevisions(Set.of(REV));
        assertDependencies(DEFAULT_CONFIG, REV);
    }

    @Test
    public void revNoConfig_dpts() {
        sut.resolveDependents(REV);
        assertDependents(DEFAULT_CONFIG);
    }

    private void assertDependents(ResolverConfig config) {
        assertEquals("dpts", sut.methodCalled);
        assertEquals(GID, sut.gid);
        assertEquals(AID, sut.aid);
        assertEquals(VERSION, sut.version);
        assertConfig(config);
    }

    private void assertDependencies(ResolverConfig config, Revision... revs) {
        var expectedGavs = Arrays.stream(revs) //
                .map(r -> toGAV(r)) //
                .collect(Collectors.toSet());

        assertEquals("deps", sut.methodCalled);
        assertEquals(expectedGavs, sut.gavs);
        assertConfig(config);
    }

    private void assertConfig(ResolverConfig config) {
        config.timestamp = (sut.config.timestamp / 1000) * 1000;
        assertFalse(sut.config.timestamp == 0);
        sut.config.timestamp = (sut.config.timestamp / 1000) * 1000;
        assertEquals(config, sut.config);
    }

    private static String toGAV(Revision r) {
        return format("%s:%s:%s", r.groupId, r.artifactId, r.version);
    }

    private static class TestMavenResolver implements IMavenResolver {

        private String methodCalled;
        private String gid;
        private String aid;
        private String version;
        private Collection<String> gavs;
        private ResolverConfig config;

        @Override
        public Set<Revision> resolveDependencies(Collection<String> gavs, ResolverConfig config) {
            assertNull(methodCalled);
            methodCalled = "deps";
            this.gavs = gavs;
            this.config = config;
            return null;
        }

        @Override
        public Set<Revision> resolveDependents(String g, String a, String v, ResolverConfig config) {
            assertNull(methodCalled);
            methodCalled = "dpts";
            this.gid = g;
            this.aid = a;
            this.version = v;
            this.config = config;
            return null;
        }
    }
}