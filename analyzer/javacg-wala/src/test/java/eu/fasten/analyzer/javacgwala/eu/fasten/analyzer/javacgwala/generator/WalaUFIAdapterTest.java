package eu.fasten.analyzer.javacgwala.generator;

import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import eu.fasten.analyzer.javacgwala.data.callgraph.WalaCallGraph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WalaUFIAdapterTest {

    @Test
    void wrap() throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
        var path = new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile()).getAbsolutePath();

        var graph = new WalaCallGraph(WalaCallgraphConstructor.buildCallGraph(path),
                new ArrayList<>());

        var wrappedCG = WalaUFIAdapter.wrap(graph);

        assertNotNull(wrappedCG.lappPackage);
        assertNotNull(wrappedCG.callGraph);
        assertNotNull(wrappedCG.jarToCoordinate);

        assertEquals(1, wrappedCG.lappPackage.resolvedCalls.size());
        assertEquals(1, wrappedCG.lappPackage.unresolvedCalls.size());
        assertEquals(graph ,wrappedCG.callGraph);
    }
}