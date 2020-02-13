package eu.fasten.analyzer.javacgwala.data.callgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ibm.wala.types.Selector;
import eu.fasten.analyzer.javacgwala.data.MavenResolvedCoordinate;
import eu.fasten.analyzer.javacgwala.data.core.Call;
import eu.fasten.analyzer.javacgwala.data.core.ResolvedMethod;
import eu.fasten.analyzer.javacgwala.data.core.UnresolvedMethod;
import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PartialCallGraphTest {

    private static PartialCallGraph graph;
    private static Call call;


    @BeforeAll
    static void setUp() {
        var path = Paths.get(new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile()).getAbsolutePath());

        var source = new ResolvedMethod("name.space",
                Selector.make("<init>()V"), null);
        var target = new ResolvedMethod("name.space",
                Selector.make("<init>()V"), null);
        call = new Call(source, target, Call.CallType.STATIC);


        var initialSource = new ResolvedMethod("name.space.SingleSourceToTarget",
                Selector.make("sourceMethod()V"), null);
        var initialTarget = new ResolvedMethod("name.space.SingleSourceToTarget",
                Selector.make("targetMethod()V"), null);

        var initialUnresolvedSource =
                new ResolvedMethod("name.space.SingleSourceToTarget",
                        Selector.make("<init>()V"), null);
        var initialUnresolvedTarget =
                new UnresolvedMethod("java.lang.Object",
                        Selector.make("<init>()V"));

        MavenResolvedCoordinate coordinate =
                new MavenResolvedCoordinate("group", "artifact", "1.0", path);

        graph = new PartialCallGraph(Collections.singletonList(coordinate));

        graph.addUnresolvedCall(new Call(initialUnresolvedSource, initialUnresolvedTarget,
                Call.CallType.SPECIAL));
        graph.addResolvedCall(new Call(initialSource, initialTarget, Call.CallType.STATIC));
    }

    @Test
    void getUnresolvedCalls() {
        var source = "/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoid";
        var target = "/java.lang/Object.Object()%2Fjava.lang%2FVoid";
        var call = graph.getUnresolvedCalls().get(0);

        assertEquals(source, call.getSource().toCanonicalSchemalessURI().toString());
        assertEquals(target, call.getTarget().toCanonicalSchemalessURI().toString());
    }

    @Test
    void getResolvedCalls() {
        var source = "/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid";
        var target = "/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid";
        var call = graph.getResolvedCalls().get(0);

        assertEquals(source, call.getSource().toCanonicalSchemalessURI().toString());
        assertEquals(target, call.getTarget().toCanonicalSchemalessURI().toString());
    }

    @Test
    void addResolvedCall() {
        assertEquals(1, graph.getResolvedCalls().size());

        graph.addResolvedCall(call);

        assertEquals(2, graph.getResolvedCalls().size());
        assertEquals(call, graph.getResolvedCalls().get(1));

        graph.addResolvedCall(call);
        assertEquals(2, graph.getResolvedCalls().size());
    }

    @Test
    void addUnresolvedCall() {
        assertEquals(1, graph.getUnresolvedCalls().size());

        graph.addUnresolvedCall(call);

        assertEquals(2, graph.getUnresolvedCalls().size());
        assertEquals(call, graph.getUnresolvedCalls().get(1));

        graph.addUnresolvedCall(call);
        assertEquals(2, graph.getUnresolvedCalls().size());
    }

    @Test
    void toRevisionCallGraph() {
        var rcg = graph.toRevisionCallGraph(-1);

        assertEquals(-1, rcg.timestamp);
        assertEquals("group.artifact", rcg.product);
        assertEquals("mvn", rcg.forge);
        assertEquals("1.0", rcg.version);

        assertEquals("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid",
                rcg.graph.get(0)[0].toString());
        assertEquals("///name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid",
                rcg.graph.get(0)[1].toString());

        assertEquals("group:artifact", rcg.depset.get(0).get(0).product);
        assertEquals("mvn", rcg.depset.get(0).get(0).forge);
        assertEquals("[1.0]", rcg.depset.get(0).get(0).constraints.get(0).toString());
    }
}