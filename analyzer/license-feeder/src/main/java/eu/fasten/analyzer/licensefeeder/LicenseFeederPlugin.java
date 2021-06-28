package eu.fasten.analyzer.licensefeeder;

import eu.fasten.core.plugins.KafkaPlugin;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class LicenseFeederPlugin extends Plugin {

    public LicenseFeederPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class LicenseFeeder implements KafkaPlugin {

        private final Logger logger = LoggerFactory.getLogger(LicenseFeeder.class.getName());

        protected Exception pluginError = null;

        /**
         * The topic this plugin consumes.
         * FIXME Call graph should also be created first.
         */
        protected String consumerTopic = "fasten.LicenseDetector.out";

        @Override
        public void consume(String record) {
            // TODO Implement
        }

        @Override
        public String name() {
            return "License Feeder Plugin";
        }

        @Override
        public String description() {
            return "Inserts license findings into the Metadata DB";
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
        public Optional<String> produce() {
            return Optional.empty(); // FIXME
        }

        @Override
        public String getOutputPath() {
            return null; // FIXME
        }
    }
}
