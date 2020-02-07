package eu.fasten.analyzer.javacgwala.lapp.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import eu.fasten.analyzer.javacgwala.data.callgraph.WalaCallGraph;
import eu.fasten.analyzer.javacgwala.data.fastenjson.CanonicalJSON;
import eu.fasten.analyzer.javacgwala.generator.WalaCallgraphConstructor;
import eu.fasten.analyzer.javacgwala.generator.WalaUFIAdapter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

class CallTest {

    private static WalaUFIAdapter graph;

    @BeforeAll
    public static void setUp() throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
        var path = new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile()).getAbsolutePath();


        graph = WalaUFIAdapter.wrap(new WalaCallGraph(WalaCallgraphConstructor
                .buildCallGraph(path), new ArrayList<>()));
    }

    @Test
    void toURICall() {
        var resolvedCall = graph.lappPackage.resolvedCalls.iterator().next();
        var source = CanonicalJSON.convertToFastenURI(resolvedCall.source);
        var target = CanonicalJSON.convertToFastenURI(resolvedCall.target);

        var fastenUri = resolvedCall.toURICall(source, target);

        assertEquals("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid",
                fastenUri[0].toString());
        assertEquals("///name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid",
                fastenUri[1].toString());
    }

    @Test
    void getLabel() {
        var resolvedCall = graph.lappPackage.resolvedCalls.iterator().next();

        assertEquals("invoke_static" ,resolvedCall.getLabel());
    }

    @Test
    void isResolved() {
        var resolvedCall = graph.lappPackage.resolvedCalls.iterator().next();
        var unresolvedCall = graph.lappPackage.unresolvedCalls.iterator().next();

        assertTrue(resolvedCall.isResolved());
        assertFalse(unresolvedCall.isResolved());
    }
}