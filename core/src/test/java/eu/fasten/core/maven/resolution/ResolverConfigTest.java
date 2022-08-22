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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Date;

import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Scope;

public class ResolverConfigTest {

    @Test
    public void defaults() {
        var sut = new ResolverConfig();
        assertEquals(Integer.MAX_VALUE, sut.depth);
        assertEquals(Scope.RUNTIME, sut.scope);
        assertFalse(sut.alwaysIncludeProvided);
        assertFalse(sut.alwaysIncludeOptional);
        var diff = new Date().getTime() - sut.resolveAt;
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
        var a = getNonDefaultConfig();
        var b = getNonDefaultConfig();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentDepth() {
        var a = getNonDefaultConfig();
        var b = getNonDefaultConfig();
        b.depth = 2;
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentScope() {
        var a = getNonDefaultConfig();
        var b = getNonDefaultConfig();
        b.scope = Scope.PROVIDED;
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentTime() {
        var a = getNonDefaultConfig();
        var b = getNonDefaultConfig();
        b.resolveAt = 1234567890123L;
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentProvided() {
        var a = getNonDefaultConfig();
        var b = getNonDefaultConfig();
        b.alwaysIncludeProvided = false;
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentOptional() {
        var a = getNonDefaultConfig();
        var b = getNonDefaultConfig();
        b.alwaysIncludeOptional = false;
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

        var diff = Math.abs(a.resolveAt - b.resolveAt);
        assertTrue(diff < 100);
        b.resolveAt = a.resolveAt;

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void builderSetsTime() {
        var sut = resolve();
        assertSame(sut, sut.at(23456));
        assertEquals(sut.resolveAt, 23456);
    }

    @Test
    public void builderIncludesTransitives() {
        var sut = resolve();
        assertSame(sut, sut.includeTransitiveDeps());
        assertEquals(sut.depth, Integer.MAX_VALUE);
    }

    @Test
    public void builderExcludesTransitives() {
        var sut = resolve();
        assertSame(sut, sut.excludeTransitiveDeps());
        assertEquals(sut.depth, 1);
    }

    @Test
    public void builderLimitsTransitives() {
        var sut = resolve();
        assertSame(sut, sut.limitTransitiveDeps(17));
        assertEquals(17, sut.depth);
    }

    @Test
    public void builderDistinguishesDirectAndTransitives() {
        assertFalse(resolve().includeTransitiveDeps().isExcludingTransitiveDeps());
        assertTrue(resolve().excludeTransitiveDeps().isExcludingTransitiveDeps());
        assertFalse(resolve().limitTransitiveDeps(13).isExcludingTransitiveDeps());
    }

    @Test
    public void builderSetsScope() {
        var sut = resolve();
        assertSame(sut, sut.scope(Scope.COMPILE));
        assertEquals(sut.scope, Scope.COMPILE);
    }

    @Test
    public void builderSetsProvided() {
        var sut = resolve();
        assertSame(sut, sut.alwaysIncludeProvided(true));
        assertTrue(sut.alwaysIncludeProvided);
    }

    @Test
    public void builderSetsOptional() {
        var sut = resolve();
        assertSame(sut, sut.alwaysIncludeOptional(true));
        assertTrue(sut.alwaysIncludeOptional);
    }

    @Test
    public void builderFailsForInvalidDepths() {
        assertThrows(MavenResolutionException.class, () -> {
            resolve().limitTransitiveDeps(0);
        });
        assertThrows(MavenResolutionException.class, () -> {
            resolve().limitTransitiveDeps(-1);
        });
    }

    private static ResolverConfig getNonDefaultConfig() {
        var cfg = new ResolverConfig();
        cfg.depth = 1357;
        cfg.scope = Scope.COMPILE;
        cfg.resolveAt = 1234567890000L;
        cfg.alwaysIncludeProvided = true;
        cfg.alwaysIncludeOptional = true;
        return cfg;
    }
}