package eu.fasten.analyzer.qualityanalyzer;

import eu.fasten.core.plugins.KafkaPlugin;
import eu.fasten.core.plugins.DBConnector;
import eu.fasten.core.data.metadatadb.MetadataDao;

import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Optional;
import java.util.List;

import org.jooq.DSLContext;


public class QualityAnalyzerPlugin extends Plugin {

    public QualityAnalyzerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class QualityAnalyzer implements KafkaPlugin, DBConnector {

        private String consumerTopic = "fasten.RapidPlugin.out";
        private static DSLContext dslContext;
        private final Logger logger = LoggerFactory.getLogger(QualityAnalyzer.class.getName());
        private Throwable pluginError = null;
        private String artifact = null;
        private String group = null;
        private String version = null;
        private long date = -1L;
        private boolean restartTransaction = false;
        private final int transactionRestartLimit = 3;
        private boolean processedRecord = false;

        @Override
        public void setDBConnection(DSLContext dslContext) {
            QualityAnalyzer.dslContext = dslContext;
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
        public void consume(String record) {
            pluginError = null;
            artifact = null;
            group = null;
            version = null;
            date = -1L;
            this.processedRecord = false;
            this.restartTransaction = false;
            logger.info("Consumed: " + record);
            var jsonRecord = new JSONObject(record);
            var payload = new JSONObject();
            if (jsonRecord.has("payload")) {
                payload = jsonRecord.getJSONObject("payload");
            }
        }

        public long saveToDatabase(String product, String version, String repoUrl, JSONObject metrics, MetadataDao metadataDao) {
            final var packageId = metadataDao.insertPackage(product, "mvn", null, repoUrl, null);
            var packageVersionMetadata = new JSONObject();
            packageVersionMetadata.put("metrics", metrics != null ? metrics : null);
            final var packageVersionId = metadataDao.insertPackageVersion(packageId,
                    null, version, null, packageVersionMetadata);
            return packageVersionId;
        }

        @Override
        public Optional<String> produce() {
            try {
                Thread.sleep(100000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return Optional.empty();
        }

        @Override
        public String getOutputPath() {
            return ".";
        }

        @Override
        public String name() {
            return "Quality Analyzer Plugin";
        }

        @Override
        public String description() {
            return "Quality Analyzer Plugin. "
                    + "Consumes JSON objects (code metrics by lizard) from Kafka topic"
                    + " and populates metadata database with consumed data.";
        }

        @Override
        public String version() {
            return "0.0.1";
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Throwable getPluginError() {
            return null;
        }

        @Override
        public void freeResource() {
        }

    }
}
