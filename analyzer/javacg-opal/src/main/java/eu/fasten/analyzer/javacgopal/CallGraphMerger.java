package eu.fasten.analyzer.javacgopal;

import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.RevisionCallGraph;

import java.util.List;

public class CallGraphMerger {

    public static CallGraphMerger resolve(MavenCoordinate coordinate) {

        var PDN = MavenResolver.resolveDependencies(coordinate.getCoordinate());

        List<List<FastenURI>> depencencyList = getDependenciesURI(PDN);

        for (List<FastenURI> fastenURIS : depencencyList) {
            List<ProposalRevisionCallGraph> revisionCallGraphs = loadRevisionCallGraph(fastenURIS);
//                List<ProposalRevisionCallGraph> resolvedCallGraphs = mergeCallGraphs(revisionCallGraphs);
        }

        return null;
    }

    public static ProposalRevisionCallGraph mergeCallGraph(ProposalRevisionCallGraph artifact, List<ProposalRevisionCallGraph> dependencies) {

        for (FastenURI[] fastenURIS : artifact.graph) {
            var source = fastenURIS[0];
            var target = fastenURIS[1];
            var isSuperClassMethod = artifact.classHierarchy.get(getTypeURI(source)).superClasses.contains(getTypeURI(target));
            nextCall:

            //Foreach unresolved call
            if (target.toString().startsWith("///")) {

                //Go through all dependencies
                for (ProposalRevisionCallGraph dependency : dependencies) {
                    nextDependency:
                    //Check whether this method is inside the dependency
                    if (dependency.classHierarchy.containsKey(getTypeURI(target))) {
                        if (dependency.classHierarchy.get(getTypeURI(target)).methods.contains(FastenURI.create(target.getRawPath()))) {
                            var resolvedMethod = target.toString().replace("///","//" + dependency.product + "/");
                            //Check if this call is related to a super class
                            if (isSuperClassMethod) {
                                //Find that super class. in case there are two, pick the first one since the order of instantiation matters
                                for (FastenURI superClass : artifact.classHierarchy.get(getTypeURI(source)).superClasses) {
                                    //Check if this dependency contains the super class that we want
                                    if (dependency.classHierarchy.containsKey(superClass)) {
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

    private static List<ProposalRevisionCallGraph> loadRevisionCallGraph(List<FastenURI> uri) {

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
