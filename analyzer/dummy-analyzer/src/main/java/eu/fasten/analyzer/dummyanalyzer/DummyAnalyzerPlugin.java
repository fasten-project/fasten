package eu.fasten.analyzer.dummyanalyzer;

import eu.fasten.core.plugins.KafkaPlugin;
import java.util.Optional;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.List;

public class DummyAnalyzerPlugin extends Plugin {

    public DummyAnalyzerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class DummyAnalyzer implements KafkaPlugin<String, String> {

        private String consumeTopic = "fasten.mvn.pkg";
        private final Logger logger = LoggerFactory.getLogger(DummyAnalyzer.class.getName());

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
        public String name() {
            return "DummyAnalyzer";
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
        public void setPluginError(Throwable throwable) {

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
