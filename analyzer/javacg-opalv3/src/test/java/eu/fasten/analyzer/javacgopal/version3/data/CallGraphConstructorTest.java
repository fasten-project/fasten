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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.Objects;
import org.apache.commons.lang.StringUtils;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.rules.ExpectedException;
import scala.NotImplementedError;

class CallGraphConstructorTest {

    @Test
    void constructCHAEmptyMainClass() {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.class"))
                .getFile());

        var cg = new CallGraphConstructor(file, "", "CHA");

        var confValue1 = cg.getProject().config().getValue("org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis");
        assertEquals("org.opalj.br.analyses.cg.LibraryEntryPointsFinder",
                confValue1.render().substring(1, confValue1.render().length() - 1));

        var confValue2 = cg.getProject().config().getValue("org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis");
        assertEquals("org.opalj.br.analyses.cg.LibraryInstantiatedTypesFinder",
                confValue2.render().substring(1, confValue2.render().length() - 1));
    }

    @Test
    void constructCHANullMainClass() {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.class"))
                .getFile());

        var cg = new CallGraphConstructor(file, null, "CHA");

        var confValue1 = cg.getProject().config().getValue("org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis");
        assertEquals("org.opalj.br.analyses.cg.LibraryEntryPointsFinder",
                confValue1.render().substring(1, confValue1.render().length() - 1));

        var confValue2 = cg.getProject().config().getValue("org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis");
        assertEquals("org.opalj.br.analyses.cg.LibraryInstantiatedTypesFinder",
                confValue2.render().substring(1, confValue2.render().length() - 1));
    }

    @Test
    void constructCHAWithMainClass() {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.class"))
                .getFile());

        var cg = new CallGraphConstructor(file, "SingleSourceToTarget", "CHA");

        var confValue1 = cg.getProject().config().getValue("org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis");
        assertEquals("org.opalj.br.analyses.cg.ConfigurationEntryPointsFinder",
                confValue1.render().substring(1, confValue1.render().length() - 1));

        var confValue2 = cg.getProject().config().getValue("org.opalj.br.analyses.cg.InitialEntryPointsKey.entryPoints");
        assertEquals("SingleSourceToTarget", StringUtils
                .substringBetween(confValue2.render(), "\"declaringClass\" : \"", "\","));

        var confValue3 = cg.getProject().config().getValue("org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis");
        assertEquals("org.opalj.br.analyses.cg.ApplicationInstantiatedTypesFinder",
                confValue3.render().substring(1, confValue3.render().length() - 1));
    }

    @Test
    void constructRTA() {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.class"))
                .getFile());

        var cg = new CallGraphConstructor(file, "", "RTA");

        assertNotNull(cg.getCallGraph());
        assertNotNull(cg.getProject());
        assertTrue(cg.getCallGraph().numEdges() > 0);
    }

    @Test
    void constructCHA() {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.class"))
                .getFile());

        var cg = new CallGraphConstructor(file, "", "CHA");

        assertNotNull(cg.getCallGraph());
        assertNotNull(cg.getProject());
        assertTrue(cg.getCallGraph().numEdges() > 0);
    }

    @Test
    void constructAllocationSiteBasedPointsTo() {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.class"))
                .getFile());

        var cg = new CallGraphConstructor(file, "", "AllocationSiteBasedPointsTo");

        assertNotNull(cg.getCallGraph());
        assertNotNull(cg.getProject());
        assertTrue(cg.getCallGraph().numEdges() > 0);
    }

    @Test
    void constructTypeBasedPointsTo() {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.class"))
                .getFile());

        var cg = new CallGraphConstructor(file, "", "TypeBasedPointsTo");

        assertNotNull(cg.getCallGraph());
        assertNotNull(cg.getProject());
        assertTrue(cg.getCallGraph().numEdges() > 0);
    }

    @Test
    void constructWrongAlgorithm() {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.class"))
                .getFile());

        Exception exception = assertThrows(IllegalStateException.class, () ->
                new CallGraphConstructor(file, "", "WrongAlgorithm"));

        assertEquals("Unexpected value: WrongAlgorithm", exception.getMessage());
    }
}