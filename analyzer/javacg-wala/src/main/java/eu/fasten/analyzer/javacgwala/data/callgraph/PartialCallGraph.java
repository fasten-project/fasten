package eu.fasten.analyzer.javacgwala.data.callgraph;

import eu.fasten.analyzer.javacgwala.data.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.core.Call;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.RevisionCallGraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartialCallGraph {

    /**
     * List of maven coordinates of dependencies.
     */
    private final MavenCoordinate coordinate;

    /**
     * Calls that their target's packages are not still known and need to be resolved in
     * later on, e.g. in a merge phase.
     */
    private final List<Call> unresolvedCalls;

    /**
     * Calls that their sources and targets are fully resolved.
     */
    private final List<Call> resolvedCalls;

    /**
     * Class hierarchy.
     */
    private final Map<FastenURI, ExtendedRevisionCallGraph.Type> classHierarchy;

    /**
     * Construct a partial call graph with empty lists of resolved / unresolved calls.
     *
     * @param coordinate List of {@link MavenCoordinate}
     */
    public PartialCallGraph(MavenCoordinate coordinate) {
        this.resolvedCalls = new ArrayList<>();
        this.unresolvedCalls = new ArrayList<>();
        this.classHierarchy = new HashMap<>();
        this.coordinate = coordinate;
    }

    public List<Call> getUnresolvedCalls() {
        return unresolvedCalls;
    }

    public List<Call> getResolvedCalls() {
        return resolvedCalls;
    }

    public Map<FastenURI, ExtendedRevisionCallGraph.Type> getClassHierarchy() {
        return classHierarchy;
    }


    /**
     * Add a new call to the list of resolved calls.
     *
     * @param call New call
     */
    public void addResolvedCall(Call call) {
        if (!this.resolvedCalls.contains(call)) {
            this.resolvedCalls.add(call);
        }
    }

    /**
     * Add a new call to the list of unresolved calls.
     *
     * @param call New call
     */
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
    public RevisionCallGraph toRevisionCallGraph(long date) {

        List<List<RevisionCallGraph.Dependency>> depArray =
                MavenCoordinate.MavenResolver.resolveDependencies(coordinate.getCoordinate());


        var graph = toURIGraph();

        return new RevisionCallGraph(
                "mvn",
                coordinate.getProduct(),
                coordinate.getVersionConstraint(),
                date, depArray, graph
        );
    }

    /**
     * Convert a {@link PartialCallGraph} to FASTEN compatible format.
     *
     * @return FASTEN call graph
     */
    public RevisionCallGraph toExtendedRevisionCallGraph(long date) {

        List<List<RevisionCallGraph.Dependency>> depArray =
                MavenCoordinate.MavenResolver.resolveDependencies(coordinate.getCoordinate());


        var graph = toURIGraph();

        return new ExtendedRevisionCallGraph(
                "mvn",
                coordinate.getProduct(),
                coordinate.getVersionConstraint(),
                date, depArray, graph, classHierarchy
        );
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
