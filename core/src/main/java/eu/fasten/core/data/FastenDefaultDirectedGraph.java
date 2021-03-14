package eu.fasten.core.data;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jgrapht.graph.DefaultDirectedGraph;

import java.io.Serializable;
import java.util.stream.Collectors;

public class FastenDefaultDirectedGraph extends DefaultDirectedGraph<Long, LongLongPair> implements DirectedGraph, Serializable {

    public FastenDefaultDirectedGraph(Class<? extends LongLongPair> edgeClass) {
        super(edgeClass);
    }

    @Override
    public int numNodes() {
        return this.vertexSet().size();
    }

    @Override
    public long numArcs() {
        return this.edgeSet().size();
    }

    @Override
    public LongList successors(long node) {
        return new LongArrayList(this.outgoingEdgesOf(node).stream().map(e -> e.rightLong()).collect(Collectors.toList()));
    }

    @Override
    public LongList predecessors(long node) {
        return new LongArrayList(this.incomingEdgesOf(node).stream().map(e -> e.leftLong()).collect(Collectors.toList()));

    }

    @Override
    public LongSet nodes() {
        return new LongOpenHashSet(this.vertexSet());
    }

    @Override
    public LongSet externalNodes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isInternal(long node) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isExternal(long node) {
        throw new UnsupportedOperationException();
    }

    @Override
    public LongIterator iterator() {
        return this.nodes().iterator();
    }
}
