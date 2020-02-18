package eu.fasten.analyzer.metadataplugin;

import eu.fasten.core.plugins.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MetadataDatabasePlugin extends Plugin {

    public MetadataDatabasePlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class MetadataPlugin implements KafkaConsumer<String> {
        private final Logger logger = LoggerFactory.getLogger(MetadataPlugin.class.getName());
        @Override
        public List<String> consumerTopics() {
            return new ArrayList<>(Collections.singletonList("opal_callgraphs"));
        }

        @Override
        public void consume(String topic, ConsumerRecord<String, String> record) {
            logger.debug("Key: " + record.key() + " Value:" + record.value());
            // TODO: Insert consumed data in the metadata database using jOOQ
        }

        @Override
        public boolean recordProcessSuccessful() {
            return true;
        }

        @Override
        public String name() {
            return "Metadata plugin";
        }

        @Override
        public String description() {
            return "Metadata plugin. Connects to a Kafka topic and populates metadata database with consumed data.";
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void setPluginError(String exceptionType) {

        }

        @Override
        public String getPluginError() {
            return "";
        }

        @Override
        public void freeResource() {

        }
    }
}
