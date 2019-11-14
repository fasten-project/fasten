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

import org.opalj.AnalysisModes;
import org.opalj.ai.analyses.cg.CallGraphFactory;
import org.opalj.ai.analyses.cg.ComputedCallGraph;
import org.opalj.ai.analyses.cg.CHACallGraphAlgorithmConfiguration;


import org.opalj.br.Method;
import org.opalj.br.analyses.AnalysisModeConfigFactory;
import org.opalj.br.analyses.Project;
import org.opalj.collection.immutable.ConstArray;
import org.opalj.fpcf.analyses.cg.cha.CHACallGraphKey$;
import scala.collection.Iterable;
import scala.collection.JavaConversions;
import scala.collection.Map;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;


public class CallGraphGenerator {

    static PartialCallGraph generatePartialCallGraph(File artifactFile) {

        Project artifactInOpalFormat = Project.apply(artifactFile);

        ComputedCallGraph callGraphInOpalFormat = CallGraphFactory.create(artifactInOpalFormat,
            JavaToScalaConverter.asScalaFunction0(findEntryPoints(artifactInOpalFormat.allMethodsWithBody())),
            new CHACallGraphAlgorithmConfiguration(artifactInOpalFormat, true));

//        ComputedCallGraph callGraphInOpalFormat = (ComputedCallGraph) AnalysisModeConfigFactory.resetAnalysisMode(artifactInOpalFormat, AnalysisModes.OPA(),false).get(CHACallGraphKey$.MODULE$);

        return ToPartialGraph(callGraphInOpalFormat);

    }

    private static PartialCallGraph ToPartialGraph(ComputedCallGraph callGraphInOpalFormat) {

        PartialCallGraph partialCallGraph = new PartialCallGraph();

        callGraphInOpalFormat.callGraph().foreachCallingMethod(JavaToScalaConverter.asScalaFunction2(setResolvedCalls(partialCallGraph.getResolvedCalls())));

        partialCallGraph.setUnresolvedCalls(new ArrayList<>(JavaConversions.asJavaCollection(callGraphInOpalFormat.unresolvedMethodCalls().toList())));

        partialCallGraph.setClassHierarchy(callGraphInOpalFormat.callGraph().project().classHierarchy());

        return partialCallGraph;

    }

    private static ScalaFunction2 setResolvedCalls(List<ResolvedCall> resolvedCallsList) {
        return (Method callerMethod, Map<Object, Iterable<Method>> calleeMethodsObject) -> {
            Collection<Iterable<Method>> calleeMethodsCollection =
                JavaConversions.asJavaCollection(calleeMethodsObject.valuesIterator().toList());

            List<Method> calleeMethodsList = new ArrayList<>();
            for (Iterable<Method> i : calleeMethodsCollection) {
                for (Method j : JavaConversions.asJavaIterable(i)) {
                    calleeMethodsList.add(j);
                }
            }
            return resolvedCallsList.add(new ResolvedCall(callerMethod, calleeMethodsList));
        };
    }

    private static Iterable<Method> findEntryPoints(ConstArray allMethods) {

        return (Iterable<Method>) allMethods.filter(JavaToScalaConverter.asScalaFunction1((Object method) -> (!((Method) method).isAbstract()) && !((Method) method).isPrivate()));

    }

}
