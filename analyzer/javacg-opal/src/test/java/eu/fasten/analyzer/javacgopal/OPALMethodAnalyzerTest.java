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

import scala.collection.JavaConversions;

import java.io.File;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;

class OPALMethodAnalyzerTest {

    PartialCallGraph callGraph;

    @BeforeEach
    void generateCallGraph(){
        callGraph = CallGraphGenerator.generatePartialCallGraph(
                new File(Thread.currentThread().getContextClassLoader().getResource("SingleSourceToTarget.class").getFile())

        );
    }

    @Test
     void testToCanonicalFastenJavaURI() {

        assertEquals(
                new FastenJavaURI("fasten:/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2Fvoid"),
                OPALMethodAnalyzer.toCanonicalFastenJavaURI(callGraph.getResolvedCalls().get(0).getSource())
        );

        assertEquals(
                new FastenJavaURI("fasten:/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2Fvoid"),
                OPALMethodAnalyzer.toCanonicalFastenJavaURI(callGraph.getResolvedCalls().get(0).getTarget().get(0))
        );
    }

    @Test
    void testGetPctReturnType() {

        assertEquals(
                "%2Fjava.lang%2Fvoid",
                OPALMethodAnalyzer.getPctReturnType(callGraph.getResolvedCalls().get(0).getSource()
                .returnType())
        );
    }

    @Test
    void testGetPctParameters() {

        assertEquals(
                "",
                OPALMethodAnalyzer.getPctParameters(JavaConversions.seqAsJavaList(callGraph.getResolvedCalls().get(0).getSource()
                        .parameterTypes()))
        );

    }

    @Test
    void testGetPackageName() {

        assertEquals(
                "/java.lang",
                OPALMethodAnalyzer.getPackageName(callGraph.getResolvedCalls().get(0).getSource()
                        .returnType())
        );
    }

    @Test
    void testGetClassName() {

        assertEquals(
                "/void",
                OPALMethodAnalyzer.getClassName(callGraph.getResolvedCalls().get(0).getSource()
                        .returnType())
        );
    }

}