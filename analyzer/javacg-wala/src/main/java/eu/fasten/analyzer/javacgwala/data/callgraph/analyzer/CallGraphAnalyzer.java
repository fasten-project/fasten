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

package eu.fasten.analyzer.javacgwala.data.callgraph.analyzer;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraph;
import eu.fasten.analyzer.javacgwala.data.core.CallType;
import eu.fasten.analyzer.javacgwala.data.core.Method;
import eu.fasten.analyzer.javacgwala.data.core.ResolvedMethod;
import java.util.Iterator;
import java.util.function.Predicate;

public class CallGraphAnalyzer {

    private final AnalysisContext analysisContext;

    private final CallGraph rawCallGraph;

    private final PartialCallGraph partialCallGraph;

    private final ClassHierarchyAnalyzer classHierarchyAnalyzer;

    /**
     * Analyze raw call graph in Wala format.
     *
     * @param rawCallGraph     Raw call graph in Wala format
     * @param partialCallGraph Partial call graph
     */
    public CallGraphAnalyzer(CallGraph rawCallGraph, PartialCallGraph partialCallGraph) {
        this.rawCallGraph = rawCallGraph;
        this.partialCallGraph = partialCallGraph;
        this.analysisContext = new AnalysisContext(rawCallGraph.getClassHierarchy());
        this.classHierarchyAnalyzer = new ClassHierarchyAnalyzer(rawCallGraph, partialCallGraph);
    }

    /**
     * Iterate over nodes in Wala call graph and add calls that "belong" to application class
     * loader to lists of resolved / unresolved calls of partial call graph.
     */
    public void resolveCalls() {
        for (CGNode node : this.rawCallGraph) {
            MethodReference nodeReference = node.getMethod().getReference();

            if (applicationClassLoaderFilter.test(node)) {
                continue;
            }

            Method methodNode = analysisContext.findOrCreate(nodeReference);

            var callerID = classHierarchyAnalyzer.addMethodToCHA(methodNode,
                    nodeReference.getDeclaringClass());

            for (Iterator<CallSiteReference> callSites = node.iterateCallSites();
                 callSites.hasNext(); ) {
                CallSiteReference callSite = callSites.next();

                MethodReference targetWithCorrectClassLoader = correctClassLoader(callSite
                        .getDeclaredTarget());

                Method targetMethodNode =
                        analysisContext.findOrCreate(targetWithCorrectClassLoader);

                var calleeID = classHierarchyAnalyzer.addMethodToCHA(targetMethodNode,
                        targetWithCorrectClassLoader
                                .getDeclaringClass());
                addCall(methodNode, callerID, targetMethodNode, calleeID,
                        getInvocationLabel(callSite));
            }

        }
    }

    /**
     * Add call to partial call graph.
     *
     * @param source   Caller
     * @param target   Callee
     * @param callType Call type
     */
    private void addCall(Method source, int sourceID, Method target,
                         int targetID, CallType callType) {

        if (source instanceof ResolvedMethod && target instanceof ResolvedMethod) {
            partialCallGraph.addResolvedCall(sourceID, targetID);
        } else {
            partialCallGraph.addUnresolvedCall(sourceID,
                    target.toCanonicalSchemalessURI(), callType);
        }
    }

    /**
     * True if node "belongs" to application class loader.
     */
    private Predicate<CGNode> applicationClassLoaderFilter = node -> !node.getMethod()
            .getDeclaringClass()
            .getClassLoader()
            .getReference()
            .equals(ClassLoaderReference.Application);

    /**
     * Get class loader with correct class loader.
     *
     * @param reference Method reference
     * @return Method reference with correct class loader
     */
    private MethodReference correctClassLoader(MethodReference reference) {
        IClass klass = rawCallGraph.getClassHierarchy().lookupClass(reference.getDeclaringClass());

        if (klass == null) {
            return MethodReference.findOrCreate(ClassLoaderReference.Extension,
                    reference.getDeclaringClass().getName().toString(),
                    reference.getName().toString(),
                    reference.getDescriptor().toString());
        }

        return MethodReference.findOrCreate(klass.getReference(), reference.getSelector());

    }

    /**
     * Get call type.
     *
     * @param callSite Call site
     * @return Call type
     */
    private CallType getInvocationLabel(CallSiteReference callSite) {

        switch ((IInvokeInstruction.Dispatch) callSite.getInvocationCode()) {
            case INTERFACE:
                return CallType.INTERFACE;
            case VIRTUAL:
                return CallType.VIRTUAL;
            case SPECIAL:
                return CallType.SPECIAL;
            case STATIC:
                return CallType.STATIC;
            default:
                return CallType.UNKNOWN;
        }
    }
}
