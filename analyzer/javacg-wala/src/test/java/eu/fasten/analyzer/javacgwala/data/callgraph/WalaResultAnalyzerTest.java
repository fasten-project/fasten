package eu.fasten.analyzer.javacgwala.data.callgraph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WalaResultAnalyzerTest {

    private static CallGraph graph;

    @BeforeAll
    static void setUp() throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
        var path = new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile()).getAbsolutePath();

        graph = CallGraphConstructor.generateCallGraph(path);
    }

    @Test
    void wrap() {
        var wrapped = WalaResultAnalyzer.wrap(graph, new ArrayList<>());

        assertEquals(1, wrapped.getResolvedCalls().size());
        assertEquals(1, wrapped.getUnresolvedCalls().size());

        var source = "/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoid";
        var target = "/java.lang/Object.Object()%2Fjava.lang%2FVoid";
        var call = wrapped.getUnresolvedCalls().get(0);

        assertEquals(source, call.getSource().toCanonicalSchemalessURI().toString());
        assertEquals(target, call.getTarget().toCanonicalSchemalessURI().toString());

        source = "/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid";
        target = "/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid";
        call = wrapped.getResolvedCalls().get(0);

        assertEquals(source, call.getSource().toCanonicalSchemalessURI().toString());
        assertEquals(target, call.getTarget().toCanonicalSchemalessURI().toString());
    }
}