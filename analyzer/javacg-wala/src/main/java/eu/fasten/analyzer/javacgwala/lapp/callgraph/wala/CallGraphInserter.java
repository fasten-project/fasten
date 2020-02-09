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


package eu.fasten.analyzer.javacgwala.lapp.callgraph.wala;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import eu.fasten.analyzer.javacgwala.lapp.call.Call;
import eu.fasten.analyzer.javacgwala.lapp.core.Method;

import java.util.Iterator;
import java.util.function.Predicate;

public class CallGraphInserter {


    private final CallGraph cg;
    private final IClassHierarchy cha;
    private final LappPackageBuilder lappPackageBuilder;

    /**
     * Constructs a call graph inserter for a specific lapp package builder.
     *
     * @param cg                 Raw call graph
     * @param cha                Class hierarchy
     * @param lappPackageBuilder Lapp package builder to insert call graph into
     */
    public CallGraphInserter(CallGraph cg, IClassHierarchy cha,
                             LappPackageBuilder lappPackageBuilder) {
        this.cg = cg;
        this.cha = cha;
        this.lappPackageBuilder = lappPackageBuilder;
    }


    /**
     * Add call graph to a lapp package builder.
     */
    public void insertCallGraph() {
        for (CGNode node : this.cg) {
            MethodReference nodeReference = node.getMethod().getReference();

            if (applicationClassLoaderFilter.test(node)) {
                // Ignore everything not in the application classloader
                continue;
            }
            Method methodNode = lappPackageBuilder.addMethod(nodeReference,
                    LappPackageBuilder.MethodType.IMPLEMENTATION);


            for (Iterator<CallSiteReference> callSites = node.iterateCallSites();
                 callSites.hasNext(); ) {
                CallSiteReference callSite = callSites.next();

                /* If the target is unknown, is gets the Application loader by default.
                   We would like this to be the
                   Extension loader, that way it is easy to filter them out later.
                   */
                MethodReference targetWithCorrectClassLoader = correctClassLoader(callSite
                        .getDeclaredTarget());

                Method targetMethodNode = lappPackageBuilder
                        .addMethod(targetWithCorrectClassLoader);
                lappPackageBuilder.addCall(methodNode, targetMethodNode,
                        getInvocationLabel(callSite));
            }

        }
    }

    private Predicate<CGNode> applicationClassLoaderFilter = node -> {
        return !node.getMethod()
                .getDeclaringClass()
                .getClassLoader()
                .getReference()
                .equals(ClassLoaderReference.Application);
    };

    private Call.CallType getInvocationLabel(CallSiteReference callsite) {

        switch ((IInvokeInstruction.Dispatch) callsite.getInvocationCode()) {
            case INTERFACE:
                return Call.CallType.INTERFACE;
            case VIRTUAL:
                return Call.CallType.VIRTUAL;
            case SPECIAL:
                return Call.CallType.SPECIAL;
            case STATIC:
                return Call.CallType.STATIC;
            default:
                return Call.CallType.UNKNOWN;
        }
    }

    private MethodReference correctClassLoader(MethodReference reference) {
        IClass klass = cha.lookupClass(reference.getDeclaringClass());

        if (klass == null) {
            return MethodReference.findOrCreate(ClassLoaderReference.Extension,
                    reference.getDeclaringClass().getName().toString(),
                    reference.getName().toString(),
                    reference.getDescriptor().toString());
        }

        return MethodReference.findOrCreate(klass.getReference(), reference.getSelector());

    }
}
