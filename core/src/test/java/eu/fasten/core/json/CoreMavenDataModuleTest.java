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
package eu.fasten.core.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Exclusion;
import eu.fasten.core.maven.data.Scope;
import eu.fasten.core.maven.data.VersionConstraint;

public class CoreMavenDataModuleTest {

    private ObjectMapper om;

    @BeforeEach
    public void setup() {
        om = new ObjectMapperBuilder() {
            @Override
            protected ObjectMapper addMapperOptions(ObjectMapper om) {
                return om.enable(SerializationFeature.INDENT_OUTPUT);
            }
        }.build();
    }

    @Test
    public void testDependency() {
        var d = new Dependency("g1", "a1", Set.of(new VersionConstraint("[1,2]")), Set.of(new Exclusion("g2", "a2")),
                Scope.TEST, false, "type", "sources");
        var json = "{\"versionConstraints\":[\"[1,2]\"],\"groupId\":\"g1\",\"scope\":\"test\",\"classifier\":\"sources\",\"artifactId\":\"a1\",\"exclusions\":[\"g2:a2\"],\"optional\":false,\"type\":\"type\"}";
        test(d, json);
    }

    @Test
    public void testVersionConstraint() {
        var vc = new VersionConstraint("[1,2]");
        test(vc, "\"[1,2]\"");
    }

    @Test
    public void testExclusion() {
        var e = new Exclusion("gid", "aid");
        test(e, "\"gid:aid\"");
    }

    @Test
    public void testDefaultArtifactVersion() {
        test(new DefaultArtifactVersion("1.2.3"), "\"1.2.3\"");
        test(new DefaultArtifactVersion("2.3.4-classifier"), "\"2.3.4-classifier\"");
        test(new DefaultArtifactVersion("3"), "\"3\"");
        test(new DefaultArtifactVersion("4.5"), "\"4.5\"");
    }

    private void test(Object in, String expectedJson) {
        try {
            var json = om.writeValueAsString(in);
            assertJsonEquals(expectedJson, json);
            var out = om.readValue(json, in.getClass());
            assertEquals(in, out);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void assertJsonEquals(String expectedJson, String actualJson) {
        JSONAssert.assertEquals(expectedJson, actualJson, JSONCompareMode.STRICT_ORDER);
    }
}