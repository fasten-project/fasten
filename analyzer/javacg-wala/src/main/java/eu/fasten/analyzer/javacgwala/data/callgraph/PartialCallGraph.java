package eu.fasten.analyzer.javacgwala.data.callgraph;

import eu.fasten.analyzer.javacgwala.data.MavenResolvedCoordinate;
import eu.fasten.analyzer.javacgwala.data.core.Call;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.RevisionCallGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PartialCallGraph {

    /**
     * List of maven coordinates of dependencies.
     */
    private final List<MavenResolvedCoordinate> coordinates;

    /**
     * Calls that their target's packages are not still known and need to be resolved in
     * later on, e.g. in a merge phase.
     */
    private List<Call> unresolvedCalls;

    /**
     * Calls that their sources and targets are fully resolved.
     */
    private List<Call> resolvedCalls;


    //TODO: add class hierarchy analysis

    public PartialCallGraph(List<MavenResolvedCoordinate> coordinates) {
        this.resolvedCalls = new ArrayList<>();
        this.unresolvedCalls = new ArrayList<>();
        this.coordinates = coordinates;
    }

    public List<Call> getUnresolvedCalls() {
        return unresolvedCalls;
    }

    public List<Call> getResolvedCalls() {
        return resolvedCalls;
    }

    public List<MavenResolvedCoordinate> getCoordinates() {
        return coordinates;
    }

    public void addResolvedCall(Call call) {
        if (!this.resolvedCalls.contains(call)) {
            this.resolvedCalls.add(call);
        }
    }

    public void addUnresolvedCall(Call call) {
        if (!this.unresolvedCalls.contains(call)) {
            this.unresolvedCalls.add(call);
        }
    }

    /**
     * Convert a {@link PartialCallGraph} to FASTEN compatible format.
     *
     * @return FASTEN call graph
     */
    public RevisionCallGraph toRevisionCallGraph(int date) {

        List<List<RevisionCallGraph.Dependency>> depArray = new ArrayList<>(coordinates.size());

        for (MavenResolvedCoordinate dependency : coordinates) {
            depArray.add(toFastenDep(dependency));
        }

        var graph = toURIGraph();

        return new RevisionCallGraph(
                "mvn",
                coordinates.get(0).groupId + "." + coordinates.get(0).artifactId,
                coordinates.get(0).version,
                date, depArray, graph
        );
    }

    /**
     * Converts MavenResolvedCoordinate to a list of FASTEN compatible dependencies.
     *
     * @param coordinate MavenResolvedCoordinate to convert
     * @return List of FASTEN compatible dependencies
     */
    private static List<RevisionCallGraph.Dependency> toFastenDep(MavenResolvedCoordinate coordinate) {
        var constraints = new RevisionCallGraph.Constraint(coordinate.version, coordinate.version);
        var result = new ArrayList<RevisionCallGraph.Dependency>();
        result.add(new RevisionCallGraph.Dependency("mvn",
                coordinate.groupId + ":" + coordinate.artifactId,
                Collections.singletonList(constraints)
        ));
        return result;
    }

    /**
     * Converts all nodes {@link Call} of a Wala call graph to URIs.
     *
     * @return A graph of all nodes in URI format represented in a List of {@link FastenURI}
     */
    private ArrayList<FastenURI[]> toURIGraph() {

        var graph = new ArrayList<FastenURI[]>();

        for (Call resolvedCall : resolvedCalls) {
            addCall(graph, resolvedCall);
        }

        for (Call unresolvedCall : unresolvedCalls) {
            addCall(graph, unresolvedCall);
        }

        return graph;
    }

    /**
     * Add call to a call graph.
     *
     * @param graph Call graph to add a call to
     * @param call  Call to add
     */
    private static void addCall(ArrayList<FastenURI[]> graph, Call call) {

        var uriCall = call.toURICall();

        if (uriCall[0] != null && uriCall[1] != null && !graph.contains(uriCall)) {
            graph.add(uriCall);
        }
    }
}
