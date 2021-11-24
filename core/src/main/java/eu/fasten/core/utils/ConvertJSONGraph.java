
package eu.fasten.core.utils;

import java.io.IOException;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.nio.json.JSONImporter;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public class ConvertJSONGraph {
	public static void main(String[] args) {
		final Graph<Long, DefaultEdge> g = new DefaultDirectedGraph<>(DefaultEdge.class);
		final JSONImporter<Long, DefaultEdge> importer = new JSONImporter<>();
		final Object2ObjectMap<String, LongOpenHashSet> m = new Object2ObjectOpenHashMap<>();
		
		importer.setVertexWithAttributesFactory((stringGid, map) -> { 
			Long gid = Long.valueOf(stringGid);
			System.out.println(gid);
			String revision = map.get("product").toString();
			var s = m.get(revision);
			if (s == null) {
				s = new LongOpenHashSet();
				m.put(revision, s);
			}
			s.add(gid.longValue());
			return gid; 
		});
		importer.importGraph(g, System.in);
		System.out.println(g.vertexSet().size());
		System.out.println(m.keySet());
		long c = 0;
		for(String k: m.keySet()) {
			System.out.println(k + ": " + m.get(k).size());
			c += m.get(k).size();
		}
		System.out.println(c + " " + g.iterables().vertexCount());
		
	}
}
