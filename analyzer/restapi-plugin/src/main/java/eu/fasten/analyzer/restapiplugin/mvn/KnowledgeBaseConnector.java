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

package eu.fasten.analyzer.restapiplugin.mvn;

import eu.fasten.core.data.callableindex.RocksDao;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.dbconnectors.RocksDBConnector;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.SQLException;
import java.util.Properties;

@Component
public class KnowledgeBaseConnector {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseConnector.class.getName());

    /**
     * KnowledgeBase data access object.
     */
    public static MetadataDao kbDao;

    public static RocksDao graphDao;

    public static String rcgBaseUrl;

    public static String dependencyMavenGraphPath;

    public static String dependencyPyPiGraphPath;

    public static String dependencyDebianGraphPath;

    /**
     * Java Database connection context
     */
    public static DSLContext dbJavaContext;

    /**
     * Python Database connection context
     */
    public static DSLContext dbPythonContext;

    public static String vulnerableCallChainsPath;

    /**
     * C Database connection context
     */
    public static DSLContext dbCContext;


    public static KafkaProducer<String, String> kafkaProducer;

    public static String ingestTopic;

    /**
     * KnowledgeBase username, retrieved from the server configuration file.
     */
    @Value("${kb.user}")
    private String kbUser;

    /**
     * KnowledgeBase Java address, retrieved from the server configuration file.
     */
    @Value("${kb.java.url}")
    private String kbJavaUrl;

    /**
     * KnowledgeBase Python address, retrieved from the server configuration file.
     */
    @Value("${kb.python.url}")
    private String kbPythonUrl;
    

    /**
     * KnowledgeBase C address, retrieved from the server configuration file.
     */
    @Value("${kb.c.url}")
    private String kbCUrl;
    
    /**
     * Path to the Maven serialized dependency graph
     */
    @Value("${kb.maven.depgraph.path}")
    private String depMavenGraphPath;

    /**
     * Path to the PyPi serialized dependency graph
     */
    @Value("${kb.pypi.depgraph.path}")
    private String depPyPiGraphPath;

    /**
     * Path to the Debian serialized dependency graph
     */
    @Value("${kb.debian.depgraph.path}")
    private String depDebianGraphPath;


    @Value("${kb.graphdb.path}")
    private String graphdbPath;

    @Value("${kb.vulnchains.path}")
    private String vulnChainsPath;

    @Value("${lima.rcg.url}")
    private String rcgUrl;

    @Value("${kafka.address}")
    private String kafkaAddress;

    @Value("${kafka.output.topic}")
    private String kafkaOutputTopic;

    /**
     * Connects to the KnowledgeBase before starting the REST server.
     */
    @PostConstruct
    public void connectToKnowledgeBase() {
        try {
            logger.info("Establishing connection to the Java KnowledgeBase at " + kbJavaUrl + ", user " + kbUser + "...");
            dbJavaContext = PostgresConnector.getDSLContext(kbJavaUrl, kbUser, true);
            logger.info("Establishing connection to the Python KnowledgeBase at " + kbPythonUrl + ", user " + kbUser + "...");
            dbPythonContext  = PostgresConnector.getDSLContext(kbPythonUrl, kbUser, true);
            logger.info("Establishing connection to the C KnowledgeBase at " + kbCUrl + ", user " + kbUser + "...");
            dbCContext  = PostgresConnector.getDSLContext(kbCUrl, kbUser, true);
            kbDao = new MetadataDao(dbJavaContext);
        } catch (SQLException e) {
            logger.error("Couldn't connect to the KnowledgeBase", e);
            System.exit(1);
        }
        logger.info("...KnowledgeBase connection established successfully.");
    }

    /**
     * Sets base URL for retrieving JSON RCGs.
     */
    @PostConstruct
    public void setLimaUrl() {
        var url = this.rcgUrl;
        if (!url.endsWith("/")) {
            url += "/";
        }
        KnowledgeBaseConnector.rcgBaseUrl = url;
        logger.info("RCG base URL successfully set");
    }


    /**
     * Retrieves the Maven dependency graph if possible, otherwise constructs the graph from database.
     */
    @PostConstruct
    public void setDependencyGraphPath() {
        KnowledgeBaseConnector.dependencyMavenGraphPath = depMavenGraphPath;
    }

    /**
     * Retrieves the PyPi dependency graph if possible, otherwise constructs the graph from database.
     */
    @PostConstruct
    public void setPyPiDependencyGraphPath() {
        KnowledgeBaseConnector.dependencyPyPiGraphPath = depPyPiGraphPath;
    }

    /**
     * Retrieves the Debian dependency graph if possible, otherwise constructs the graph from database.
     */
    @PostConstruct
    public void setDebianDependencyGraphPath() {
        KnowledgeBaseConnector.dependencyDebianGraphPath = depDebianGraphPath;
    }

    /**
     * Retrieves the vulnerability call chains path if possible.
     */
    @PostConstruct
    public void setVulnerableCallChainsPath() {
        KnowledgeBaseConnector.vulnerableCallChainsPath = vulnChainsPath;
    }

    /**
     * Established read-only connection to the graph database.
     */
    @PostConstruct
    public void connectToGraphDB() {
        logger.info("Establishing connection to the Graph Database at " + graphdbPath + "...");
        try {
            graphDao = RocksDBConnector.createReadOnlyRocksDBAccessObject(graphdbPath);
        } catch (RuntimeException e) {
            logger.error("Couldn't connect to the Graph Database", e);
            System.exit(1);
        }
        logger.info("...Graph database connection established successfully.");
    }

    @PostConstruct
    public void initKafkaProducer() {
        ingestTopic = this.kafkaOutputTopic;
        var producerProperties = new Properties();
        producerProperties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaAddress);
        producerProperties.setProperty(ProducerConfig.CLIENT_ID_CONFIG, "fasten_restapi_producer");
        producerProperties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProperties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProperties.setProperty(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, "1000000");
        kafkaProducer = new KafkaProducer<>(producerProperties);
    }
}
