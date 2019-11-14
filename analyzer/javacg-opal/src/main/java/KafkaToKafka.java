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

import eu.fasten.core.data.FastenJavaURI;

import com.fasterxml.jackson.databind.deser.std.StringDeserializer;

import scala.collection.JavaConversions;

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

            JSONObject kafkaConsumedJson = new JSONObject(kafkaRecord.value());

            MavenResolver.MavenCoordinate mavenCoordinate = new MavenResolver.MavenCoordinate(kafkaConsumedJson.get("groupId").toString(),
                kafkaConsumedJson.get("artifactId").toString(),
                kafkaConsumedJson.get("version").toString());

            MavenResolver.MavenCoordinate mavenCoordinate1 = new MavenResolver.MavenCoordinate("1",
                "2",
                "3");

            PartialCallGraph partialCallGraph = CallGraphGenerator.generatePartialCallGraph(MavenResolver.downloadArtifact(mavenCoordinate1.getCoordinate()));

            for (ResolvedCall resolvedCall : partialCallGraph.getResolvedCalls()) {
                OPALMethodAnalyzer.toCanonicalFastenJavaURI(resolvedCall.getSource());
                for (Method target : resolvedCall.getTarget()) {
                    OPALMethodAnalyzer.toCanonicalFastenJavaURI(target);
                }
            }

            for (UnresolvedMethodCall unresolvedCall : partialCallGraph.getUnresolvedCalls()) {

                OPALMethodAnalyzer.toCanonicalFastenJavaURI(unresolvedCall.caller());

                String URIString =
                    "/" + OPALMethodAnalyzer.getPackageName(unresolvedCall.calleeClass()).replace("/", ".").substring(1)
                        + OPALMethodAnalyzer.getClassName(unresolvedCall.calleeClass()) +
                        "." + unresolvedCall.calleeName() +
                        "(" + OPALMethodAnalyzer.getPctParameters(JavaConversions.seqAsJavaList(unresolvedCall.calleeDescriptor().parameterTypes())) +
                        ")" + OPALMethodAnalyzer.getPctReturnType(unresolvedCall.calleeDescriptor().returnType());

                try {
                    new FastenJavaURI(URIString).canonicalize();
                } catch (IllegalArgumentException | NullPointerException e) {
                    logger.error("{} faced {}", URIString, e.getMessage());
                }
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


