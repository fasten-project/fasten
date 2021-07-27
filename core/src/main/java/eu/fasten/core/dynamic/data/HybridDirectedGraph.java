package eu.fasten.core.dynamic.data;

import eu.fasten.core.data.DirectedGraph;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import java.io.Serializable;
import java.util.Map;
import java.util.stream.Collectors;

public class HybridDirectedGraph extends DefaultDirectedGraph<Long, LongLongPair> implements DirectedGraph, Serializable {

    private final Map<LongLongPair, CallOrigin> callOriginMap;

    public HybridDirectedGraph(final Map<LongLongPair, CallOrigin> callOriginMap) {
        this(HybridEdge.class, callOriginMap);
    }

    public HybridDirectedGraph(Class<? extends LongLongPair> edgeClass,
                               final Map<LongLongPair, CallOrigin> callOriginMap) {
        super(edgeClass);
        this.callOriginMap = callOriginMap;
    }

    public enum CallOrigin {
        dynamicCg,
        staticCg,
        staticAndDynamicCgs
    }

    public CallOrigin getCallOrigin(LongLongPair call) {
        return this.callOriginMap.get(LongLongPair.of(call.leftLong(), call.rightLong()));
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
        return LongSet.of();
    }

    @Override
    public boolean isInternal(long node) {
        return false;
    }

    @Override
    public boolean isExternal(long node) {
        return false;
    }


    @Override
    public LongIterator iterator() {
        return this.nodes().iterator();
    }


    public boolean addInternalNode(long node) {
        return addVertex(node);
    }

    public boolean removeVertex(long node) {
        return super.removeVertex(node);
    }

    public static class HybridEdge extends DefaultEdge implements LongLongPair {

        @Override
        public long leftLong() {
            return (long) this.getSource();
        }

        @Override
        public long rightLong() {
            return (long) this.getTarget();
        }

    }
}
