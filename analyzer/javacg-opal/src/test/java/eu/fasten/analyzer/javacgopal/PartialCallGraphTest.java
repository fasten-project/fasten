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

import eu.fasten.core.data.FastenJavaURI;

import java.io.File;

import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;



class PartialCallGraphTest {

    @Test
    void testCreateRevisionCallGraph() {

        var revisionCallGraph = PartialCallGraph.createRevisionCallGraph("mvn",
                new MavenCoordinate("org.slf4j", "slf4j-api","1.7.29"),
                1574072773,
                CallGraphGenerator.generatePartialCallGraph(
                        MavenResolver.downloadJar("org.slf4j:slf4j-api:1.7.29").orElseThrow(RuntimeException::new)
                )
        );

        assertNotNull(revisionCallGraph);
        assertEquals("mvn",revisionCallGraph.forge);
        assertEquals("1.7.29",revisionCallGraph.version);
        assertEquals(1574072773,revisionCallGraph.timestamp);
        assertEquals(new FastenJavaURI("fasten://mvn!org.slf4j.slf4j-api$1.7.29"),revisionCallGraph.uri);
        assertEquals(new FastenJavaURI("fasten://org.slf4j.slf4j-api$1.7.29"),revisionCallGraph.forgelessUri);
        assertEquals("org.slf4j.slf4j-api",revisionCallGraph.product);
        assertNotNull(revisionCallGraph.graph);

    }

    @Test
    public void testToURICallGraph() {

        /**
         * SingleSourceToTarget is a java8 compiled bytecode of:
         *<pre>
             * package name.space;
             *
             * public class SingleSourceToTarget{
             *
             *     public static void sourceMethod() { targetMethod(); }
             *
             *     public static void targetMethod() {}
             * }
         * </pre>
         */

        assertArrayEquals(
                new FastenJavaURI[]{
                        new FastenJavaURI("fasten:/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2Fvoid"),
                        new FastenJavaURI("fasten:/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2Fvoid")}
                ,
                CallGraphGenerator.generatePartialCallGraph(
                        new File(Thread.currentThread().getContextClassLoader().getResource("SingleSourceToTarget.class").getFile())
                ).toURIGraph().get(0)
        );
    }

}