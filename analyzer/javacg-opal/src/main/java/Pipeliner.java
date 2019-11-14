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

import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import eu.fasten.core.data.FastenJavaURI;
import org.apache.kafka.clients.consumer.*;
import org.json.JSONObject;
import org.opalj.ai.analyses.cg.UnresolvedMethodCall;
import org.opalj.br.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.JavaConversions;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

public class Pipeliner {

    private static Logger logger = LoggerFactory.getLogger(Pipeliner.class);

    public static void main(String[] args) {

        ConsumerRecords<String, String> kafkaRecords = KafkaConsumerMonster.createConsumer("cf_mvn_releases").poll(Duration.ofDays(365));

        for (ConsumerRecord<String, String> kafkaRecord : kafkaRecords) {

            JSONObject kafkaConsumedJson = new JSONObject(kafkaRecord.value());

            MVNCoordinate mvnCoordinate = new MVNCoordinate(kafkaConsumedJson.get("groupId").toString(),
                kafkaConsumedJson.get("artifactId").toString(),
                kafkaConsumedJson.get("version").toString());

            PartialCallGraph partialCallGraph = CallGraphGenerator.generatePartialCallGraph(MavenResolver.downloadArtifact(mvnCoordinate.getCoordinate()));

            for (ResolvedCall resolvedCall : partialCallGraph.getResolvedCalls()) {
                MethodAnalyzer.toCanonicalFastenJavaURI(resolvedCall.source);
                for (Method target : resolvedCall.target) {
                    MethodAnalyzer.toCanonicalFastenJavaURI(target);
                }
            }

            for (UnresolvedMethodCall unresolvedCall : partialCallGraph.getUnresolvedCalls()) {

                MethodAnalyzer.toCanonicalFastenJavaURI(unresolvedCall.caller());

                String URIString =
                    "/" + MethodAnalyzer.getPackageName(unresolvedCall.calleeClass()).replace("/", ".").substring(1)
                        + MethodAnalyzer.getClassName(unresolvedCall.calleeClass()) +
                        "." + unresolvedCall.calleeName() +
                        "(" + MethodAnalyzer.getPctParameters(JavaConversions.seqAsJavaList(unresolvedCall.calleeDescriptor().parameterTypes())) +
                        ")" + MethodAnalyzer.getPctReturnType(unresolvedCall.calleeDescriptor().returnType());

                try {
                    new FastenJavaURI(URIString).canonicalize();
                } catch (IllegalArgumentException | NullPointerException e) {
                    logger.error("{} faced {}", URIString, e.getMessage());
                }
            }

            try {
                JVMFormat.printJVMGraph(partialCallGraph);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    static class KafkaConsumerMonster {

        private final static String BROKER = "localhost:30001,localhost:30002,localhost:30003";

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


