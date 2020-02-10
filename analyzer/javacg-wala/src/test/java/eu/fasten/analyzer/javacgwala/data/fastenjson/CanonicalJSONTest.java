package eu.fasten.analyzer.javacgwala.data.fastenjson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import eu.fasten.analyzer.javacgwala.data.callgraph.WalaCallGraph;
import eu.fasten.analyzer.javacgwala.data.type.MavenResolvedCoordinate;
import eu.fasten.analyzer.javacgwala.generator.WalaCallgraphConstructor;
import eu.fasten.analyzer.javacgwala.generator.WalaUFIAdapter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


class CanonicalJSONTest {

    private static WalaUFIAdapter graph;

    @BeforeAll
    public static void setUp() throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
        var path = new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile()).getAbsolutePath();

        var mvnPath = Paths.get(path);
        MavenResolvedCoordinate mvn =
                new MavenResolvedCoordinate("group", "artifact", "1.0", mvnPath);

        List<MavenResolvedCoordinate> mvnList = new ArrayList<>();
        mvnList.add(mvn);

        graph = WalaUFIAdapter.wrap(new WalaCallGraph(WalaCallgraphConstructor
                .buildCallGraph(path), mvnList));
    }

    @Test
    void toJsonCallgraph() {
        var actualForge = "mvn";
        var actualProduct = "group.artifact";
        var actualProductInDepset = "group:artifact";
        var actualVersion = "1.0";

        var canonicalGraph = CanonicalJSON.toJsonCallgraph(graph, 0);

        assertEquals(actualForge, canonicalGraph.forge);
        assertEquals(actualProduct, canonicalGraph.product);
        assertEquals(actualVersion, canonicalGraph.version);

        assertEquals(actualForge, canonicalGraph.depset.get(0).get(0).forge);
        assertEquals(actualProductInDepset, canonicalGraph.depset.get(0).get(0).product);

        assertEquals(2, canonicalGraph.graph.size());
    }

    @Test
    void convertToFastenURI() {
        var actualSourceURI = "fasten:/name.space/SingleSourceToTarget"
                + ".sourceMethod()%2Fjava.lang%2FVoid";

        var resolvedCall = graph.lappPackage.resolvedCalls.iterator().next();

        assertEquals(actualSourceURI,
                CanonicalJSON.convertToFastenURI(resolvedCall.source).toString());
    }
}