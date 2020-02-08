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
}