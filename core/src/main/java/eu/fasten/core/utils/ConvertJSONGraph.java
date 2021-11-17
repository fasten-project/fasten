
package eu.fasten.core.utils;

import org.jgrapht.nio.json.JSONImporter; 
import org.jgrapht.graph.DefaultDirectedGraph;

public class ConvertJSONGraph {
	public static void main(String[] args) {
		var g = new DefaultDirectedGraph(Integer.class);
		new JSONImporter().importGraph(g, System.in);
		System.out.println(g.vertexSet().size());
	}
}
