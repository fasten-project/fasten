package eu.fasten.analyzer.baseanalyzer;

import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.plugins.KafkaConsumer;
import eu.fasten.core.plugins.KafkaProducer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.json.JSONException;
import org.json.JSONObject;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AnalyzerPlugin extends Plugin {
    public AnalyzerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    public abstract static class ANALYZER implements KafkaConsumer<String>, KafkaProducer {

        private Logger logger = LoggerFactory.getLogger(getClass());

        private static org.apache.kafka.clients.producer.KafkaProducer<Object, String>
                kafkaProducer;
        final String consumeTopic = "maven.packages";
        private boolean processedRecord;
        private String pluginError;

        @Override
        public List<String> consumerTopics() {
            return new ArrayList<>(Collections.singletonList(consumeTopic));
        }

        @Override
        public void consume(String topic, ConsumerRecord<String, String> kafkaRecord) {
            pluginError = "";
            processedRecord = false;
            consume(kafkaRecord, true);
            if (getPluginError().isEmpty()) {
                processedRecord = true;
            }
        }

        /**
         * Generates call graphs using OPAL for consumed maven coordinates in
         * eu.fasten.core.data.RevisionCallGraph format, and produce them to the Producer that is
         * provided for this Object.
         *
         * @param kafkaRecord    A record including maven coordinates in the JSON format. e.g. {
         *                       "groupId": "com.g2forge.alexandria", "artifactId": "alexandria",
         *                       "version": "0.0.9", "date": "1574072773" }
         * @param writeCGToKafka If true, the generated call graph will be written into Kafka
         */
        public ExtendedRevisionCallGraph consume(final ConsumerRecord<String, String> kafkaRecord,
                                                 final boolean writeCGToKafka) {
            try {
                final var kafkaConsumedJson = new JSONObject(kafkaRecord.value());
                final var mavenCoordinate = getMavenCoordinate(kafkaConsumedJson);

                logger.info("Generating call graph for {}", mavenCoordinate.getCoordinate());
                final var cg = generateCallGraph(mavenCoordinate, kafkaConsumedJson);

                if (cg == null || cg.isCallGraphEmpty()) {
                    logger.warn("Empty call graph for {}", mavenCoordinate.getCoordinate());
                    return cg;
                }

                logger.info("Call graph successfully generated for {}!",
                        mavenCoordinate.getCoordinate());

                if (writeCGToKafka) {
                    sendToKafka(cg);
                }
                return cg;

            } catch (Exception e) {
                setPluginError(e);
                logger.error("", e);
                return null;
            }
        }

        /**
         * Convert consumed JSON from Kafka to {@link MavenCoordinate}.
         *
         * @param kafkaConsumedJson Coordinate JSON
         * @return MavenCoordinate
         */
        public MavenCoordinate getMavenCoordinate(final JSONObject kafkaConsumedJson) {

            try {
                return new MavenCoordinate(
                        kafkaConsumedJson.get("groupId").toString(),
                        kafkaConsumedJson.get("artifactId").toString(),
                        kafkaConsumedJson.get("version").toString());
            } catch (JSONException e) {
                setPluginError(e);
                logger.error("Could not parse input coordinates: {}\n{}", kafkaConsumedJson, e);
            }
            return null;
        }

        /**
         * Generate an ExtendedRevisionCallGraph.
         *
         * @param mavenCoordinate   Maven coordinate
         * @param kafkaConsumedJson Consumed JSON
         * @return Generated ExtendedRevisionCallGraph
         */
        public abstract ExtendedRevisionCallGraph generateCallGraph(
                final MavenCoordinate mavenCoordinate,
                final JSONObject kafkaConsumedJson) throws Exception;

        /**
         * Send ExtendedRevisionCallGraph to Kafka.
         *
         * @param cg Generated call graph
         */
        public void sendToKafka(final ExtendedRevisionCallGraph cg) {

            logger.debug("Writing call graph for {} to Kafka", cg.uri.toString());
            final var record = new ProducerRecord<Object, String>(this.producerTopic(),
                    cg.uri.toString(),
                    cg.toJSON().toString()
            );

            kafkaProducer.send(record, (recordMetadata, e) -> {
                if (recordMetadata != null) {
                    logger.debug("Sent: {} to {}", cg.uri.toString(), this.producerTopic());
                } else {
                    setPluginError(e);
                    logger.error("Failed to write message to Kafka: " + e.getMessage(), e);
                }
            });
        }


        @Override
        public abstract String producerTopic();

        /**
         * This method should be called before calling consume method. It sets the KafkaProducer of
         * this Object to what is passed to it.
         *
         * @param producer org.apache.kafka.clients.producer.KafkaProducer.
         */
        @Override
        public void setKafkaProducer(
                org.apache.kafka.clients.producer.KafkaProducer<Object, String> producer) {
            kafkaProducer = producer;
        }

        public String name() {
            return this.getClass().getCanonicalName();
        }

        @Override
        public String description() {
            return "Generates call graphs for Java packages using OPAL";
        }

        @Override
        public boolean recordProcessSuccessful() {
            return this.processedRecord;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public String getPluginError() {
            return this.pluginError;
        }

        @Override
        public void setPluginError(Throwable throwable) {

            this.pluginError =
                    new JSONObject().put("plugin", this.getClass().getSimpleName()).put("msg",
                            throwable.getMessage()).put("trace", throwable.getStackTrace())
                            .put("type", throwable.getClass().getSimpleName()).toString();
            System.out.println(this.pluginError);
        }

        @Override
        public void freeResource() {

        }
    }
}
