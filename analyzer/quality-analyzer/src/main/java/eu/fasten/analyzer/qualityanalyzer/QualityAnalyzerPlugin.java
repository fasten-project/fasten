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

import eu.fasten.analyzer.qualityanalyzer.data.QAConstants;
import eu.fasten.core.data.Constants;
import eu.fasten.core.exceptions.UnrecoverableError;
import eu.fasten.core.plugins.DBConnector;
import eu.fasten.core.plugins.KafkaPlugin;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class QualityAnalyzerPlugin extends Plugin {

    public QualityAnalyzerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class QualityAnalyzer implements KafkaPlugin, DBConnector {

        private final Logger logger = LoggerFactory.getLogger(QualityAnalyzer.class.getName());
        private List<String> consumeTopics = new LinkedList<>(Collections.singletonList("fasten.RapidPlugin.callable.out"));
        private static MetadataUtils utils = null;
        private Exception pluginError = null;
        private String lastProcessedPayload = null;

        @Override
        public void setDBConnection(Map<String, DSLContext> dslContexts) {
            QualityAnalyzer.utils = new MetadataUtils(dslContexts);
        }

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(consumeTopics);
        }

        @Override
        public void setTopics(List<String> consumeTopics) {
            this.consumeTopics = consumeTopics;
        }

        @Override
        public void consume(String kafkaMessage) {
            lastProcessedPayload = null;

            logger.info("Consumed: " + kafkaMessage);

            var jsonRecord = new JSONObject(kafkaMessage);

            String payload = null;
            if (jsonRecord.has("payload")) {
                payload = jsonRecord.getJSONObject("payload").toString();
            }

            if(payload == null) {
                var message = "Could not extract payload from the Kafka message: " + kafkaMessage;
                logger.error(message);
                setPluginError(new RuntimeException(message));
                return;
            }

            int transactionRestartCount = 0;
            boolean restartTransaction = false;

            do {
                logger.info("Beginning of the transaction sequence");
                setPluginError(null);
                transactionRestartCount++;

                try {
                    utils.processJsonRecord(jsonRecord);
                }

                catch (DataAccessException e) {
                    logger.info("Data access exception: " + e);
                    if (transactionRestartCount >= Constants.transactionRestartLimit) {
                        throw new UnrecoverableError("Could not connect to or query the Postgres DB and the plug-in should be stopped and restarted.",
                                e.getCause());
                    }
                    setPluginError(e);
                    logger.info("Restarting transaction for '" + payload + "'");
                    restartTransaction = true;
                }

                catch(IllegalStateException e) {
                    logger.info("Illegal state exception: " + e.getMessage());
                    restartTransaction = false;
                    setPluginError(e);
                }

                catch (RuntimeException e) {
                    restartTransaction = false;
                    logger.error("Error saving to the database: '" + payload + "'", e);
                    setPluginError(e);
                }

                if (getPluginError() == null) {
                    restartTransaction = false;
                    lastProcessedPayload = payload;
                    logger.info("Processed " + payload + " successfully.");
                }

            } while( restartTransaction && transactionRestartCount < Constants.transactionRestartLimit );
        }

        @Override
        public Optional<String> produce() {
            if (lastProcessedPayload == null) {
                return Optional.empty();
            } else {
                return Optional.of("{\"result\" : \"Successfully inserted payload into the Metadata DB.\"}");
            }
        }

        @Override
        public String getOutputPath() {
            return null;
        }

        @Override
        public String name() {
            return QAConstants.QA_PLUGIN_NAME;
        }

        @Override
        public String description() {
            return "Consumes code quality  metrics generated by RAPID Plugin" +
                    "from a Kafka topic to populate callables in the Metadata DB.";
        }

        @Override
        public String version() {
            return QAConstants.QA_VERSION_NUMBER;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Exception getPluginError() {
            return pluginError;
        }

        @Override
        public void freeResource() {
        }

        public void setPluginError(Exception throwable) {
            this.pluginError = throwable;
        }

        @Override
        public long getMaxConsumeTimeout() {
            return 10 * 1000;
        }
    }
}
