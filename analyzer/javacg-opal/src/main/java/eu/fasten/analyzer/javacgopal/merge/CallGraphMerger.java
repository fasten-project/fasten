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

package eu.fasten.analyzer.javacgopal.merge;

import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.callgraph.ExtendedRevisionCallGraph;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.RevisionCallGraph;

import java.util.List;

public class CallGraphMerger {

    public static CallGraphMerger resolve(MavenCoordinate coordinate) {

        var PDN = MavenCoordinate.MavenResolver.resolveDependencies(coordinate.getCoordinate());

        List<List<FastenURI>> depencencyList = getDependenciesURI(PDN);

        for (List<FastenURI> fastenURIS : depencencyList) {
            List<ExtendedRevisionCallGraph> revisionCallGraphs = loadRevisionCallGraph(fastenURIS);
//                List<ExtendedRevisionCallGraph> resolvedCallGraphs = mergeCallGraphs(revisionCallGraphs);
        }

        return null;
    }

    public static ExtendedRevisionCallGraph mergeCallGraph(ExtendedRevisionCallGraph artifact, List<ExtendedRevisionCallGraph> dependencies) {

        for (FastenURI[] fastenURIS : artifact.graph) {
            var source = fastenURIS[0];
            var target = fastenURIS[1];
            var isSuperClassMethod = artifact.getClassHierarchy().get(getTypeURI(source)).getSuperClasses().contains(getTypeURI(target));
            nextCall:

            //Foreach unresolved call
            if (target.toString().startsWith("///")) {

                //Go through all dependencies
                for (ExtendedRevisionCallGraph dependency : dependencies) {
                    nextDependency:
                    //Check whether this method is inside the dependency
                    if (dependency.getClassHierarchy().containsKey(getTypeURI(target))) {
                        if (dependency.getClassHierarchy().get(getTypeURI(target)).getMethods().contains(FastenURI.create(target.getRawPath()))) {
                            var resolvedMethod = target.toString().replace("///","//" + dependency.product + "/");
                            //Check if this call is related to a super class
                            if (isSuperClassMethod) {
                                //Find that super class. in case there are two, pick the first one since the order of instantiation matters
                                for (FastenURI superClass : artifact.getClassHierarchy().get(getTypeURI(source)).getSuperClasses()) {
                                    //Check if this dependency contains the super class that we want
                                    if (dependency.getClassHierarchy().containsKey(superClass)) {
                                        fastenURIS[1] = new FastenJavaURI(resolvedMethod);
                                        break nextCall;
                                    } else {
                                        break nextDependency;
                                    }
                                }
                            }
                            else {
                                fastenURIS[1] = new FastenJavaURI(resolvedMethod);
                            }
                        }
                    }
                }
            }
        }


        return artifact;
    }


    private static FastenURI getTypeURI(FastenURI callee) {
        return new FastenJavaURI("/" + callee.getNamespace() + "/" + callee.getEntity().substring(0, callee.getEntity().indexOf(".")));
    }

    private static List<ExtendedRevisionCallGraph> loadRevisionCallGraph(List<FastenURI> uri) {

        //TODO load RevisionCallGraphs
        return null;
    }

    private static List<List<FastenURI>> getDependenciesURI(List<List<RevisionCallGraph.Dependency>> PDN) {

        List<List<FastenURI>> allProfilesDependenices = null;

        for (List<RevisionCallGraph.Dependency> dependencies : PDN) {

            List<FastenURI> oneProfileDependencies = null;
            for (RevisionCallGraph.Dependency dependency : dependencies) {
                oneProfileDependencies.add(FastenURI.create("fasten://mvn" + "!" + dependency.product + "$" + dependency.constraints));
            }

            allProfilesDependenices.add(oneProfileDependencies);
        }

        return allProfilesDependenices;
    }

    public static CallGraphMerger resolve(List<List<RevisionCallGraph.Dependency>> packageDependencyNetwork) {
        return null;
    }
}
