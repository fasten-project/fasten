/*
 * Copyright 2021 Delft University of Technology
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DependencyTest {

    private static final Dependency SOME_DEPENDENCY = new Dependency("gid", "aid",
            Set.of(new VersionConstraint("(1.2.3,2.3.4]")), Set.of(new Exclusion("gid2", "aid2")), "compile", false,
            "jar", "sources");

    @Test
    public void dependencyTest() {
        var expected = new Dependency("junit", "junit", "4.11");
        var json = expected.toJSON();
        var actual = Dependency.fromJSON(json);
        assertEquals(expected, actual);
    }

    @Test
    public void equalsTest() {
        Assertions.assertEquals( //
                new Dependency("junit", "junit", "4.12"), //
                new Dependency("junit", "junit", "4.12", new HashSet<>(), "compile", false, "jar", ""));
    }

    @Test
    public void cannotUseInvalidScope() {
        assertThrows(IllegalStateException.class, () -> {
            new Dependency("junit", "junit", "4.12", new HashSet<>(), "", false, "jar", "");
        });
    }

    @Test
    public void cannotUseEmptyPackagingType() {
        assertThrows(IllegalStateException.class, () -> {
            new Dependency("junit", "junit", "4.12", new HashSet<>(), "", false, "", "");
        });
    }

    @Test
    public void jsonRoundtripViaObj() {
        var a = SOME_DEPENDENCY;
        var jsonObj = a.toJSON();
        var b = Dependency.fromJSON(jsonObj);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void jsonRoundtripViaString() {
        var a = SOME_DEPENDENCY;
        var json = a.toJSON().toString();
        var jsonObj = new JSONObject(json);
        var b = Dependency.fromJSON(jsonObj);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void jsonHasRightFields() {

        var expected = new HashMap<String, Class<?>>();
        expected.put("artifactId", String.class);
        expected.put("groupId", String.class);
        expected.put("type", String.class);
        expected.put("versionConstraints", JSONArray.class);
        expected.put("exclusions", JSONArray.class);
        expected.put("scope", String.class);
        expected.put("classifier", String.class);
        expected.put("optional", Boolean.class);

        var actual = SOME_DEPENDENCY.toJSON();
        for (var expectedField : expected.keySet()) {
            var expectedType = expected.get(expectedField);

            if (!actual.has(expectedField)) {
                fail(String.format("Resulting json object is missing field '%s'", expectedField));
            }
            var obj = actual.get(expectedField);
            assertNotNull(obj);
            var objType = obj.getClass();
            if (!expectedType.isAssignableFrom(objType)) {
                fail(String.format("Expected type %s, but was %s", expectedType, objType));
            }
        }
    }

    @Test
    public void equality() {
        var a = someDep();
        var b = someDep();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    private Dependency someDep() {
        var vcs = new HashSet<VersionConstraint>();
        vcs.add(new VersionConstraint("(,1.0]"));
        vcs.add(new VersionConstraint("[1.2)"));
        var excls = new HashSet<Exclusion>();
        return new Dependency("g", "a", vcs, excls, "compile", true, "jar", "c");
    }
}