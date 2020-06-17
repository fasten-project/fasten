package eu.fasten.analyzer.pomanalyzer;

import eu.fasten.core.plugins.DBConnector;
import eu.fasten.core.plugins.KafkaPlugin;
import org.jooq.DSLContext;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class POMAnalyzerPlugin extends Plugin {

    public POMAnalyzerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class POMAnalyzer implements KafkaPlugin, DBConnector {

        private String consumerTopic = "fasten.maven.pkg";
        private final Logger logger = LoggerFactory.getLogger(POMAnalyzer.class.getName());
        private Throwable pluginError = null;
        private static DSLContext dslContext;

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(Collections.singletonList(consumerTopic));
        }

        @Override
        public void setTopic(String topicName) {
            this.consumerTopic = topicName;
        }

        @Override
        public void consume(String record) {
            pluginError = null;
            logger.info("Consumed: " + record);
        }

        @Override
        public Optional<String> produce() {
            return Optional.empty();
        }

        @Override
        public String getOutputPath() {
            return File.separator + ""; // TODO: Change to actual output path
        }

        @Override
        public String name() {
            return "POM Analyzer plugin";
        }

        @Override
        public String description() {
            return "POM Analyzer plugin. Consumes Maven coordinate from Kafka topic, "
                    + "downloads pom.xml of that coordinate and analyzes it "
                    + "extracting relevant information such as dependency information "
                    + "and repository URL, and produces that information to Kafka topic.";
        }

        @Override
        public String version() {
            return "0.0.1";
        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public Throwable getPluginError() {
            return this.pluginError;
        }

        @Override
        public void freeResource() {

        }

        @Override
        public void setDBConnection(DSLContext dslContext) {
            POMAnalyzer.dslContext = dslContext;
        }
    }
}
