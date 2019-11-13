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


package eu.fasten.pipeliner;



import eu.fasten.analyzer.javacgwala.generator.WalaUFIAdapter;
import eu.fasten.analyzer.javacgwala.serverContact.Artifact;
import eu.fasten.analyzer.javacgwala.serverContact.KafkaConsumerMonster;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;


public class Pipeliner {

    private static Logger logger = LoggerFactory.getLogger(Pipeliner.class);

    public static <Method> void main(String[] args) {
        int projectNumber = 0;
        String nonResolvables = "";
        final Consumer<String, String> consumer = KafkaConsumerMonster.createConsumer();
        WalaUFIAdapter wrapped_cg = null;
        try {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Long.MAX_VALUE);
                for (ConsumerRecord<String, String> record : records) {
                    JSONObject jsonObject = new JSONObject(record.value());
                    Artifact artifact = new Artifact(record.offset(), jsonObject.get("groupId").toString(), jsonObject.get("artifactId").toString(), jsonObject.get("version").toString(), jsonObject.get("date").toString());
                    String coordinate = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"+ artifact.getVersion();
                    File tempFile = new File("canonicalGraphs/"+projectNumber+"-"+coordinate);
                    if(!tempFile.exists()) {
                        logger.info("Call Graph Generation for :{}", coordinate);
                        projectNumber++;
                        //wrapped_cg = CallGraphGenerator.generateCallGraph(coordinate);
                        if (wrapped_cg == null) {
                            nonResolvables += + projectNumber + "-" + coordinate + "\n";
                            continue;
                        }
                        //KafkaProducerMonster.runProducer(projectNumber, CanonicalJSON.toJson(wrapped_cg, artifact.getDate()));
                        logger.info("producing done!");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error Occured", e);
        } finally {
            consumer.close();
        }
    }

}
