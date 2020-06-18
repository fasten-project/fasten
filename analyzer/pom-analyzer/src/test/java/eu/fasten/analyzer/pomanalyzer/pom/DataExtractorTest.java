package eu.fasten.analyzer.pomanalyzer.pom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataExtractorTest {

    private DataExtractor dataExtractor;

    @BeforeEach
    public void setup() {
        dataExtractor = new DataExtractor();
    }

    @Test
    public void extractRepoUrlTest() {
        var expected = "http://github.com/junit-team/junit/tree/master";
        var actual = dataExtractor.extractRepoUrl("junit", "junit", "4.12");
        assertEquals(expected, actual);
    }
}
