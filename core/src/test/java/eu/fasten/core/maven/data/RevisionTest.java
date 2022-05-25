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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.sql.Timestamp;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;

public class RevisionTest {

    private static final Timestamp SOME_TIMESTAMP = new Timestamp(1234567890L);
    private static final Timestamp OTHER_TIMESTAMP = new Timestamp(2345678901L);

    @Test
    public void defaultsInitNoArgs() {
        var sut = new Revision();
        assertEquals(0, sut.id);
        assertNull(sut.groupId);
        assertNull(sut.artifactId);
        assertNull(sut.version);
        assertNull(sut.createdAt);
    }

    @Test
    public void defaultsInit1() {
        var sut = new Revision("g", "a", "1.2.3", SOME_TIMESTAMP);
        assertEquals(0, sut.id);
        assertEquals("g", sut.groupId);
        assertEquals("a", sut.artifactId);
        assertEquals(new DefaultArtifactVersion("1.2.3"), sut.version);
        assertEquals(SOME_TIMESTAMP, sut.createdAt);
    }

    @Test
    public void defaultsInit2() {
        var sut = new Revision(1234, "g", "a", "1.2.3", SOME_TIMESTAMP);
        assertEquals(1234, sut.id);
        assertEquals("g", sut.groupId);
        assertEquals("a", sut.artifactId);
        assertEquals(new DefaultArtifactVersion("1.2.3"), sut.version);
        assertEquals(SOME_TIMESTAMP, sut.createdAt);
    }

    @Test
    public void equality() {
        var a = new Revision(1234, "g", "a", "1.2.3", SOME_TIMESTAMP);
        var b = new Revision(1234, "g", "a", "1.2.3", SOME_TIMESTAMP);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDespiteDifferentId() {
        var a = new Revision(1234, "g", "a", "1.2.3", SOME_TIMESTAMP);
        var b = new Revision(2345, "g", "a", "1.2.3", SOME_TIMESTAMP);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDespiteDifferentTimestamp() {
        var a = new Revision(1234, "g", "a", "1.2.3", SOME_TIMESTAMP);
        var b = new Revision(1234, "g", "a", "1.2.3", OTHER_TIMESTAMP);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentGroupId() {
        var a = new Revision(1234, "g", "a", "1.2.3", SOME_TIMESTAMP);
        var b = new Revision(1234, "g2", "a", "1.2.3", SOME_TIMESTAMP);
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentArtifactId() {
        var a = new Revision(1234, "g", "a", "1.2.3", SOME_TIMESTAMP);
        var b = new Revision(1234, "g", "a2", "1.2.3", SOME_TIMESTAMP);
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentVersion() {
        var a = new Revision(1234, "g", "a", "1.2.3", SOME_TIMESTAMP);
        var b = new Revision(1234, "g", "a", "2.3.4", SOME_TIMESTAMP);
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hasToString() {
        var actual = new Revision(1234, "g", "a", "1.2.3", SOME_TIMESTAMP).toString();
        assertTrue(actual.contains(Revision.class.getSimpleName()));
        assertTrue(actual.contains("\n"));
        assertTrue(actual.contains("@"));
        assertTrue(actual.contains("createdAt"));
    }
}