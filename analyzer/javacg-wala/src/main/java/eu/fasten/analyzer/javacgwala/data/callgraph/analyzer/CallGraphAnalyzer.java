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
import eu.fasten.analyzer.javacgwala.data.core.InternalMethod;
import eu.fasten.analyzer.javacgwala.data.core.Method;
import eu.fasten.core.data.FastenJavaURI;
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
    public CallGraphAnalyzer(final CallGraph rawCallGraph,
                             final PartialCallGraph partialCallGraph,
                             final AnalysisContext analysisContext) {
        this.rawCallGraph = rawCallGraph;
        this.partialCallGraph = partialCallGraph;
        this.analysisContext = analysisContext;
        this.classHierarchyAnalyzer =
                new ClassHierarchyAnalyzer(rawCallGraph, partialCallGraph, analysisContext);
    }

    /**
     * Iterate over nodes in Wala call graph and add calls that "belong" to application class
     * loader to lists of resolved / unresolved calls of partial call graph.
     */
    public void resolveCalls() {
        for (final CGNode node : this.rawCallGraph) {
            final var nodeReference = node.getMethod().getReference();

            if (applicationClassLoaderFilter.test(node)) {
                continue;
            }

            final var methodNode = analysisContext.findOrCreate(nodeReference);

            for (final var callSites = node.iterateCallSites(); callSites.hasNext(); ) {
                final var callSite = callSites.next();

                final var targetWithCorrectClassLoader =
                        correctClassLoader(callSite.getDeclaredTarget());

                final var targetMethodNode =
                        analysisContext.findOrCreate(targetWithCorrectClassLoader);

                addCall(methodNode, targetMethodNode, getInvocationLabel(callSite));
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
    private void addCall(final Method source, final Method target, final CallType callType)
            throws NullPointerException {
        var sourceID = classHierarchyAnalyzer.addMethodToCHA(source,
                source.getReference().getDeclaringClass());
        if (source instanceof InternalMethod && target instanceof InternalMethod) {
            var targetID = classHierarchyAnalyzer.addMethodToCHA(target,
                    target.getReference().getDeclaringClass());
            partialCallGraph.addInternalCall(sourceID, targetID);

        } else {
            partialCallGraph.addExternalCall(sourceID,
                    new FastenJavaURI("//" + target.toCanonicalSchemalessURI()), callType);
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
    private MethodReference correctClassLoader(final MethodReference reference) {
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
    private CallType getInvocationLabel(final CallSiteReference callSite) {

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
