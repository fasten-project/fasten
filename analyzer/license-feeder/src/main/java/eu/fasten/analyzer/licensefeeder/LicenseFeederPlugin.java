package eu.fasten.analyzer.licensefeeder;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.plugins.DBConnector;
import eu.fasten.core.plugins.KafkaPlugin;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.json.JSONArray;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LicenseFeederPlugin extends Plugin {

    public LicenseFeederPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class LicenseFeeder implements KafkaPlugin, DBConnector {

        private final Logger logger = LoggerFactory.getLogger(LicenseFeeder.class.getName());

        protected Exception pluginError = null;
        private static DSLContext dslContext;

        /**
         * The topic this plugin consumes.
         */
        protected String consumerTopic = "fasten.LicenseDetector.out";

        @Override
        public void setDBConnection(Map<String, DSLContext> dslContexts) {
            LicenseFeeder.dslContext = dslContexts.get(Constants.mvnForge);
        }

        @Override
        public void consume(String record) {
            try { // Fasten error-handling guidelines

                this.pluginError = null;

                logger.info("License feeder started.");

                // Retrieving coordinates of the input record
                Revision coordinates = extractMavenCoordinates(record);
                logger.info("Input coordinates: " + coordinates + ".");

                // Inserting detected outbound into the database
                var metadataDao = new MetadataDao(dslContext);
                dslContext.transaction(transaction -> {
                    metadataDao.setContext(DSL.using(transaction));
                    insertOutboundLicenses(coordinates, record, metadataDao);
                    insertFileLicenses(coordinates, record, metadataDao);
                });

                // TODO Inserting licenses in files

            } catch (Exception e) { // Fasten error-handling guidelines
                logger.error(e.getMessage(), e.getCause());
                setPluginError(e);
            }
        }

        /**
         * Retrieves the Maven coordinate of the input record.
         * TODO Unit tests.
         *
         * @param record the input record containing repository information (`fasten.RepoCloner.out`).
         * @return the Maven coordinate of the input record.
         * @throws IllegalArgumentException in case the function couldn't find coordinate information
         *                                  in the input record.
         */
        public static Revision extractMavenCoordinates(String record) {
            var payload = new JSONObject(record);
            if (payload.has("input")) {
                payload = payload.getJSONObject("input");
            }
            if (payload.has("fasten.RepoCloner.out")) {
                payload = payload.getJSONObject("fasten.RepoCloner.out");
            }
            if (payload.has("payload")) {
                payload = payload.getJSONObject("payload");
            }
            String groupId = payload.getString("groupId");
            if (groupId == null) {
                throw new IllegalArgumentException("Invalid repository information: missing coordinate group ID.");
            }
            String artifactId = payload.getString("artifactId");
            if (artifactId == null) {
                throw new IllegalArgumentException("Invalid repository information: missing coordinate artifact ID.");
            }
            String version = payload.getString("version");
            if (version == null) {
                throw new IllegalArgumentException("Invalid repository information: missing coordinate version.");
            }
            long createdAt = payload.getLong("date");
            // TODO Is the timestamp conversion right?
            return new Revision(groupId, artifactId, version, new Timestamp(createdAt));
        }

        /**
         * Inserts outbound licenses at the package version level.
         *
         * @param coordinates the coordinates whose outbound licenses are about to be inserted.
         * @param record      the input record containing outbound license findings.
         * @param metadataDao Data Access Object to insert records in the database.
         */
        protected void insertOutboundLicenses(Revision coordinates, String record, MetadataDao metadataDao) {
            var payload = new JSONObject(record);
            if (payload.has("payload")) {
                payload = payload.getJSONObject("payload");
            }
            JSONArray outboundLicenses = payload.getJSONArray("outbound");
            logger.info("About to insert outbound licenses...");
            metadataDao.insertPackageOutboundLicenses(
                    coordinates,
                    new JSONObject().put("licenses", outboundLicenses).toString()
            );
            logger.info("...outbound licenses inserted.");
        }

        protected void insertFileLicenses(Revision coordinates, String record, MetadataDao metadataDao) {
            var payload = new JSONObject(record);
            if (payload.has("payload")) {
                payload = payload.getJSONObject("payload");
            }
            JSONArray fileLicenses = payload.getJSONArray("files");
            logger.info("About to insert file licenses...");
            fileLicenses.forEach(f -> {
                logger.debug("(cycling files) Object f: " + f);
                JSONObject file = (JSONObject) f;
                logger.debug("(cycling files) JSONObject f: " + file + " has " +
                        (file.has("path") ? "" : "no ") + "path and " +
                        (file.has("licenses") ? file.getJSONArray("licenses").length() : "no") + " licenses.");
                if (file.has("path") && file.has("licenses")) {
                    metadataDao.insertFileLicenses(
                            coordinates,
                            file.getString("path"),
                            new JSONObject().put("licenses", file.getJSONArray("licenses")).toString()
                    );
                }
            });
            logger.info("...file licenses inserted.");
        }

        @Override
        public String name() {
            return "License Feeder Plugin";
        }

        @Override
        public String description() {
            return "Inserts license findings into the Metadata DB";
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
        public Exception getPluginError() {
            return this.pluginError;
        }

        @Override
        public void freeResource() {
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
        public Optional<String> produce() {
            return Optional.empty(); // FIXME
        }

        @Override
        public String getOutputPath() {
            return null; // FIXME
        }
    }
}
