package eu.fasten.core.data;

import com.esotericsoftware.kryo.serializers.FieldSerializer;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLongMutablePair;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jgrapht.graph.DefaultDirectedGraph;

import java.io.Serializable;
import java.util.stream.Collectors;

public class FastenDefaultDirectedGraph extends DefaultDirectedGraph<Long, LongLongPair> implements DirectedGraph, Serializable {

    private final LongSet externalNodes;

    public FastenDefaultDirectedGraph() {
        this(LongLongPair.class);
    }

    public FastenDefaultDirectedGraph(Class<? extends LongLongPair> edgeClass) {
        super(edgeClass);
        externalNodes = new LongOpenHashSet();
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
        return new LongArrayList(
                this.outgoingEdgesOf(node).stream().map(LongLongPair::rightLong).collect(Collectors.toList()));
    }

    @Override
    public LongList predecessors(long node) {
        return new LongArrayList(
                this.incomingEdgesOf(node).stream().map(LongLongPair::leftLong).collect(Collectors.toList()));
    }

    @Override
    public LongSet nodes() {
        return new LongOpenHashSet(this.vertexSet());
    }

    @Override
    public LongSet externalNodes() {
        return this.externalNodes;
    }

    @Override
    public boolean isInternal(long node) {
        return this.containsVertex(node) && !isExternal(node);
    }

    @Override
    public boolean isExternal(long node) {
        return externalNodes.contains(node);
    }

    @Override
    public LongIterator iterator() {
        return this.nodes().iterator();
    }

    @Override
    public LongLongPair addEdge(Long source, Long target) {
        LongLongPair edge = new LongLongMutablePair(source, target);
        return this.addEdge(source, target, edge) ? edge : null;
    }

    public boolean addInternalNode(long node) {
        return addVertex(node);
    }

    public boolean addExternalNode(long node) {
        boolean result = addVertex(node);
        if(result) {
            externalNodes.add(node);
        }
        return result;
    }

    public boolean removeVertex(long node) {
        boolean result = super.removeVertex(node);
        if(result) {
            externalNodes.remove(node);
        }
        return result;
    }
}
