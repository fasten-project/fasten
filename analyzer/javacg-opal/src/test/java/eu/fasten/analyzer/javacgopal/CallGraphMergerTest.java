package eu.fasten.analyzer.javacgopal;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CallGraphMergerTest {

    @Test
    void testResolve() {

    }

    @Test
    void testMergeCallGraphs() {
        var revisionCallGraph = PartialCallGraph.createProposalRevisionCallGraph("mvn",
                new MavenCoordinate("org.slf4j", "slf4j-api","1.7.29"),
                1574072773,
                new PartialCallGraph(
                        new File(Thread.currentThread().getContextClassLoader().getResource("Imported.class").getFile())
                )
        );
        var revisionCallGraph1 = PartialCallGraph.createProposalRevisionCallGraph("mvn",
                new MavenCoordinate("org.slf4j", "slf4j-api","1.7.29"),
                1574072773,
                new PartialCallGraph(
                        new File(Thread.currentThread().getContextClassLoader().getResource("Importer.class").getFile())
                )
        );

        CallGraphMerger.mergeCallGraphs(Arrays.asList(revisionCallGraph,revisionCallGraph1));
    }
}