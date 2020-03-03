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

package eu.fasten.analyzer.javacgwala.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.types.ClassLoaderReference;
import eu.fasten.analyzer.javacgwala.data.callgraph.CallGraphConstructor;
import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ArtifactResolverTest {

    private static CallGraph graph;
    private static File jar;

    @BeforeAll
    static void setUp() throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
        jar = new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile());

        graph = CallGraphConstructor.generateCallGraph(jar.getAbsolutePath());
    }

    @Test
    void findJarFileUsingMethod() throws IOException {
        ArtifactResolver artifactResolver = new ArtifactResolver(graph.getClassHierarchy());
        JarFile jarFile = new JarFile(jar);

        CGNode correctClassLoaderNode = null;

        for (CGNode node : graph) {

            if (!node.getMethod()
                    .getDeclaringClass()
                    .getClassLoader()
                    .getReference()
                    .equals(ClassLoaderReference.Application)) {
                continue;
            }

            correctClassLoaderNode = node;
            break;
        }

        assertNotNull(correctClassLoaderNode);
        assertEquals(jarFile.getName(), artifactResolver
                .findJarFileUsingMethod(correctClassLoaderNode.getMethod().getReference()).getName());
    }
}