package eu.fasten.analyzer.javacgopal;

import edu.uci.ics.jung.graph.util.Pair;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.RevisionCallGraph;
import org.json.JSONException;
import org.json.JSONObject;
import org.opalj.br.ObjectType;

import java.net.URISyntaxException;
import java.util.*;

public class ProposalRevisionCallGraph extends RevisionCallGraph {

    Map<FastenURI,Type> classHierarchy;

    /**
     * Type can be a class or interface that inherits (implements) from others or implements methods.
     */
    static class Type{
        //Methods that this type implements
        List<FastenURI> methods;
        //Classes that this type inherits from in the order of instantiation.
        LinkedList<FastenURI> superClasses;
        //Interfaces that this type or its super classes implement.
        List<FastenURI> superInterfaces;

        public Type(List<FastenURI> methods, LinkedList<FastenURI> superClasses, List<FastenURI> superInterfaces) {
            this.methods = methods;
            this.superClasses = superClasses;
            this.superInterfaces = superInterfaces;
        }
    }

    public ProposalRevisionCallGraph(String forge, String product, String version, long timestamp, List<List<Dependency>> depset, ArrayList<FastenURI[]> graph, Map<FastenURI,Type> classHierarchy) {
        super(forge, product, version, timestamp, depset, graph);
        this.classHierarchy = classHierarchy;
    }

    public ProposalRevisionCallGraph(JSONObject json, boolean ignoreConstraints) throws JSONException, URISyntaxException {
        super(json, ignoreConstraints);
    }
}
