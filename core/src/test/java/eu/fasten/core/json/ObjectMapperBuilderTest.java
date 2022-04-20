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
package eu.fasten.core.json;

import static com.fasterxml.jackson.databind.SerializationFeature.USE_EQUALITY_FOR_OBJECT_ID;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper.Builder;

public class ObjectMapperBuilderTest {

    private static final SerializationFeature SOME_FEATURE = USE_EQUALITY_FOR_OBJECT_ID;

    private ObjectMapper sut;

    @TempDir
    private File tempDir;

    @BeforeEach
    public void setup() {
        sut = new ObjectMapperBuilder().build();
    }

    @Test
    public void defaultConfig() {
        sut = new ObjectMapperBuilder().build();
        assertFalse(sut.isEnabled(SOME_FEATURE));
    }

    @Test
    public void canAddBuilderOptions() {
        sut = new ObjectMapperBuilder() {
            @Override
            protected Builder addBuilderOptions(Builder b) {
                return b.enable(SOME_FEATURE);
            }
        }.build();
        assertTrue(sut.isEnabled(SOME_FEATURE));
    }

    @Test
    public void canAddMapperOptions() {
        sut = new ObjectMapperBuilder() {
            protected ObjectMapper addMapperOptions(ObjectMapper om) {
                return om.enable(SOME_FEATURE);
            }
        }.build();
        assertTrue(sut.isEnabled(SOME_FEATURE));
    }

    @Test
    public void doesNotStoreNullEmptyOrDefaultFields() throws JsonProcessingException {
        sut = new ObjectMapperBuilder().build();
        var data = new TestData();
        data.y3 = true;
        var actual = sut.writeValueAsString(data);
        assertEquals("{\"y1\":\"y1\",\"y2\":[\"y2\"],\"y3\":true,\"y4\":[],\"y5\":false,\"y6\":false}", actual);
    }

    @Test
    public void doesNotCloseTargets() throws IOException {
        sut = new ObjectMapperBuilder().build();
        var f = new File(tempDir, "abc.asd");
        try (var os = new FileOutputStream(f)) {
            sut.writeValue(os, "123");
            os.write("4".getBytes());
        }
        // test is successful if no exception is thrown
    }

    @Test
    public void doesNotCloseSources() throws IOException {
        sut = new ObjectMapperBuilder().build();
        var f = new File(tempDir, "abc.asd");
        FileUtils.writeStringToFile(f, "\"abc\"", UTF_8);
        try (var os = new FileInputStream(f)) {
            sut.readValue(os, String.class);
            os.read();
        }
        // test is successful if no exception is thrown
    }

    @Test
    public void jsr310() throws JsonProcessingException {
        // will crash with missing import
        var ldt = LocalDateTime.now();
        sut.writeValueAsString(ldt);
    }

    @Test
    public void java8() throws JsonProcessingException {
        var o = Optional.of("...");
        var json = sut.writeValueAsString(o);
        assertEquals("\"...\"", json);
    }

    @SuppressWarnings("unused")
    private static class TestData {
        public String y1 = "y1";
        public Set<String> y2 = Set.of("y2");
        public boolean y3 = false;

        // empty
        public Set<String> y4 = new HashSet<>();

        // defaults
        public boolean y5;
        public boolean y6 = false;

        public String n1 = null;
        public Set<String> n2 = null;
    }
}