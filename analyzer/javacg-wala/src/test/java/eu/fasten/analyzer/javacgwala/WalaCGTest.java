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

import eu.fasten.analyzer.javacgwala.WalaJavaCGGen;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class WalaCGTest {

    @Test
    public void generateCallgraph() {
        var cg = WalaJavaCGGen.generateCallGraph("ai.h2o:h2o-bindings:3.10.0.7");
        assertEquals(cg.product, "ai.h2o.h2o-bindings");
        assertEquals(cg.version, "3.10.0.7");
        assertEquals(cg.uri, "fasten://mvn!ai.h2o.h2o-bindings$3.10.0.7");
        assertNotNull(cg.graph);
    }

    @Test
    public void nameTest() {
        var cgName = new WalaJavaCGGen().name();
        assertEquals(cgName, "eu.fasten.analyzer.javacgwala");
    }

    @Test
    public void descriptionTest() {
        var cgDescription = new WalaJavaCGGen().description();
        assertEquals(cgDescription, "Constructs call graphs for Java packages using Wala.");
    }
}
