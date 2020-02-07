package eu.fasten.analyzer.javacgwala;

import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.*;

public class WalaJavaCGGenTest {

    @Test
    void name() {
        var cgName = new WalaJavaCGGen().name();
        assertEquals(cgName, "eu.fasten.analyzer.javacgwala");
    }

    @Test
    void description() {
        var cgDescription = new WalaJavaCGGen().description();
        assertEquals(cgDescription, "Constructs call graphs for Java packages using Wala.");
    }

    @Test
    void generateCallGraph() {
        var cg = WalaJavaCGGen.generateCallGraph("ai.h2o:h2o-bindings:3.10.0.7");
        assertEquals(cg.product, "ai.h2o.h2o-bindings");
        assertEquals(cg.version, "3.10.0.7");
        assertNotNull(cg.graph);
    }
}