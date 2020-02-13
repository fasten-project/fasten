package eu.fasten.analyzer.javacgwala;

import eu.fasten.core.plugins.KafkaConsumer;
import eu.fasten.core.plugins.KafkaProducer;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

public class WALAPlugin extends Plugin {

    public WALAPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class WALA implements KafkaConsumer<String>, KafkaProducer {

        @Override
        public List<String> consumerTopics() {
            return null;
        }

        @Override
        public void consume(String topic, ConsumerRecord<String, String> record) {

        }

        @Override
        public boolean recordProcessSuccessful() {
            return false;
        }

        @Override
        public String producerTopic() {
            return null;
        }

        @Override
        public void setKafkaProducer(org.apache.kafka.clients.producer.KafkaProducer<Object,
                String> producer) {

        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public String description() {
            return null;
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
            return null;
        }

        @Override
        public void freeResource() {

        }
    }
}
