package eu.fasten.core.maven;

import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.data.Revision;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;

public class MavenStats {

    public static void main(String[] args) throws Exception {
        var dbContext = PostgresConnector.getDSLContext("jdbc:postgresql://localhost:5432/fasten_java", "fastenro", true);
        var resolver = new GraphMavenResolver();
        resolver.buildDependencyGraph(dbContext, args[0]);
        var dependencies = new HashMap<Revision, Set<Revision>>(GraphMavenResolver.dependencyGraph.vertexSet().size());
        for (var revision : GraphMavenResolver.dependencyGraph.vertexSet()) {
            try {
                var depSet = resolver.resolveDependencies(revision, dbContext, true);
                dependencies.put(revision, depSet);
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
        var top10dependencies = new HashMap<Revision, Set<Revision>>(10);
        dependencies.entrySet().stream().sorted(Comparator.comparingInt(e -> -e.getValue().size())).limit(10).forEachOrdered(e -> top10dependencies.put(e.getKey(), e.getValue()));
        dependencies = null;
        var dependents = new HashMap<Revision, Set<Revision>>(GraphMavenResolver.dependentGraph.vertexSet().size());
        for (var revision : GraphMavenResolver.dependentGraph.vertexSet()) {
            try {
                var depSet = resolver.resolveDependents(revision, true);
                dependents.put(revision, depSet);
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
        var top10dependents = new HashMap<Revision, Set<Revision>>(10);
        dependents.entrySet().stream().sorted(Comparator.comparingInt(e -> -e.getValue().size())).limit(10).forEachOrdered(e -> top10dependents.put(e.getKey(), e.getValue()));
        System.out.println();
        System.out.println("10 artifacts with the most dependencies are:");
        for (var entry : top10dependencies.entrySet()) {
            System.out.println("\t" + entry.getKey().toString() + " -> " + entry.getValue().size() + "dependencies");
        }
        System.out.println("10 artifacts with the most dependents are:");
        for (var entry : top10dependents.entrySet()) {
            System.out.println("\t" + entry.getKey().toString() + " -> " + entry.getValue().size() + " dependents");
        }
    }
}
