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

package eu.fasten.analyzer.qualityanalyzer;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URI;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import eu.fasten.analyzer.qualityanalyzer.data.QAConstants;
import eu.fasten.core.data.Constants;
import eu.fasten.core.dbconnectors.PostgresConnector;

import org.jooq.DSLContext;
import org.json.JSONObject;
import org.json.JSONTokener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;

import java.util.Arrays;

@CommandLine.Command(name = "QualityAnalyzerPlugin")
public class Main implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @CommandLine.Option(names = {"-f", "--file"},
            paramLabel = "JSON File",
            description = "Path to JSON file that contains JSON with Lizard tool generated quality metrics")
    String jsonFile;

    @CommandLine.Option(names = {"-d", "--database"},
    paramLabel = "dbURL",
    description = "Key-value pairs of Database URLs for connection Example - " +
            "mvn=jdbc:postgresql://postgres@localhost/dbname," +
            "PyPI=jdbc:postgresql://postgres@localhost/dbname1",
    split = ",")
    Map<String, String> dbUrls;

    @Override
    public void run() {

        QualityAnalyzerPlugin.QualityAnalyzer qualityAnalyzer = new QualityAnalyzerPlugin.QualityAnalyzer();

        if(!verifyDBUrls()) {
            logger.error("DB forges not properly defined!");
            return;
        }

        logger.info("DB forges properly defined!");

        setDBConnections(qualityAnalyzer);

        final FileReader reader;

        try {
            reader = new FileReader(jsonFile);
        } catch (FileNotFoundException e) {
            logger.error("Could not find input JSON file " + jsonFile, e);
            return;
        }

        final JSONObject json = new JSONObject(new JSONTokener(reader));
        qualityAnalyzer.consume(json.toString());
        qualityAnalyzer.produce().ifPresent(System.out::println);
    }

    /**
     * Verify that forges in DB URLs are properly defined.
     *
     * @return boolean that indicates whether forges are properly defined or not in DB argument.
     */
    private boolean verifyDBUrls() {

        if(dbUrls == null) {
            return false;
        }

        Set<String> forges = dbUrls.keySet();

        List<String> goodForges = Arrays.asList(Constants.mvnForge, Constants.debianForge, Constants.pypiForge);

        for(String forge: forges) {
            if(!goodForges.contains(forge)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Set multiple database connections for quality analyzer plugin.
     *
     * @param qualityAnalyzer Quality Analyzer
     */
    private void setDBConnections(QualityAnalyzerPlugin.QualityAnalyzer qualityAnalyzer) {

        qualityAnalyzer.setDBConnection(dbUrls.entrySet().stream().map(e -> new AbstractMap.SimpleEntry<>(e.getKey(),
                e.getValue())).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, e -> {
            try {
                logger.debug("Set {} DB connection successfully for plug-in {}",
                        e.getKey(), QAConstants.QA_PLUGIN_NAME);
                return getDSLContext(e.getValue());
            } catch (SQLException ex) {
                logger.error("Couldn't set {} DB connection for plug-in {}\n{}",
                        e.getKey(), QAConstants.QA_PLUGIN_NAME, ex.getStackTrace());
            }
            return null;
        })));

    }

    /**
     * Get a database connection for a given database URL
     * @param dbURL JDBC URI
     * @throws SQLException
     */
    private DSLContext getDSLContext(String dbURL) throws SQLException {
        String cleanURI = dbURL.substring(5);
        URI uri = URI.create(cleanURI);
        return PostgresConnector.getDSLContext("jdbc:postgresql://" + uri.getHost() + uri.getPath(),
                uri.getUserInfo(), true);
    }

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
