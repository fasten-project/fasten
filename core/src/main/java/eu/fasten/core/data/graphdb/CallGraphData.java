package eu.fasten.core.data.graphdb;

import java.util.Arrays;
import java.util.Properties;

import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.GOV3LongFunction;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.webgraph.ImmutableGraph;

/** Instances of this class contain the data relative to a call graph that are stored in the database. */
public class CallGraphData implements DirectedGraph {
	/** The call graph. */
	private final ImmutableGraph graph;
	/** The transpose graph. */
	private final ImmutableGraph transpose;
	/** Properties (in the sense of {@link ImmutableGraph}) of the call graph. */
	public final Properties graphProperties;
	/** Properties (in the sense of {@link ImmutableGraph}) of the transpose graph. */
	public final Properties transposeProperties;
	/** Maps LIDs to GIDs. */
	public final long[] LID2GID;
	/** Inverse to {@link #LID2GID}: maps GIDs to LIDs. */
	public final GOV3LongFunction GID2LID;
	/** A cache copy of the set of nodes. */
	private final LongOpenHashSet nodes;
	/** A cached copy of the set of external nodes. */
	private final LongOpenHashSet externalNodes;
	/** The size in bytes of the RocksDB entry. */
	public final int size;

	public CallGraphData(final ImmutableGraph graph, final ImmutableGraph transpose, final Properties graphProperties, final Properties transposeProperties, final long[] LID2GID, final GOV3LongFunction GID2LID, final int nInternal, final int size) {
		super();
		this.graph = graph;
		this.transpose = transpose;
		this.graphProperties = graphProperties;
		this.transposeProperties = transposeProperties;
		this.LID2GID = LID2GID;
		this.GID2LID = GID2LID;
		this.externalNodes = new LongOpenHashSet(Arrays.copyOfRange(LID2GID, nInternal, LID2GID.length));
		this.size = size;
		this.nodes = new LongOpenHashSet(LID2GID);
	}

	@Override
	public int numNodes() {
		return graph.numNodes();
	}

	@Override
	public long numArcs() {
		return graph.numArcs();
	}

	@Override
	public LongList successors(final long node) {
		final int lid = (int)GID2LID.getLong(node);
		if (lid < 0 || lid >= numNodes() || LID2GID[lid] != node) throw new IllegalArgumentException("GID " + node + " does not exist");
		final int outdegree = graph.outdegree(lid);
		final LongArrayList gidList = new LongArrayList(outdegree);
		for (final int s: graph.successorArray(lid)) gidList.add(LID2GID[s]);
		return gidList;
	}

	@Override
	public LongList predecessors(final long node) {
		final int lid = (int)GID2LID.getLong(node);
		if (lid < 0 || lid >= numNodes() || LID2GID[lid] != node) throw new IllegalArgumentException("GID " + node + " does not exist");
		final int indegree = transpose.outdegree(lid);
		final LongArrayList gidList = new LongArrayList(indegree);
		for (final int s: transpose.successorArray(lid)) gidList.add(LID2GID[s]);
		return gidList;
	}

	@Override
	public LongSet nodes() {
		return nodes;
	}

	@Override
	public LongIterator iterator() {
		return LongIterators.wrap(LID2GID);
	}

	@Override
	public LongSet externalNodes() {
		return externalNodes;
	}

	@Override
	public boolean isExternal(final long node) {
		return externalNodes.contains(node);
	}

	@Override
	public boolean isInternal(final long node) {
		return !externalNodes.contains(node);
	}

	public ImmutableGraph rawGraph() {
		return graph;
	}

	public ImmutableGraph rawTranspose() {
		return transpose;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		for (final long gid : LID2GID) {
			sb.append(gid).append(": ").append(successors(gid));
		}
		return sb.toString();
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (!(o instanceof DirectedGraph)) return false;
		final DirectedGraph graph = (DirectedGraph)o;
		if (numNodes() != graph.numNodes()) return false;
		if (!new LongOpenHashSet(nodes()).equals(new LongOpenHashSet(graph.nodes()))) return false;
		for(final long node: nodes()) {
			if (!new LongOpenHashSet(successors(node)).equals(new LongOpenHashSet(graph.successors(node)))) return false;
			if (!new LongOpenHashSet(predecessors(node)).equals(new LongOpenHashSet(graph.predecessors(node)))) return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		long h = HashCommon.mix(numNodes()) ^ HashCommon.murmurHash3(numArcs());
		for (final long node : nodes()) {
			for (final long succ : successors(node)) h ^= HashCommon.murmurHash3(h ^ succ);
			for (final long pred : predecessors(node)) h ^= HashCommon.murmurHash3(h ^ pred);
		}
		return (int)(h ^ h >>> 32);
	}
}
