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

import java.sql.BatchUpdateException;
import java.util.Collections;
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
        private String consumerTopic = "fasten.RapidPlugin.callable.out";
        private static MetadataUtils utils = null;
        private Exception pluginError = null;

        @Override
        public void setDBConnection(Map<String, DSLContext> dslContexts) {
            QualityAnalyzer.utils = new MetadataUtils(dslContexts);
        }

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(Collections.singletonList(consumerTopic));
        }

        @Override
        public void setTopic(String topicName) {
            this.consumerTopic = topicName;
        }

        @Override
        public void consume(String kafkaMessage) {

            logger.info("Consumed: " + kafkaMessage);

            var jsonRecord = new JSONObject(kafkaMessage);

            String forge = null;

            if (jsonRecord.has("payload")) {
                forge = jsonRecord
                        .getJSONObject("payload")
                        .getString("forge".replaceAll("[\\n\\t ]", ""));
            }

            logger.info("forge = " + forge);

            if(forge == null) {
                logger.error("Could not extract forge from the message");
                setPluginError(new RuntimeException("Could not extract forge from the message"));
                return;
            }

            boolean processedRecord = false;
            int transactionRestartCount = 0;
            boolean restartTransaction = false;

            Long recordId = null;

            do {
                logger.info("Beginning of the transaction sequence");
                setPluginError(null);
                try {
                    recordId = utils.updateMetadataInDB(forge, jsonRecord);
                }
                catch(DataAccessException e) {
                    logger.info("Data access exception");
                    // Database connection error
                    if (e.getCause() instanceof BatchUpdateException) {
                        var exception = ((BatchUpdateException) e.getCause())
                                .getNextException();
                        setPluginError(exception);
                    }

                    logger.info("Restarting transaction for '" + recordId + "'");
                    // It could be a deadlock, so restart transaction
                    restartTransaction = true;
                }
                catch(IllegalStateException e) {
                    logger.info("Illegal state exception");
                    //do not restart transaction, callable list is empty
                    restartTransaction = false;
                    setPluginError(e);
                }
                catch (RuntimeException e) {
                    processedRecord = false;
                    restartTransaction = false;
                    logger.error("Error saving to the database: '" + forge + "'", e);
                    setPluginError(e);
                }

                if (getPluginError() == null) {
                    processedRecord = true;
                    restartTransaction = false;

                    logger.info("Updated the callable for  '" + forge + "' metadata "
                            + "with callable id = " + recordId);
                }

                transactionRestartCount++;

            } while( restartTransaction && !processedRecord && transactionRestartCount < Constants.transactionRestartLimit );
        }

        @Override
        public Optional<String> produce() {
            return Optional.empty();
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
            return 3600000; //The QualityAnalyzer plugin takes up to 1h to process a record.
        }

    }
}
