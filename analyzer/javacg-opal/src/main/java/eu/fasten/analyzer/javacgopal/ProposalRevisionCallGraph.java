package eu.fasten.analyzer.javacgopal;

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.RevisionCallGraph;
import org.json.JSONException;
import org.json.JSONObject;
import org.opalj.br.ClassHierarchy;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class ProposalRevisionCallGraph extends RevisionCallGraph {

    public ClassHierarchy classHierarchy;

    public ProposalRevisionCallGraph(String forge, String product, String version, long timestamp, List<List<Dependency>> depset, ArrayList<FastenURI[]> graph, ClassHierarchy classHierarchy) {
        super(forge, product, version, timestamp, depset, graph);
        this.classHierarchy = classHierarchy;
    }

    public ProposalRevisionCallGraph(JSONObject json, boolean ignoreConstraints) throws JSONException, URISyntaxException {
        super(json, ignoreConstraints);
    }
}
