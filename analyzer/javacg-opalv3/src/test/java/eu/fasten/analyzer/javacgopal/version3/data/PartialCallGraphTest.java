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

package eu.fasten.analyzer.javacgopal.version3.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.fasten.analyzer.baseanalyzer.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.version3.ExtendedRevisionCallGraphV3;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.RevisionCallGraph;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Objects;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PartialCallGraphTest {

    private static PartialCallGraph singleCallCG;

    @BeforeAll
    static void setUp() {
        singleCallCG = new PartialCallGraph(
                new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                        .getResource("SingleSourceToTarget.class")).getFile()), "", "CHA");
    }

    @Test
    void getClassHierarchy() {
        var cha = singleCallCG.getClassHierarchy();

        assertNotNull(cha);
        assertNotNull(cha.get(ExtendedRevisionCallGraphV3.Scope.internalTypes));
        assertEquals(1, cha.get(ExtendedRevisionCallGraphV3.Scope.internalTypes).size());
        assertEquals(1, cha.get(ExtendedRevisionCallGraphV3.Scope.externalTypes).size());
        assertEquals(0, cha.get(ExtendedRevisionCallGraphV3.Scope.resolvedTypes).size());

        // -------
        // Check internal types
        // -------
        var SSTTInternalType = cha.get(ExtendedRevisionCallGraphV3.Scope.internalTypes)
                .get(FastenURI.create("/name.space/SingleSourceToTarget"));

        // Check filename
        assertEquals("SingleSourceToTarget.java", SSTTInternalType.getSourceFileName());

        // Check super interfaces and classes
        assertEquals(0, SSTTInternalType.getSuperInterfaces().size());
        assertEquals(1, SSTTInternalType.getSuperClasses().size());
        assertEquals(FastenURI.create("/java.lang/Object"), SSTTInternalType.getSuperClasses().get(0));

        // Check methods
        assertEquals(3, SSTTInternalType.getMethods().size());

        assertEquals(FastenURI.create("/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoidType"),
                SSTTInternalType.getMethods().get(0).getUri());
        assertEquals("public", SSTTInternalType.getMethods().get(0).getMetadata().get("access"));
        assertEquals(true, SSTTInternalType.getMethods().get(0).getMetadata().get("defined"));
        assertEquals(3, SSTTInternalType.getMethods().get(0).getMetadata().get("first"));
        assertEquals(3, SSTTInternalType.getMethods().get(0).getMetadata().get("last"));

        // -------
        // Check external types
        // -------
        var SSTTExternalType = cha.get(ExtendedRevisionCallGraphV3.Scope.externalTypes)
                .get(FastenURI.create("/java.lang/Object"));

        // Check super interfaces and classes
        assertEquals(0, SSTTExternalType.getSuperInterfaces().size());
        assertEquals(0, SSTTExternalType.getSuperClasses().size());

        // Check methods
        assertEquals(1, SSTTExternalType.getMethods().size());

        assertEquals(FastenURI.create("/java.lang/Object.Object()VoidType"),
                SSTTExternalType.getMethods().get(3).getUri());
        assertEquals(0, SSTTExternalType.getMethods().get(3).getMetadata().size());
    }

    @Test
    void getGraph() {
        var graph = singleCallCG.getGraph();

        assertNotNull(graph);
        assertEquals(1, graph.getInternalCalls().size());
        assertEquals(1, graph.getExternalCalls().size());
        assertEquals(0, graph.getResolvedCalls().size());

        // Check internal calls
        var internalCalls = graph.getInternalCalls();

        var call = new ArrayList<Integer>();
        call.add(1);
        call.add(2);

        assertNotNull(internalCalls.get(call).get("0"));
        assertTrue(internalCalls.get(call).get("0") instanceof OPALCallSite);
        assertEquals(6, ((OPALCallSite) internalCalls.get(call).get("0")).getLine());
        assertEquals("invokestatic", ((OPALCallSite) internalCalls.get(call).get("0")).getType());
        assertEquals("/name.space/SingleSourceToTarget",
                ((OPALCallSite) internalCalls.get(call).get("0")).getReceiver());
    }

    @Test
    void getNodeCount() {
        assertEquals(4, singleCallCG.getNodeCount());
    }

    @Test
    void createExtendedRevisionCallGraph() throws FileNotFoundException {
        var coordinate = new MavenCoordinate("org.slf4j", "slf4j-api", "1.7.29");
        var rcg = PartialCallGraph.createExtendedRevisionCallGraph(coordinate,
                "", "CHA", 1574072773);
        assertSLF4j(rcg);
    }

    public static void assertSLF4j(RevisionCallGraph cg) {
        Assert.assertNotNull(cg);
        Assert.assertEquals("mvn", cg.forge);
        Assert.assertEquals("1.7.29", cg.version);
        Assert.assertEquals(1574072773, cg.timestamp);
        Assert.assertEquals(new FastenJavaURI("fasten://mvn!org.slf4j:slf4j-api$1.7.29"), cg.uri);
        Assert.assertEquals(new FastenJavaURI("fasten://org.slf4j:slf4j-api$1.7.29"), cg.forgelessUri);
        Assert.assertEquals("org.slf4j:slf4j-api", cg.product);
    }
}