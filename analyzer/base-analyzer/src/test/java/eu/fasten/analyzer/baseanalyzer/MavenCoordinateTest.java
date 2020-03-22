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

package eu.fasten.analyzer.baseanalyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class MavenCoordinateTest {

    @Test
    public void testResolveDependencies() {
        var deps = MavenCoordinate.MavenResolver.resolveDependencies("com.ibm.wala:com.ibm.wala.core:1.5.4");
        assertNotNull(deps);
        assertEquals(1, deps.size());
        assertEquals(1, deps.get(0).stream().filter(x -> x.product.contains("shrike")).toArray().length);
        assertEquals(1, deps.get(0).stream().filter(x -> x.product.contains("util")).toArray().length);
        assertEquals("[1.5.4]", deps.get(0).get(0).constraints.get(0).toString());
    }

    @Test
    public void testResolveDependenciesWithProfile() throws IOException {
        var file = new File(Thread.currentThread().getContextClassLoader()
                .getResource("testpom.txt")
                .getFile()).getAbsolutePath();

        FileInputStream inputStream = new FileInputStream(file);
        var pomText = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

        MavenCoordinate.MavenResolver resolver = Mockito.mock(MavenCoordinate.MavenResolver.class);
        Mockito.when(resolver.downloadPom("coordinate")).thenReturn(java.util.Optional.of(pomText));
        Mockito.doCallRealMethod().when(resolver).getDependencies("coordinate");

        var deps = resolver.getDependencies("coordinate");
        assertNotNull(deps);
        assertEquals("mvn", deps.get(0).get(0).forge);
        assertEquals("mvn", deps.get(0).get(1).forge);
        assertEquals("org.slf4j.slf4j-simple", deps.get(0).get(0).product);
        assertEquals("org.dom4j.dom4j", deps.get(0).get(1).product);
        assertEquals("[1.7.30]", deps.get(0).get(0).constraints.get(0).toString());
        assertEquals("[*]", deps.get(0).get(1).constraints.get(0).toString());
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