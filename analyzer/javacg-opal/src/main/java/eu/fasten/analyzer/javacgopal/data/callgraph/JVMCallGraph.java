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

package eu.fasten.analyzer.javacgopal.data.callgraph;

import eu.fasten.analyzer.javacgopal.data.ResolvedCall;

import java.io.FileWriter;
import java.io.IOException;

import org.opalj.br.Method;

/**
 * A converter for graphs to make JVM format from other graph formats.
 */
public class JVMCallGraph {
    /**
     * Prints JVM-formated graphs like this "caller callee </br>".
     * @param partialCallGraph a partial graph.
     */
    public static void printJVMGraph(PartialCallGraph partialCallGraph) throws IOException {
        FileWriter writer = new FileWriter("output.txt");
        for (ResolvedCall resolvedCall : partialCallGraph.getResolvedCalls()) {
            for (Method method : resolvedCall.getTargets()) {
                writer.write(toJVMMethod(resolvedCall.getSource()) + " " + toJVMMethod(method) + "\n");
            }
        }
        writer.close();
    }

    /**
     * Converts an OPAL Method to JVM Method.
     * @param method OPAL method.
     * @return JVM method in string.
     */
    public static String toJVMMethod(Method method) {
        return method.classFile().thisType().toJVMTypeName().replace(";", "/" + method.name()) + method.descriptor().toJVMDescriptor();
    }

}
