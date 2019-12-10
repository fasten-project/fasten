package eu.fasten.analyzer.javacgopal;

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.RevisionCallGraph;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProposalRevisionCallGraph extends RevisionCallGraph {

    static class Type{
        List<FastenURI> methods;
        List<FastenURI> superClasses;
        List<FastenURI> superInterfaces;

        public Type(List<FastenURI> methods, List<FastenURI> superClasses, List<FastenURI> superInterfaces) {
            this.methods = methods;
            this.superClasses = superClasses;
            this.superInterfaces = superInterfaces;
        }
    }

    private Map<FastenURI,Type> classHierarchy;

    public ProposalRevisionCallGraph(String forge, String product, String version, long timestamp, List<List<Dependency>> depset, ArrayList<FastenURI[]> graph, Map<FastenURI,Type> classHierarchy) {
        super(forge, product, version, timestamp, depset, graph);
        this.classHierarchy = classHierarchy;
    }

    public ProposalRevisionCallGraph(JSONObject json, boolean ignoreConstraints) throws JSONException, URISyntaxException {
        super(json, ignoreConstraints);
    }
}
