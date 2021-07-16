package eu.fasten.core.dynamic;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.dynamic.data.DynamicJavaCG;
import eu.fasten.core.dynamic.data.HybridDirectedGraph;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLongImmutablePair;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

public class StaticDynamicCGCombinerTest {

    @Test
    public void combineCGsTest() {
        var graphBuilder = new ArrayImmutableDirectedGraph.Builder();
        graphBuilder.addInternalNode(0);
        graphBuilder.addInternalNode(1);
        graphBuilder.addInternalNode(2);
        graphBuilder.addInternalNode(3);
        graphBuilder.addArc(1, 2);
        graphBuilder.addArc(0, 3);
        var staticCg = graphBuilder.build();
        var staticCgUriMap = Map.of(
                0L, "/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoidType",
                1L, "/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoidType",
                2L, "/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoidType",
                3L, "/java.lang/Object.Object()VoidType"
        );
        var dynamicMethods = new Long2ObjectOpenHashMap<String>(3);
        dynamicMethods.put(3L, "/java.lang/Object.Object()VoidType");
        dynamicMethods.put(1L, "/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoidType");
        dynamicMethods.put(2L, "/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoidType");
        var dynamicCalls = new ObjectOpenHashSet<LongLongPair>();
        dynamicCalls.add(LongLongImmutablePair.of(1, 2));
        dynamicCalls.add(LongLongImmutablePair.of(2, 3));
        var dynamicCg = new DynamicJavaCG(dynamicMethods, dynamicCalls);

        var combiner = new StaticDynamicCGCombiner(staticCg, staticCgUriMap, dynamicCg);
        var result = combiner.combineCGs();
        var uris = combiner.getAllUrisMap();

        var expectedMethods = Set.of(
                "/java.lang/Object.Object()VoidType",
                "/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoidType",
                "/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoidType",
                "/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoidType"
        );
        var expectedCalls = Set.of(
                Pair.of("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoidType",
                        "/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoidType"),
                Pair.of("/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoidType",
                        "/java.lang/Object.Object()VoidType"),
                Pair.of("/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoidType",
                        "/java.lang/Object.Object()VoidType")
        );

        var resultMethods = new ObjectOpenHashSet<String>(result.numNodes());
        result.nodes().forEach(n -> resultMethods.add(uris.get(n)));
        assertEquals(expectedMethods, resultMethods);

        var resultCalls = new ObjectOpenHashSet<ObjectObjectImmutablePair<String, String>>(result.edgeSet().size());
        result.edgeSet().forEach(e -> resultCalls.add(ObjectObjectImmutablePair.of(
                uris.get(e.firstLong()),
                uris.get(e.secondLong())
        )));
        assertEquals(expectedCalls, resultCalls);

        var inverseUris = new Object2LongOpenHashMap<String>(uris.size());
        uris.forEach((k, v) -> inverseUris.put(v, k.longValue()));
        assertEquals(HybridDirectedGraph.CallOrigin.staticAndDynamicCgs, result.getCallOrigin(LongLongPair.of(
                inverseUris.getLong("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoidType"),
                inverseUris.getLong("/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoidType")
        )));
        assertEquals(HybridDirectedGraph.CallOrigin.staticCg, result.getCallOrigin(LongLongPair.of(
                inverseUris.getLong("/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoidType"),
                inverseUris.getLong("/java.lang/Object.Object()VoidType")
        )));
        assertEquals(HybridDirectedGraph.CallOrigin.dynamicCg, result.getCallOrigin(LongLongPair.of(
                inverseUris.getLong("/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoidType"),
                inverseUris.getLong("/java.lang/Object.Object()VoidType")
        )));
    }
}
