package eu.fasten.analyzer.pythonlicensefeeder;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.MetadataDao;
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

public class PythonLicenseFeederPlugin extends Plugin {

    public PythonLicenseFeederPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class PythonLicenseFeederExtension implements KafkaPlugin, DBConnector {

        private final Logger logger = LoggerFactory.getLogger(PythonLicenseFeederExtension.class.getName());

        protected Exception pluginError = null;
        private static DSLContext dslContext;
        private List<String> consumeTopics = new LinkedList<>(Collections.singletonList("fasten.PythonLicenseDetectorExtension.out"));

        @Override
        public void setDBConnection(Map<String, DSLContext> dslContexts) {
            // to check if the pypiForge is correct
            PythonLicenseFeederExtension.dslContext = dslContexts.get(Constants.pypiForge);
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

                String packageName = extractPackageName(record);
                String packageVersion = extractPackageVersion(record);
                String sourcePath = extractSourcePath(record);

                logger.info("Package name: " + packageName + ".");
                logger.info("Package version: " + packageVersion + ".");

                // Inserting detected outbound into the database
                var metadataDao = new MetadataDao(dslContext);
                dslContext.transaction(transaction -> {
                    metadataDao.setContext(DSL.using(transaction));
                    insertOutboundLicenses(packageName, packageVersion, record, metadataDao);
                    insertFileLicenses(packageName, packageVersion, sourcePath, record, metadataDao);
                });


            } catch (Exception e) { // Fasten error-handling guidelines
                logger.error(e.getMessage(), e.getCause());
                setPluginError(e);
            }
        }

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

        protected String extractSourcePath(String record) {
            var payload = new JSONObject(record);
            if (payload.has("payload")) {
                payload = payload.getJSONObject("payload");
            }
            JSONArray array1 = new JSONArray();
            array1 = payload.getJSONArray("files");
            logger.info("Repo path:");
            for (int j = 0; j < array1.length(); j++) {
                JSONObject obj2 = array1.getJSONObject(j);
                //System.out.println(obj2);
                if (obj2.has("sourcePath")){
                    String sourcePath = obj2.getString("sourcePath");
                    System.out.println(sourcePath);
                    return sourcePath;
                }
            }
            System.out.println("Repo path not retrieved.");
            return null;
        }

        /**
         * Inserts outbound licenses at the package version level.
         *
         * @param packageName,packageVersion the coordinates whose outbound licenses are about to be inserted.
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
        /**
         * Inserts licenses at the file level.
         *
         * @param packageName,packageVersion the coordinates whose outbound licenses are about to be inserted.
         * @param record      the input record containing outbound license findings.
         * @param metadataDao Data Access Object to insert records in the database.
         */
        protected void insertFileLicenses(String packageName, String packageVersion, String sourcePath, String record, MetadataDao metadataDao) {
            var payload = new JSONObject(record);
            if (payload.has("payload")) {
                payload = payload.getJSONObject("payload");
            }

            JSONArray fileLicenses = payload.getJSONArray("files");
            logger.info("About to insert file licenses...");
            fileLicenses.forEach(f -> {
                logger.info("(cycling files) Object f: " + f);
                JSONObject file = (JSONObject) f;
                logger.info("(cycling files) JSONObject f: " + file + " has " +
                        (file.has("path") ? "" : "no ") + "path and " +
                        (file.has("licenses") ? file.getJSONArray("licenses").length() : "no") + " license.");
                if (file.has("path") && file.has("licenses")) {
                    String path = file.getString("path");
                    path = path.replace(sourcePath+"/","");
                    logger.info("path before to insertFileLicenses:");
                    logger.info(path);
                    JSONArray FileLicenses = file.getJSONArray("licenses");
                    JSONArray FileLicensesParsed = new JSONArray();
                    JSONObject packageLicenseInfo = new JSONObject();
                    for (int i = 0; i < FileLicenses.length(); i++) {
                        JSONObject jsonObj = FileLicenses.getJSONObject(i);
                        if (jsonObj.has("spdx_license_key")){
                            String spdx_id = jsonObj.getString("spdx_license_key");
                            packageLicenseInfo.put("spdx_license_key", spdx_id);
                            FileLicensesParsed.put(packageLicenseInfo);
                        }
                        if (jsonObj.has("key")){
                            String license_key = jsonObj.getString("key");
                            System.out.println("license_key");
                            System.out.println(license_key);
                            packageLicenseInfo.put("key", license_key);
                            FileLicensesParsed.put(packageLicenseInfo);
                        }
                    } 
                    String fileMetadata = new JSONObject().put("licenses", FileLicensesParsed).toString();
                    metadataDao.insertFileLicenses(
                            packageName,
                            packageVersion,
                            path,
                            fileMetadata
                    );
                }
            });
            logger.info("...file licenses inserted.");
        }

        @Override
        public String name() {
            return "Python License Feeder Plugin";
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