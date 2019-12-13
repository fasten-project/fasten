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

import eu.fasten.core.data.RevisionCallGraph;
import eu.fasten.core.plugins.FastenPlugin;
import eu.fasten.core.plugins.KafkaConsumer;
import eu.fasten.core.plugins.KafkaProducer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.json.JSONException;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.StreamSupport;

public class OPALPlugin extends Plugin {

    public OPALPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class OPAL implements KafkaConsumer<String>, KafkaProducer {

        private static Logger logger = LoggerFactory.getLogger(OPALMethodAnalyzer.class);

        private org.apache.kafka.clients.producer.KafkaProducer<Object, String> kafkaProducer;
        final String CONSUME_TOPIC = "maven.packages";
        final String PRODUCE_TOPIC = "opal_callgraphs";
        RevisionCallGraph lastCallGraphGenerated;

        @Override
        public List<String> consumerTopics() {
            return new ArrayList<>(Collections.singletonList(CONSUME_TOPIC));
        }

        /**
         * Generates call graphs using OPAL for consumed maven coordinates in eu.fasten.core.data.RevisionCallGraph format,
         * and produce them to the Producer that is provided for this Object.
         *
         * @param kafkaRecord A record including maven coordinates in the JSON format.
         *                e.g. {
         *                "groupId": "com.g2forge.alexandria",
         *                "artifactId": "alexandria",
         *                "version": "0.0.9",
         *                "date": "1574072773"
         *                }
         */
        @Override
        public void consume(String topic, ConsumerRecord<String, String> kafkaRecord) {

            try {
                JSONObject kafkaConsumedJson = new JSONObject(kafkaRecord.value());

                MavenCoordinate mavenCoordinate = new MavenCoordinate(kafkaConsumedJson.get("groupId").toString(),
                    kafkaConsumedJson.get("artifactId").toString(),
                    kafkaConsumedJson.get("version").toString());

                logger.info("Generating RevisionCallGraph for {} ...", mavenCoordinate.getCoordinate());

                lastCallGraphGenerated = PartialCallGraph.createRevisionCallGraph("mvn",
                    mavenCoordinate, Long.parseLong(kafkaConsumedJson.get("date").toString()),
                    new PartialCallGraph(MavenResolver.downloadJar(mavenCoordinate.getCoordinate()).orElseThrow(RuntimeException::new))
                );

                logger.info("RevisionCallGraph successfully generated for {}!", mavenCoordinate.getCoordinate());

                logger.info("Producing generated call graph for {} to Kafka ...", lastCallGraphGenerated.uri.toString());

                ProducerRecord<Object, String> record = new ProducerRecord<>(lastCallGraphGenerated.uri.toString(), lastCallGraphGenerated.toJSON().toString());

                try {
                    kafkaProducer.send(record, ((recordMetadata, e) -> {
                        if (e != null) {
                            logger.error("Problem in producing {}", lastCallGraphGenerated.uri.toString(), e);
                            return;
                        }
                        logger.debug("Could not produce artifact {} : ", lastCallGraphGenerated.uri.toString());
                    })).get();
                } catch (ExecutionException | InterruptedException e) {
                    logger.error("Exception in producing {}", lastCallGraphGenerated.uri.toString(), e);
                }

            } catch (JSONException e) {
                logger.error("An exception occurred while using consumer records as json: {}", e.getMessage());
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
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
        public void start() {
        }

        @Override
        public void stop() {
        }
    }
}


