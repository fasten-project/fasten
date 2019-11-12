package eu.fasten.analyzer;

import org.junit.Test;

import static eu.fasten.analyzer.CallGraphGenerator.generateCallGraph;
import static junit.framework.TestCase.assertNotNull;

public class WalaCGTest {

    @Test
    public void testCreation() {
        var cg = generateCallGraph("org.slf4j:slf4j-api:1.7.29");
        assertNotNull(cg);
    }
}
