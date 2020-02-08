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

package eu.fasten.analyzer.javacgopal;

import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.callgraph.ExtendedRevisionCallGraph;
import eu.fasten.core.plugins.KafkaConsumer;
import eu.fasten.core.plugins.KafkaProducer;

import java.io.FileNotFoundException;
import java.util.*;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.json.JSONException;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OPALPlugin extends Plugin {

    public OPALPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class OPAL implements KafkaConsumer<String>, KafkaProducer {

        private static Logger logger = LoggerFactory.getLogger(OPALPlugin.class);

        private static org.apache.kafka.clients.producer.KafkaProducer<Object, String> kafkaProducer;
        final String CONSUME_TOPIC = "maven.packages";
        final String PRODUCE_TOPIC = "opal_callgraphs";
        private boolean processedRecord;
        private String pluginError = "";


        @Override
        public List<String> consumerTopics() {
            return new ArrayList<>(Collections.singletonList(CONSUME_TOPIC));
        }

        @Override
        public void consume(String topic, ConsumerRecord<String, String> kafkaRecord) {
            processedRecord = false;
            consume(kafkaRecord, true);
            if(getPluginError().isEmpty()) { processedRecord = true; }
        }

        /**
         * Generates call graphs using OPAL for consumed maven coordinates in
         * eu.fasten.core.data.RevisionCallGraph format, and produce them to the Producer that is
         * provided for this Object.
         *
         * @param kafkaRecord A record including maven coordinates in the JSON format.
         *                    e.g. {
         *                    "groupId": "com.g2forge.alexandria",
         *                    "artifactId": "alexandria",
         *                    "version": "0.0.9",
         *                    "date": "1574072773"
         *                    }
         * @param writeCGToKafka If true, the generated call graph will be written into Kafka
         */
        public ExtendedRevisionCallGraph consume(ConsumerRecord<String, String> kafkaRecord,
                                                 boolean writeCGToKafka) {

            MavenCoordinate mavenCoordinate = null;
            ExtendedRevisionCallGraph cg = null;
            try {
                var kafkaConsumedJson = new JSONObject(kafkaRecord.value());
                mavenCoordinate = new MavenCoordinate(
                    kafkaConsumedJson.get("groupId").toString(),
                    kafkaConsumedJson.get("artifactId").toString(),
                    kafkaConsumedJson.get("version").toString());

                logger.info("Generating call graph for {}", mavenCoordinate.getCoordinate());

                cg = generateCallgraph(mavenCoordinate, kafkaConsumedJson);

                if (cg == null || cg.isCallGraphEmpty()) {
                    logger.warn("Empty call graph for {}", mavenCoordinate.getCoordinate());
                    return cg;
                }

                logger.info("Call graph successfully generated for {}!",
                    mavenCoordinate.getCoordinate());

                if(writeCGToKafka) { sendToKafka(cg); }

            }  catch (FileNotFoundException e) {
                setPluginError(e.getClass().getSimpleName());
                logger.error("Could find JAR for Maven coordinate: {}",
                    mavenCoordinate.getCoordinate(), e);
            } catch (JSONException e) {
                setPluginError(e.getClass().getSimpleName());
                logger.error("Could not parse input coordinates: {}\n{}", kafkaRecord.value(), e);
            } catch (Exception e) {
                setPluginError(e.getClass().getSimpleName());
                logger.error("", e);
            } finally {
                return cg;
            }
        }

        public void sendToKafka(ExtendedRevisionCallGraph cg) {
            logger.debug("Writing call graph for {} to Kafka", cg.uri.toString());

            var record = new ProducerRecord<Object, String>(this.PRODUCE_TOPIC,
                cg.uri.toString(),
                cg.toJSON().toString()
            );

            kafkaProducer.send(record, ((recordMetadata, e) -> {
                if (recordMetadata != null) {
                    logger.debug("Sent: {} to {}", cg.uri.toString(), this.PRODUCE_TOPIC);
                } else {
                    setPluginError(e.getClass().getSimpleName());
                    logger.error("Failed to write message to Kafka: " + e.getMessage(), e);
                }
            }));
        }

        public ExtendedRevisionCallGraph generateCallgraph(MavenCoordinate mavenCoordinate,
                                                            JSONObject kafkaConsumedJson)
            throws FileNotFoundException {
            return ExtendedRevisionCallGraph.create("mvn", mavenCoordinate,
                Long.parseLong(kafkaConsumedJson.get("date").toString()));
        }

        @Override
        public String producerTopic() {
            return this.PRODUCE_TOPIC;
        }

        /**
         * This method should be called before calling consume method.
         * It sets the KafkaProducer of this Object to what is passed to it.
         *
         * @param producer org.apache.kafka.clients.producer.KafkaProducer.
         */
        @Override
        public void setKafkaProducer(org.apache.kafka.clients.producer.KafkaProducer<Object, String> producer) {
            this.kafkaProducer = producer;
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
