package eu.fasten.analyzer.javacgwala.generator;

import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalaCallgraphConstructorTest {

    private static CallGraph graph;

    @BeforeAll
    public static void setUp() throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
        var path = new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile()).getAbsolutePath();

        graph = WalaCallgraphConstructor.buildCallGraph(path);
    }

    @Test
    void buildCallGraph() {

        // 12 nodes:
        // fakeRootMethod, fakeWorldClinit
        // Object <clinit>, <init>, registerNatives
        // SingleSourceToTarget <init>, sourceMethod, targetMethod, main
        // String <clinit>, <init>, <init>
        assertEquals(12, graph.getNumberOfNodes());
    }

    @Test
    void resolveCalls() {
        var calls = WalaCallgraphConstructor.resolveCalls(graph);
        assertEquals(2, calls.size());
        assertEquals("< Application, Lname/space/SingleSourceToTarget, <init>()V >",
                calls.get(0).source.toString());
        assertEquals("< Application, Lname/space/SingleSourceToTarget, sourceMethod()V >",
                calls.get(1).source.toString());
    }

    @Test
    void fetchJarFile() {
        // Fetch jar file of the root class -> rt.jar
        assertTrue(WalaCallgraphConstructor
                .fetchJarFile(graph.getClassHierarchy().getRootClass()).endsWith("rt.jar"));
    }
}