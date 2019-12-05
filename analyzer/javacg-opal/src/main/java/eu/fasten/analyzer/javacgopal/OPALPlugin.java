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

import eu.fasten.core.plugins.KafkaConsumer;
import eu.fasten.core.plugins.KafkaProducer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.StreamSupport;

public class OPALPlugin implements KafkaConsumer<String>, KafkaProducer {

    private static Logger logger = LoggerFactory.getLogger(OPALMethodAnalyzer.class);

    org.apache.kafka.clients.producer.KafkaProducer<Object, String> kafkaProducer;
    final String CONSUME_TOPIC = "maven.packages";
    final String PRODUCE_TOPIC = "opal_callgraphs";

    @Override
    public List<String> consumerTopic() {
        return new ArrayList<>(Collections.singletonList(CONSUME_TOPIC));
    }

    /**
     * Generates call graphs using OPAL for consumed maven coordinates in eu.fasten.core.data.RevisionCallGraph format, and produce them to the Producer that is provided for this Object.
     *
     * @param records An Iterable of records including maven coordinates in the JSON format.
     *                e.g. {
     *                       "groupId": "com.g2forge.alexandria",
     *                       "artifactId": "alexandria",
     *                       "version": "0.0.9",
     *                       "date": "1574072773"
     *                      }
     */
    @Override
    public void consume(String topic, ConsumerRecords<String, String> records) {

        StreamSupport.stream(records.spliterator(),true).forEach(

            kafkaRecord -> {

                try {
                    JSONObject kafkaConsumedJson = new JSONObject(kafkaRecord.value());

                    MavenCoordinate mavenCoordinate = new MavenCoordinate(kafkaConsumedJson.get("groupId").toString(),
                        kafkaConsumedJson.get("artifactId").toString(),
                        kafkaConsumedJson.get("version").toString());

                    logger.info("Generating RevisionCallGraph for {} ...", mavenCoordinate.getCoordinate());

                    var revisionCallGraph = PartialCallGraph.createRevisionCallGraph("mvn",
                        mavenCoordinate, Long.parseLong(kafkaConsumedJson.get("date").toString()),
                        new PartialCallGraph(MavenResolver.downloadJar(mavenCoordinate.getCoordinate()).orElseThrow(RuntimeException::new))
                    );

                    logger.info("RevisionCallGraph successfully generated for {}!", mavenCoordinate.getCoordinate());

                    logger.info("Producing generated call graph for {} to Kafka ...", revisionCallGraph.uri.toString());

                    ProducerRecord<Object, String> record = new ProducerRecord<>(revisionCallGraph.uri.toString(), revisionCallGraph.toJSON().toString());

                    try {
                        kafkaProducer.send(record, ((recordMetadata, e) -> {
                            if (e != null) {
                                logger.error("Problem in producing {}", revisionCallGraph.uri.toString());
                                logger.error("Error: ", e);
                                return;
                            }
                            logger.debug("Could not produce artifact {} : ", revisionCallGraph.uri.toString());
                        })).get();
                    } catch (ExecutionException | InterruptedException e) {
                        logger.error("Exception in producing {}", revisionCallGraph.uri.toString());
                        logger.error("Exception: ", e);
                    }

                } catch (JSONException e) {
                    logger.error("An exception occurred while using consumer records as json: {}", e.getMessage());
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }
        );

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

//    @Override
//    public String description() {
//        return  "This plugin is a call graph generator.\n" +
//                "It implements a consume method that generates call graphs using OPAL call graph generator for provided Kafka consumed maven coordinates.\n" +
//                "It also implements a produce method which produces generated call graphs to a Kafka topic.\n";
//    }
//
//    @Override
//    public void start() {
//
//    }
//
//    @Override
//    public void stop() {
//
//    }
}
