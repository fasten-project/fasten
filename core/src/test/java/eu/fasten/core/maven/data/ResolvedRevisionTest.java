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
package eu.fasten.core.maven.data;

import static eu.fasten.core.maven.data.Scope.IMPORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.sql.Timestamp;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;

public class ResolvedRevisionTest {

    private static final ResolvedRevision SOME_REV = new ResolvedRevision(123, "g", "a", "v", new Timestamp(234),
            IMPORT);

    @Test
    public void init1() {
        var actual = SOME_REV;
        assertEquals("a", actual.artifactId);
        assertEquals(new Timestamp(234), actual.createdAt);
        assertEquals("g", actual.groupId);
        assertEquals(123, actual.id);
        assertEquals(IMPORT, actual.scope);
        assertEquals(new DefaultArtifactVersion("v"), actual.version);
    }

    @Test
    public void init2() {
        var in = new Revision(123, "g", "a", "v", new Timestamp(234));
        var actual = new ResolvedRevision(in, IMPORT);
        assertEquals("a", actual.artifactId);
        assertEquals(new Timestamp(234), actual.createdAt);
        assertEquals("g", actual.groupId);
        assertEquals(123, actual.id);
        assertEquals(IMPORT, actual.scope);
        assertEquals(new DefaultArtifactVersion("v"), actual.version);
    }

    @Test
    public void initDefault() {
        var actual = new ResolvedRevision();
        assertEquals(null, actual.artifactId);
        assertEquals(null, actual.createdAt);
        assertEquals(null, actual.groupId);
        assertEquals(0, actual.id);
        assertEquals(null, actual.scope);
        assertEquals(null, actual.version);
    }

    @Test
    public void equalityDefault() {
        var a = new ResolvedRevision();
        var b = new ResolvedRevision();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityNonDefault() {
        var a = SOME_REV;
        var b = SOME_REV;
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentIdDoesNotMatter() {
        var a = new ResolvedRevision();
        var b = new ResolvedRevision();
        b.id = 123;
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentGroup() {
        var a = new ResolvedRevision();
        var b = new ResolvedRevision();
        b.groupId = "g";
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentArtifact() {
        var a = new ResolvedRevision();
        var b = new ResolvedRevision();
        b.artifactId = "a";
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentVersion() {
        var a = new ResolvedRevision();
        var b = new ResolvedRevision();
        b.version = new DefaultArtifactVersion("v");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentTimestampDoesNotMatter() {
        var a = new ResolvedRevision();
        var b = new ResolvedRevision();
        b.createdAt = new Timestamp(123);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentScopeDoesNotMatter() {
        var a = new ResolvedRevision();
        var b = new ResolvedRevision();
        b.scope = Scope.IMPORT;
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}