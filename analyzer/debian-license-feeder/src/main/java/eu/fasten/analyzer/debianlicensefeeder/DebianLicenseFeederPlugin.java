package eu.fasten.analyzer.debianlicensefeeder;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.MetadataDao;
//This import should probably be different
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.plugins.AbstractKafkaPlugin;
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
import java.util.*;

public class DebianLicenseFeederPlugin extends Plugin {

    public DebianLicenseFeederPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class DebianLicenseFeederExtension implements KafkaPlugin, DBConnector {

        private final Logger logger = LoggerFactory.getLogger(DebianLicenseFeederExtension.class.getName());

        protected Exception pluginError = null;
        private static DSLContext dslContext;
        private List<String> consumeTopics = new LinkedList<>(Collections.singletonList("fasten.DebianLicenseDetectorExtension.out"));

        @Override
        public void setDBConnection(Map<String, DSLContext> dslContexts) {
            DebianLicenseFeederExtension.dslContext = dslContexts.get(Constants.debianForge);
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
        public void consume(String record) {
            try { // Fasten error-handling guidelines

                this.pluginError = null;

                logger.info("License feeder started.");

                // Retrieving coordinates of the input record

                String packageName = extractPackageName(record);
                String packageVersion = extractPackageVersion(record);

                //Revision coordinates = extractDebianCoordinates(record);
                logger.info("Package name: " + packageName + ".");
                logger.info("Package version: " + packageVersion + ".");

                // Inserting detected outbound into the database
                var metadataDao = new MetadataDao(dslContext);
                dslContext.transaction(transaction -> {
                    metadataDao.setContext(DSL.using(transaction));
                    insertOutboundLicenses(packageName, packageVersion, record, metadataDao);
                    insertFileLicenses(packageName, packageVersion, record, metadataDao);
                });

                // TODO Inserting licenses in files

            } catch (Exception e) { // Fasten error-handling guidelines
                logger.error(e.getMessage(), e.getCause());
                setPluginError(e);
            }
        }

        /**
         * Retrieves the C/Debian coordinate of the input record (product and version).
         * TODO Unit tests.
         *
         * @param record the input record containing repository information (`fasten.MetadataDBCExtension.out`).
         * @return the product and version of the input record.
         * @throws IllegalArgumentException in case the function couldn't find coordinate information
         *                                  in the input record.
         */
        // To check if Revision is still an acceptable type of data



        protected String extractPackageName(String record) {
            var payload = new JSONObject(record);
            if (payload.has("payload")) {
                payload = payload.getJSONObject("payload");
            }
            JSONArray array1 = payload.getJSONArray("files");
            logger.info("Package name:");
            for (int j = 0; j < array1.length(); j++) {
                JSONObject obj2 = array1.getJSONObject(j);
                //System.out.println(obj2);
                if (obj2.has("packageName")){
                    String packageName = obj2.getString("packageName");
                    System.out.println(packageName);
                    return packageName;
                }
            }
            System.out.println("Package version not retrieved.");
            return null;
        }

        protected String extractPackageVersion(String record) {
            var payload = new JSONObject(record);
            if (payload.has("payload")) {
                payload = payload.getJSONObject("payload");
            }
            JSONArray array1 = new JSONArray();
            array1 = payload.getJSONArray("files");
            logger.info("Package version:");
            for (int j = 0; j < array1.length(); j++) {
                JSONObject obj2 = array1.getJSONObject(j);
                //System.out.println(obj2);
                if (obj2.has("packageVersion")){
                    String packageVersion = obj2.getString("packageVersion");
                    System.out.println(packageVersion);
                    return packageVersion;
                }
            }
            System.out.println("Package version not retrieved.");
            return null;
        }

        /**
         * Inserts outbound licenses at the package version level.
         *
         * @param coordinates the coordinates whose outbound licenses are about to be inserted.
         * @param record      the input record containing outbound license findings.
         * @param metadataDao Data Access Object to insert records in the database.
         */
        protected void insertOutboundLicenses(String packageName, String packageVersion, String record, MetadataDao metadataDao) {
            var payload = new JSONObject(record);
            if (payload.has("payload")) {
                payload = payload.getJSONObject("payload");
            }

            JSONArray outboundLicenses = payload.getJSONArray("outbound");
            logger.info("About to insert outbound licenses...");
            metadataDao.insertPackageOutboundLicenses(
                    packageName,
                    packageVersion,
                    new JSONObject().put("licenses", outboundLicenses).toString()
            );
            logger.info("...outbound licenses inserted.");
        }

        protected void insertFileLicenses(String packageName, String packageVersion, String record, MetadataDao metadataDao) {
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
                            packageName,
                            packageVersion,
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
        public Optional<String> produce() {
            return Optional.empty(); // FIXME
        }

        @Override
        public String getOutputPath() {
            return null; // FIXME
        }
    }
}