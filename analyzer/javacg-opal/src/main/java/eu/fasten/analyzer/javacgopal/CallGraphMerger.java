package eu.fasten.analyzer.javacgopal;

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.RevisionCallGraph;

import java.util.List;

public class CallGraphMerger {

    public static CallGraphMerger resolve(MavenCoordinate coordinate){

        var PDN = MavenResolver.resolveDependencies(coordinate.getCoordinate());

        List<List<FastenURI>> depencencyList = getDependenciesURI(PDN);

        for (List<FastenURI> fastenURIS : depencencyList) {
                List<ProposalRevisionCallGraph> revisionCallGraphs = loadRevisionCallGraph(fastenURIS);
                List<ProposalRevisionCallGraph> resolvedCallGraphs = mergeCallGraphs(revisionCallGraphs);
        }

        return null;
    }

    public static List<ProposalRevisionCallGraph> mergeCallGraphs(List<ProposalRevisionCallGraph> revisionCallGraphs) {
        for (int i =0 ; i < revisionCallGraphs.size() ; i++) {
            for (FastenURI[] fastenURIS : revisionCallGraphs.get(i).graph) {
                var callee = fastenURIS[1];
                if(callee.toString().contains("SomeDependency")){
                    for (ProposalRevisionCallGraph proposalRevisionCallGraph : revisionCallGraphs.subList(i,revisionCallGraphs.size()-1)) {
//                       var v = proposalRevisionCallGraph.classHierarchy.supertypes().filter(JavaToScalaConverter.asScalaFunction1(j->j.toString().contains(callee.getRawNamespace())));
                        System.out.println();
                    }
                }
            }
        }
        return null;
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

    public static CallGraphMerger resolve(List<List<RevisionCallGraph.Dependency>> packageDependencyNetwork){
        return null;
    }
}
