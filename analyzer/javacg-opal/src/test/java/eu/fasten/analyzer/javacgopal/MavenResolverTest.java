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

package eu.fasten.analyzer.javacgopal;

import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MavenResolverTest {

    @Test
    public void testResolveDependencies() {
        var deps = MavenResolver.resolveDependencies("com.ibm.wala:com.ibm.wala.core:1.5.4");
        assertNotNull(deps);
        assertEquals(2, deps.size());
        assertEquals(1, deps.get(0).stream().filter(x -> x.product.contains("shrike")).toArray().length);
        assertEquals(1, deps.get(1).stream().filter(x -> x.product.contains("util")).toArray().length);
        assertEquals("[1.5.4]", deps.get(0).get(0).constraints.get(0).toString());
    }

    @Test
    public void testURLFromCoordinates() {

        assertEquals("https://repo.maven.apache.org/maven2/com/ibm/wala/com.ibm.wala.core/1.5.4/com.ibm.wala.core-1.5.4.jar",
                MavenCoordinate.fromString("com.ibm.wala:com.ibm.wala.core:1.5.4").toJarUrl());

        assertEquals("https://repo.maven.apache.org/maven2/org/elasticsearch/elasticsearch/5.0.2/elasticsearch-5.0.2.pom",
                MavenCoordinate.fromString("org.elasticsearch:elasticsearch:5.0.2").toPomUrl());

        assertEquals("https://repo.maven.apache.org/maven2/commons-codec/commons-codec/1.13/commons-codec-1.13.pom",
                MavenCoordinate.fromString("commons-codec:commons-codec:1.13").toPomUrl());

        assertEquals("https://repo.maven.apache.org/maven2/org/codefeedr/codefeedr-plugin-mongodb_2.12/0.1.3/codefeedr-plugin-mongodb_2.12-0.1.3.jar",
                MavenCoordinate.fromString("org.codefeedr:codefeedr-plugin-mongodb_2.12:0.1.3").toJarUrl());
    }
}