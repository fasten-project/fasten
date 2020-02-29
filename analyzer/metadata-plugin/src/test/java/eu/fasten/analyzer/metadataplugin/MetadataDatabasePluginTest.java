/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.analyzer.metadataplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

public class MetadataDatabasePluginTest {

    private MetadataDatabasePlugin.MetadataPlugin metadataPlugin;

    @BeforeEach
    public void setUp() {
        metadataPlugin = new MetadataDatabasePlugin.MetadataPlugin();
    }

    @Test
    public void consumeTest() {
        // TODO: Test if consumed data is correctly saved in the database
    }

    @Test
    public void recordProcessSuccessfulTest() {
        assertFalse(metadataPlugin.recordProcessSuccessful());
    }

    @Test
    public void nameTest() {
        var name = "Metadata plugin";
        assertEquals(name, metadataPlugin.name());
    }

    @Test
    public void descriptionTest() {
        var description = "Metadata plugin. "
                + "Consumes kafka topic and populates metadata database with consumed data.";
        assertEquals(description, metadataPlugin.description());
    }
}
