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

package eu.fasten.analyzer.javacgopal.data;

import eu.fasten.analyzer.javacgopal.data.callgraph.PartialCallGraph;
import eu.fasten.core.data.FastenJavaURI;

import java.io.File;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class ResolvedCallTest {

    @Test
    public void testToURICalls() {

        assertArrayEquals(
                new FastenJavaURI[]{
                        new FastenJavaURI("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid"),
                        new FastenJavaURI("/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid")}
                ,
                new PartialCallGraph(
                        new File(Thread.currentThread().getContextClassLoader().getResource("SingleSourceToTarget.class").getFile())
                ).getResolvedCalls().get(0).toURICalls().get(0)
        );

    }
}