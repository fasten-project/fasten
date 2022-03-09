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
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Date;

import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Scope;
import eu.fasten.core.maven.resolution.ResolverConfig;
import eu.fasten.core.maven.resolution.ResolverDepth;

public class ResolverConfigTest {

    @Test
    public void defaults() {
        var sut = new ResolverConfig();
        assertEquals(ResolverDepth.TRANSITIVE, sut.depth);
        assertEquals(Scope.RUNTIME, sut.scope);
        var diff = new Date().getTime() - sut.timestamp;
        assertTrue("Difference should be <100ms, but is " + diff, diff < 100);
    }

    @Test
    public void equalityDefault() {
        var a = new ResolverConfig();
        var b = new ResolverConfig();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityNonDefault() {
        var a = getSomeConfig();
        var b = getSomeConfig();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentDepth() {
        var a = new ResolverConfig();
        var b = new ResolverConfig();
        b.depth = ResolverDepth.DIRECT;
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentScope() {
        var a = new ResolverConfig();
        var b = new ResolverConfig();
        b.scope = Scope.COMPILE;
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentTime() {
        var a = new ResolverConfig();
        var b = new ResolverConfig();
        b.timestamp = 1234567890000L;
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hasToString() {
        var actual = new ResolverConfig().toString();
        assertTrue(actual.contains("\n"));
        assertTrue(actual.contains("@"));
        assertTrue(actual.contains("scope"));
        assertTrue(actual.contains(ResolverConfig.class.getSimpleName()));
    }

    @Test
    public void builderReturnsDefault() {
        var a = new ResolverConfig();
        var b = ResolverConfig.resolve();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void builderSetsTime() {
        var sut = resolve().at(23456);
        assertEquals(sut.timestamp, 23456);
    }

    @Test
    public void builderSetsDepth() {
        var sut = resolve().depth(ResolverDepth.DIRECT);
        assertEquals(sut.depth, ResolverDepth.DIRECT);
    }

    @Test
    public void builderSetsScope() {
        var sut = resolve().scope(Scope.COMPILE);
        assertEquals(sut.scope, Scope.COMPILE);
    }

    private static ResolverConfig getSomeConfig() {
        var cfg = new ResolverConfig();
        cfg.depth = ResolverDepth.DIRECT;
        cfg.scope = Scope.COMPILE;
        cfg.timestamp = 1234567890000L;
        return cfg;
    }
}