/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.analyzer.javacgwala;

import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import eu.fasten.analyzer.baseanalyzer.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraph;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.plugins.KafkaConsumer;
import eu.fasten.core.plugins.KafkaProducer;
import java.io.IOException;
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
        private String pluginError = "";

        @Override
        public List<String> consumerTopics() {
            return new ArrayList<>(Collections.singletonList(consumeTopic));
        }

        @Override
        public void consume(String topic, ConsumerRecord<String, String> record) {
            processedRecord = false;
            consume(record, true);
            if (getPluginError().isEmpty()) {
                processedRecord = true;
            }
        }

        /**
         * Generates call graphs using OPAL for consumed maven coordinates in
         * eu.fasten.core.data.RevisionCallGraph format, and produce them to the Producer that is
         * provided for this Object.
         *
         * @param kafkaRecord    A record including maven coordinates in the JSON format.
         *                       e.g. {
         *                       "groupId": "com.g2forge.alexandria",
         *                       "artifactId": "alexandria",
         *                       "version": "0.0.9",
         *                       "date": "1574072773"
         *                       }
         * @param writeCGToKafka If true, the generated call graph will be written into Kafka
         */
        public ExtendedRevisionCallGraph consume(final ConsumerRecord<String, String> kafkaRecord,
                                                 final boolean writeCGToKafka) {
            try {
                final var kafkaConsumedJson = new JSONObject(kafkaRecord.value());
                final var mavenCoordinate = getMavenCoordinate(kafkaConsumedJson);

                final var cg = generateCallGraph(mavenCoordinate, kafkaConsumedJson);

                if (cg.isCallGraphEmpty()) {
                    logger.warn("Empty call graph for {}", mavenCoordinate.getCoordinate());
                    return cg;
                }

                logger.info("Call graph successfully generated for {}!",
                        mavenCoordinate.getCoordinate());

                if (writeCGToKafka) {
                    sendToKafka(cg);
                }
                return cg;

            } catch (Throwable e) {
                setPluginError(e);
                logger.error("Failed to generate a call graph for {}", kafkaRecord);
                return null;
            }
        }

        /**
         * Convert consumed JSON from Kafka to {@link MavenCoordinate}.
         *
         * @param kafkaConsumedJson Coordinate JSON
         * @return MavenCoordinate
         */
        public MavenCoordinate getMavenCoordinate(final JSONObject kafkaConsumedJson)
                throws JSONException {
            return new MavenCoordinate(
                    kafkaConsumedJson.get("groupId").toString(),
                    kafkaConsumedJson.get("artifactId").toString(),
                    kafkaConsumedJson.get("version").toString());
        }

        /**
         * Generate an ExtendedRevisionCallGraph.
         *
         * @param mavenCoordinate   Maven coordinate
         * @param kafkaConsumedJson Consumed JSON
         * @return Generated ExtendedRevisionCallGraph
         */
        public ExtendedRevisionCallGraph generateCallGraph(final MavenCoordinate mavenCoordinate,
                                                           final JSONObject kafkaConsumedJson)
                throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {

            return PartialCallGraph.createExtendedRevisionCallGraph(mavenCoordinate,
                    Long.parseLong(kafkaConsumedJson.get("date").toString()));
        }

        /**
         * Send ExtendedRevisionCallGraph to Kafka.
         *
         * @param cg Generated call graph
         */
        public void sendToKafka(final ExtendedRevisionCallGraph cg) {

            logger.debug("Writing call graph for {} to Kafka", cg.uri.toString());
            final var record = new ProducerRecord<Object, String>(this.produceTopic,
                    cg.uri.toString(),
                    cg.toJSON().toString()
            );

            kafkaProducer.send(record, (recordMetadata, e) -> {
                if (recordMetadata != null) {
                    logger.debug("Sent: {} to {}", cg.uri.toString(), this.produceTopic);
                } else {
                    setPluginError(e);
                    logger.error("Failed to write message to Kafka: " + e.getMessage());
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
        public void setPluginError(Throwable throwable) {
            this.pluginError = new JSONObject().put("plugin", this.getClass().getSimpleName())
                    .put("msg", throwable.getMessage())
                    .put("trace", throwable.getStackTrace())
                    .put("type", throwable.getClass().getSimpleName()).toString();
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
