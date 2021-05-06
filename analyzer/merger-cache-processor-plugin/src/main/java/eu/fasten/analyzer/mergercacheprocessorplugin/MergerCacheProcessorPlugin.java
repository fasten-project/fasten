package eu.fasten.analyzer.mergercacheprocessorplugin;

import eu.fasten.core.plugins.KafkaPlugin;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MergerCacheProcessorPlugin extends Plugin {
    public MergerCacheProcessorPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class MergerCacheProcessorExtension implements KafkaPlugin {

        private final Logger logger = LoggerFactory.getLogger(MergerCacheProcessorExtension.class.getName());
        private String consumerTopic = "fasten.XXX.out";
        private Exception pluginError = null;
        private String outputPath;

        @Override
        public String name() {
            return "Merger Cache Processor Plugin";
        }

        @Override
        public String description() {
            return "Merger Cache Processor Plugin. "
                    + "Consumes list XXX from Kafka"
                    + " topic and caches vulnerable paths to a file";
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

        public void setPluginError(Exception throwable) {
            this.pluginError = throwable;
        }

        @Override
        public Exception getPluginError() {
            return this.pluginError;
        }

        @Override
        public void freeResource() {

        }

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

        }

        @Override
        public Optional<String> produce() {
            return Optional.empty();
        }

        @Override
        public String getOutputPath() {
            return this.outputPath;
        }
    }
}

