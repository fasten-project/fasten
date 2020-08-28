package eu.fasten.analyzer.qualityanalyzer;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QualityAnalyzerPluginTest {
    private QualityAnalyzerPlugin.QualityAnalyzer qualityAnalyzer;

    @BeforeEach
    public void setup() {
        qualityAnalyzer = new QualityAnalyzerPlugin.QualityAnalyzer();
        qualityAnalyzer.setTopic("fasten.RapidPlugin.out");
    }

    @Test
    public void consumeTest() {
        var record = new JSONObject("{" +
                "\"payload\": {" +
                "\"artifactId\": \"junit\"," +
                "\"groupId\": \"junit\"," +
                "\"version\": \"4.12\"," +
                "\"metrics\": {" +
                "      \"nloc\": 1234,\n" +
                "      \"method_count\": 123,\n" +
                "      \"complexity\": 12,\n" +
                "      \"token_count\": 98765,\n" +
                "      \"file_list\": []" +
                "}}}").toString();
        qualityAnalyzer.consume(record);
        var output = qualityAnalyzer.produce();
        assertTrue(output.isPresent());
    }

    @Test
    public void saveToDatabaseTest() {
        assertTrue(true);
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Optional.of(Collections.singletonList("fasten.RapidPlugin.out"));
        assertEquals(topics, qualityAnalyzer.consumeTopic());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Optional.of(Collections.singletonList("fasten.RapidPlugin.out"));
        assertEquals(topics1, qualityAnalyzer.consumeTopic());
        var differentTopic = "DifferentKafkaTopic";
        var topics2 = Optional.of(Collections.singletonList(differentTopic));
        qualityAnalyzer.setTopic(differentTopic);
        assertEquals(topics2, qualityAnalyzer.consumeTopic());
    }

    @Test
    public void nameTest() {
        var name = "Quality Analyzer Plugin";
        assertEquals(name, qualityAnalyzer.name());
    }

    @Test
    public void descriptionTest() {
        var description = "Quality Analyzer Plugin. "
                + "Consumes JSON objects (code metrics by lizard) from Kafka topic"
                + " and populates metadata database with consumed data.";
        assertEquals(description, qualityAnalyzer.description());
    }

    @Test
    public void versionTest() {
        var version = "0.0.1";
        assertEquals(version, qualityAnalyzer.version());
    }
}
