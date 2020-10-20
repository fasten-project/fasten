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

package eu.fasten.analyzer.restapiplugin;

import eu.fasten.server.connectors.PostgresConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.sql.SQLException;

@CommandLine.Command(name = "RestAPIPlugin")
public class Main implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(RestAPIPlugin.class.getName());

    @CommandLine.Option(names = {"-p", "--port"},
            paramLabel = "port",
            description = "REST server port",
            defaultValue = "8080")
    int port;

    @CommandLine.Option(names = {"-d", "--db", "--database"},
            paramLabel = "dbURL",
            description = "KnowledgeBase URL",
            defaultValue = "jdbc:postgresql:postgres")
    String kbUrl;

    @CommandLine.Option(names = {"-u", "--user", "--username"},
            paramLabel = "dbUser",
            description = "KnowledgeBase username",
            defaultValue = "user")
    String kbUser;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {

        var restAPIPlugin = new RestAPIPlugin.RestAPIExtension(port);

        logger.info("Establishing connection to the KnowledgeBase...");
        try {
            restAPIPlugin.setDBConnection(PostgresConnector.getDSLContext(kbUrl, kbUser));
        } catch (SQLException e) {
            logger.error("Couldn't connect to the KnowledgeBase", e);
            return;
        }
        logger.info("...KnowledgeBase connection established successfully.");

        restAPIPlugin.start();
    }
}
