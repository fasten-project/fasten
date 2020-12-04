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

import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.GraphMavenResolver;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.SQLException;

@Component
public class KnowledgeBaseConnector {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseConnector.class.getName());

    /**
     * KnowledgeBase data access object.
     */
    public static MetadataDao kbDao;

    /**
     * Dependency graph resolver for Maven
     */
    public static GraphMavenResolver graphResolver;

    /**
     * Database connection context
     */
    public static DSLContext dbContext;

    /**
     * KnowledgeBase username, retrieved from the server configuration file.
     */
    @Value("${kb.user}")
    private String kbUser;

    /**
     * KnowledgeBase address, retrieved from the server configuration file.
     */
    @Value("${kb.url}")
    private String kbUrl;

    /**
     * Path to the serialized dependency graph
     */
    @Value("${kb.depgraph.path}")
    private String depGraphPath;

    /**
     * Connects to the KnowledgeBase before starting the REST server.
     */
    @PostConstruct
    public void connectToKnowledgeBase() {
        logger.info("Establishing connection to the KnowledgeBase at " + kbUrl + ", user " + kbUser + "...");
        try {
            dbContext = PostgresConnector.getDSLContext(kbUrl, kbUser);
            kbDao = new MetadataDao(dbContext);
        } catch (SQLException e) {
            logger.error("Couldn't connect to the KnowledgeBase", e);
            System.exit(1);
        }
        logger.info("...KnowledgeBase connection established successfully.");
    }

    @PostConstruct
    public void retrieveDependencyGraph() {
        logger.info("Constructing dependency graph from " + depGraphPath);
        try {
            graphResolver = new GraphMavenResolver();
            graphResolver.buildDependencyGraph(PostgresConnector.getDSLContext(kbUrl, kbUser), depGraphPath);
        } catch (Exception e) {
            logger.error("Couldn't construct dependency graph", e);
            System.exit(1);
        }
        logger.info("Successfully constructed dependency graph");
    }
}
