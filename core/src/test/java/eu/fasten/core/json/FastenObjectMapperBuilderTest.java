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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.c0ps.maven.data.Exclusion;

public class FastenObjectMapperBuilderTest {

    private ObjectMapper sut;

    @BeforeEach
    public void setup() {
        sut = new FastenObjectMapperBuilder().build();
    }

    @Test
    public void hasRegistrationForCoreMavenDataModule() throws JsonProcessingException {
        var in = new Exclusion("g", "a");
        var actual = sut.writeValueAsString(in);
        assertEquals("\"g:a\"", actual);
    }
}