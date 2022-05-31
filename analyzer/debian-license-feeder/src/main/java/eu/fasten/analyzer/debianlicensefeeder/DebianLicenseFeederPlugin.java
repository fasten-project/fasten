package eu.fasten.analyzer.debianlicensefeeder;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.MetadataDao;
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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

//This import should probably be different

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
                String productName = extractProductName(record);

                //Revision coordinates = extractDebianCoordinates(record);
                logger.info("Package name: " + packageName + ".");
                logger.info("Package version: " + packageVersion + ".");
                logger.info("Product name: " + productName + ".");
                // Inserting detected outbound into the database
                var metadataDao = new MetadataDao(dslContext);
                //System.out.println("After new MetadataDao(dslContext) ");
                dslContext.transaction(transaction -> {
                    metadataDao.setContext(DSL.using(transaction));
                    insertOutboundLicenses(productName, packageVersion, record, metadataDao);
                    insertFileLicenses(productName, packageVersion, record, metadataDao);
                });

                // TODO Inserting licenses in files

            } catch (Exception e) { // Fasten error-handling guidelines
                logger.error(e.getMessage(), e.getCause());
                throw e;
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
            System.out.println("Package name not retrieved.");
            return null;
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



        protected String extractProductName(String record) {
            var payload = new JSONObject(record);
            if (payload.has("payload")) {
                payload = payload.getJSONObject("payload");
            }
            JSONArray array1 = payload.getJSONArray("files");
            logger.info("Product name:");
            for (int j = 0; j < array1.length(); j++) {
                JSONObject obj2 = array1.getJSONObject(j);
                //System.out.println(obj2);
                if (obj2.has("productName")){
                    String productName = obj2.getString("productName");
                    System.out.println(productName);
                    return productName;
                }
            }
            System.out.println("Product name not retrieved.");
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
            //System.out.println("[BEGINNING] inside of insertOutboundLicenses");
            var payload = new JSONObject(record);
            if (payload.has("payload")) {
                payload = payload.getJSONObject("payload");
            }

            JSONArray outboundLicenses = payload.getJSONArray("outbound");
            //System.out.println("After payload.getJSONArray outbound");
            System.out.println(outboundLicenses);
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
                //this debug message is not correct for the Debian use case TODO modify it accordingly
                logger.debug("(cycling files) JSONObject f: " + file + " has " +
                        (file.has("path") ? "" : "no ") + "path and " +
                        (file.has("licenses") ? file.getJSONArray("licenses").length() : "no") + " licenses.");
                if (file.has("path") && file.has("license")) {
                    JSONObject licenseObject = new JSONObject();
                    licenseObject.put("name", file.getString("license"));
                    licenseObject.put("source", "DEBIAN_PACKAGES");
                    JSONArray ja = new JSONArray();
                    ja.put(licenseObject);
                    metadataDao.insertFileLicenses(
                            packageName,
                            packageVersion,
                            file.getString("path"),
                            new JSONObject().put("licenses", ja).toString()
                            //new JSONObject().put("license", file.getString("license")).toString()
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
