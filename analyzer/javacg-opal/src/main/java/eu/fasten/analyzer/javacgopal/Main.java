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
 * Makes javacg-opal module runnable from command line.
 * Usage: java -jar javacg-opal-0.0.1-SNAPSHOT.jar groupId artifactId version timestamp
 */
public class Main {

    /**
     * Generates RevisionCallGraphs using Opal for the specified artifact in the command line parameters.
     *
     * @param args parameters should be in this sequence: "groupId" "artifactId" "version" "timestamp"
     * timestamp is the release timestamp of the artifact and should be in seconds from UNIX epoch.
     */
    public static void main(String[] args) {

        MavenResolver.MavenCoordinate mavenCoordinate= new MavenResolver.MavenCoordinate(args[0],args[1],args[2]);

        var revisionCallGraph =PartialCallGraph.createRevisionCallGraph("mvn",
            new MavenResolver.MavenCoordinate(mavenCoordinate.getGroupID(), mavenCoordinate.getArtifactID(),mavenCoordinate.getVersion()),
            Long.parseLong(args[3]),
            CallGraphGenerator.generatePartialCallGraph(MavenResolver.downloadArtifact(mavenCoordinate.getCoordinate())));

    }

}


