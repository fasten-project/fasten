package eu.fasten.analyzer.javacgwala;

import eu.fasten.analyzer.javacgwala.data.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.CallGraphConstructor;
import eu.fasten.analyzer.javacgwala.data.callgraph.ExtendedRevisionCallGraph;
import eu.fasten.core.plugins.KafkaConsumer;
import eu.fasten.core.plugins.KafkaProducer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.json.JSONException;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WALAPlugin extends Plugin {

    public WALAPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class WALA implements KafkaConsumer<String>, KafkaProducer {

        private static Logger logger = LoggerFactory.getLogger(WALAPlugin.class);

        private static org.apache.kafka.clients.producer
                .KafkaProducer<Object, String> kafkaProducer;
        final String consumeTopic = "maven.packages";
        final String produceTopic = "wala_callgraphs";
        private boolean processedRecord;
        private boolean writeCGToKafka = false;
        private String pluginError = "";

        @Override
        public List<String> consumerTopics() {
            return new ArrayList<>(Collections.singletonList(consumeTopic));
        }

        @Override
        public void consume(String topic, ConsumerRecord<String, String> record) {
            processedRecord = false;
            consume(record);
            if (getPluginError().isEmpty()) {
                processedRecord = true;
            }
        }

        /**
         * Generates call graphs using OPAL for consumed maven coordinates in
         * {@link ExtendedRevisionCallGraph} format, and produce them to the Producer that is
         * provided for this Object.
         *
         * @param kafkaRecord A record including maven coordinates in the JSON format.
         *                    e.g. {
         *                    "groupId": "com.g2forge.alexandria",
         *                    "artifactId": "alexandria",
         *                    "version": "0.0.9",
         *                    "date": "1574072773"
         *                    }
         */
        public ExtendedRevisionCallGraph consume(ConsumerRecord<String, String> kafkaRecord) {

            MavenCoordinate mavenCoordinate;
            ExtendedRevisionCallGraph cg = null;
            try {
                var kafkaConsumedJson = new JSONObject(kafkaRecord.value());
                mavenCoordinate = new MavenCoordinate(
                        kafkaConsumedJson.get("groupId").toString(),
                        kafkaConsumedJson.get("artifactId").toString(),
                        kafkaConsumedJson.get("version").toString());

                logger.info("Generating call graph for {}", mavenCoordinate.getCoordinate());

                cg = CallGraphConstructor.build(mavenCoordinate)
                        .toExtendedRevisionCallGraph(Long
                                .parseLong(kafkaConsumedJson.get("date").toString()));

                if (cg == null || cg.graph.size() == 0) {
                    logger.warn("Empty call graph for {}", mavenCoordinate.getCoordinate());
                    return cg;
                }

                logger.info("Call graph successfully generated for {}!",
                        mavenCoordinate.getCoordinate());

                if (writeCGToKafka) {
                    sendToKafka(cg);
                }

            } catch (JSONException e) {
                setPluginError(e.getClass().getSimpleName());
                logger.error("Could not parse input coordinates: {}\n{}", kafkaRecord.value(), e);
            } catch (Exception e) {
                setPluginError(e.getClass().getSimpleName());
                logger.error("", e);
            }

            return cg;
        }

        /**
         * Send {@link ExtendedRevisionCallGraph} to Kafka.
         *
         * @param cg Call graph to send
         */
        public void sendToKafka(ExtendedRevisionCallGraph cg) {
            logger.debug("Writing call graph for {} to Kafka", cg.uri.toString());

            var record = new ProducerRecord<Object, String>(this.produceTopic,
                    cg.uri.toString(),
                    cg.toJSON().toString()
            );

            kafkaProducer.send(record, (recordMetadata, e) -> {
                if (recordMetadata != null) {
                    logger.debug("Sent: {} to {}", cg.uri.toString(), this.produceTopic);
                } else {
                    setPluginError(e.getClass().getSimpleName());
                    logger.error("Failed to write message to Kafka: " + e.getMessage(), e);
                }
            });
        }

        @Override
        public boolean recordProcessSuccessful() {
            return this.processedRecord;
        }

        @Override
        public String producerTopic() {
            return this.produceTopic;
        }

        @Override
        public void setKafkaProducer(org.apache.kafka.clients.producer.KafkaProducer<Object,
                String> producer) {
            kafkaProducer = producer;
            this.writeCGToKafka = true;
        }

        @Override
        public String name() {
            return this.getClass().getCanonicalName();
        }

        @Override
        public String description() {
            return "Generates call graphs for Java packages using WALA";
        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public void setPluginError(String exceptionType) {
            this.pluginError = exceptionType;
        }

        @Override
        public String getPluginError() {
            return this.pluginError;
        }

        @Override
        public void freeResource() {

        }
    }
}
