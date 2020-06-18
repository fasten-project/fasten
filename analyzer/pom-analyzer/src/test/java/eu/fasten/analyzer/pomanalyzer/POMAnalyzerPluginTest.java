package eu.fasten.analyzer.pomanalyzer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class POMAnalyzerPluginTest {

    private POMAnalyzerPlugin.POMAnalyzer pomAnalyzer;

    @BeforeEach
    public void setup() {
        pomAnalyzer = new POMAnalyzerPlugin.POMAnalyzer();
        pomAnalyzer.setTopic("fasten.maven.pkg");
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Optional.of(Collections.singletonList("fasten.maven.pkg"));
        assertEquals(topics, pomAnalyzer.consumeTopic());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Optional.of(Collections.singletonList("fasten.maven.pkg"));
        assertEquals(topics1, pomAnalyzer.consumeTopic());
        var differentTopic = "DifferentKafkaTopic";
        var topics2 = Optional.of(Collections.singletonList(differentTopic));
        pomAnalyzer.setTopic(differentTopic);
        assertEquals(topics2, pomAnalyzer.consumeTopic());
    }

    @Test
    public void nameTest() {
        var name = "POM Analyzer plugin";
        assertEquals(name, pomAnalyzer.name());
    }

    @Test
    public void descriptionTest() {
        var description = "POM Analyzer plugin. Consumes Maven coordinate from Kafka topic, "
                + "downloads pom.xml of that coordinate and analyzes it "
                + "extracting relevant information such as dependency information "
                + "and repository URL, then inserts that data into Metadata Database "
                + "and produces it to Kafka topic.";
        assertEquals(description, pomAnalyzer.description());
    }

    @Test
    public void versionTest() {
        var version = "0.0.1";
        assertEquals(version, pomAnalyzer.version());
    }
}
