package eu.fasten.analyzer.qualityanalyzer;

import eu.fasten.core.plugins.KafkaPlugin;
import java.util.Optional;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.List;

public class QualityAnalyzerPlugin extends Plugin {

    public QualityAnalyzerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class QualityAnalyzer implements KafkaPlugin {

        private String consumeTopic = "fasten.mvn.pkg";
        private final Logger logger = LoggerFactory.getLogger(QualityAnalyzer.class.getName());

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.empty();
        }

        @Override
        public void setTopic(String topicName) {

        }

        @Override
        public void consume(String record) {

        }

        @Override
        public Optional<String> produce() {
            try {
                Thread.sleep(100000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return Optional.empty();
        }

        @Override
        public String getOutputPath() {
            return ".";
        }

        @Override
        public String name() {
            return "QualityAnalyzer";
        }

        @Override
        public String description() {
            return "SomeDescription";
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
            return null;
        }

        @Override
        public void freeResource() {

        }

    }
}
