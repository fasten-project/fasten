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

import com.fasterxml.jackson.databind.deser.std.StringDeserializer;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.json.JSONObject;
import org.opalj.ai.analyses.cg.UnresolvedMethodCall;
import org.opalj.br.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

/**
 * Consumes maven coordinates per artifact from Kafka,
 * Generates call graph per artifact,
 * Convert call graph to FastenJSON,
 * And Produce FastenJSON to the Kafka.
 */
public class KafkaToKafka {

    private static Logger logger = LoggerFactory.getLogger(KafkaToKafka.class);

    public static void main(String[] args) {

        ConsumerRecords<String, String> kafkaRecords = KafkaConsumerMonster.createConsumer("cf_mvn_releases").poll(Duration.ofDays(365));

        for (ConsumerRecord<String, String> kafkaRecord : kafkaRecords) {
            try {
                JSONObject kafkaConsumedJson = new JSONObject(kafkaRecord.value());

                MavenCoordinate mavenCoordinate = new MavenCoordinate(kafkaConsumedJson.get("groupId").toString(),
                    kafkaConsumedJson.get("artifactId").toString(),
                    kafkaConsumedJson.get("version").toString());

                MavenCoordinate mavenCoordinate1 = new MavenCoordinate("1",
                    "2",
                    "3");

                PartialCallGraph partialCallGraph = new PartialCallGraph(
                MavenResolver.downloadJar(mavenCoordinate1.getCoordinate()).orElseThrow(
                    RuntimeException::new
                ));

                for (ResolvedCall resolvedCall : partialCallGraph.getResolvedCalls()) {

                    OPALMethodAnalyzer.toCanonicalSchemelessURI(
                        null,
                        resolvedCall.getSource().declaringClassFile().thisType(),
                        resolvedCall.getSource().name(),
                        resolvedCall.getSource().descriptor());

                    for (Method target : resolvedCall.getTarget()) {

                        OPALMethodAnalyzer.toCanonicalSchemelessURI(
                            null,
                            target.declaringClassFile().thisType(),
                            target.name(),
                            target.descriptor()
                        );
                    }
                }

                for (UnresolvedMethodCall unresolvedCall : partialCallGraph.getUnresolvedCalls()) {

                    OPALMethodAnalyzer.toCanonicalSchemelessURI(
                        null,
                        unresolvedCall.caller().declaringClassFile().thisType(),
                        unresolvedCall.caller().name(),
                        unresolvedCall.caller().descriptor()
                    );

                    OPALMethodAnalyzer.toCanonicalSchemelessURI(
                        "SomeDependency",
                        unresolvedCall.calleeClass(),
                        unresolvedCall.calleeName(),
                        unresolvedCall.calleeDescriptor()
                    );
                }
            }catch (Exception e){
                logger.info("cound not generate cg for coordinate");
            }
        }
        //TODO generate FASTEN JSON and Produce to Kafka.

    }

    /**
     * Configuration needed for KafkaConsumer.
     */
    static class KafkaConsumerMonster {

        private final static String BROKER = "localhost:30001,localhost:30002,localhost:30003";

        /**
         * Configures and Creates a key-value consumer form a given topic.
         * @param topic Kafka topic that maven coordinates are there.
         * @return Consumer recode in a key-value format.
         */
        public static Consumer<String, String> createConsumer(final String topic) {

            final Properties props = new Properties();

            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BROKER);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString()); // We want to have a random consumer group.
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put("auto.offset.reset", "earliest");
            props.put("max.poll.records", "1");

            final Consumer<String, String> consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Collections.singletonList(topic));

            return consumer;
        }

    }
}


