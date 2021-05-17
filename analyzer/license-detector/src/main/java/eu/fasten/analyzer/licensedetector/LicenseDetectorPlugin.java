package eu.fasten.analyzer.licensedetector;

import eu.fasten.analyzer.licensedetector.license.DetectedLicense;
import eu.fasten.analyzer.licensedetector.license.DetectedLicenseSource;
import eu.fasten.core.plugins.KafkaPlugin;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;


public class LicenseDetectorPlugin extends Plugin {

    public LicenseDetectorPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class LicenseDetector implements KafkaPlugin {

        private final Logger logger = LoggerFactory.getLogger(LicenseDetector.class.getName());

        protected Exception pluginError = null;

        /**
         * The topic this plugin consumes.
         */
        protected String consumerTopic = "fasten.RepoCloner.out";

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
            try { // Fasten error-handling guidelines

                this.pluginError = null;

                logger.info("License detector started.");

                // Retrieving the repository path on the shared volume
                String repoPath = extractRepoPath(record);
                logger.info("License detector: scanning repository in " + repoPath + "...");

                // TODO Checking whether the repository has already been scanned (by querying the KB)

                // TODO Retrieving the outbound license
                Set<DetectedLicense> outboundLicenses = getOutboundLicenses(repoPath);

            } catch (Exception e) { // Fasten error-handling guidelines
                logger.error(e.getMessage());
                setPluginError(e);
            }
        }

        protected Set<DetectedLicense> getOutboundLicenses(String repoPath)
                throws FileNotFoundException, XmlPullParserException {

            // Retrieving the `pom.xml` file
            Optional<File> optionalPomFile = retrievePomFile(repoPath);
            if (optionalPomFile.isEmpty()) {
                throw new FileNotFoundException("No file named pom.xml found in " + repoPath + ". " +
                        "This plugin only analyzes Maven projects.");
            }
            File pomFile = optionalPomFile.get();

            // Looking for licenses in the local `pom.xml` file FIXME
            List<License> licenses;
            MavenXpp3Reader reader = new MavenXpp3Reader();
            try(FileReader fileReader = new FileReader(pomFile)) {

                // Parsing and retrieving the `licenses` XML tag
                Model model = reader.read(fileReader);
                licenses = model.getLicenses();

                // If the pom file contains at least a license tag
                if (!licenses.isEmpty()) {

                    // Logging
                    logger.info("Found some licenses in :" + repoPath);
                    for (int i = 0; i < licenses.size(); i++) {
                        logger.info("License number " + i + ": " + licenses.get(i).getName());
                    }

                    // Returning the set of discovered licenses
                    Set<DetectedLicense> result = new HashSet<>(Collections.emptySet());
                    licenses.forEach(license ->
                            result.add(new DetectedLicense(license.getName(), DetectedLicenseSource.LOCAL_POM)));
                    return result;
                }
            } catch (IOException e) {
                throw new RuntimeException("Pom file " + pomFile.getAbsolutePath() +
                        " exists but couldn't instantiate a FileReader object..");
            } catch (XmlPullParserException e) {
                throw new XmlPullParserException("Pom file " + pomFile.getAbsolutePath() +
                        " exists but couldn't be parsed as a Maven pom XML file: " + e.getMessage());
            }

            // TODO Retrieving licenses from Maven central
//            if (licenses.isEmpty()) { // in case no licenses have been found in the local `pom.xml` file
//
//            }

            // TODO Retrieving licenses from the GitHub API

            return Collections.emptySet(); // FIXME
        }

        /**
         * Retrieves the cloned repository path on the shared volume from the input record.
         *
         * @param record the input record containing repository information.
         * @return the repository path on the shared volume
         * @throws IllegalArgumentException in case the function couldn't find the repository path in the input record.
         */
        protected String extractRepoPath(String record) throws IllegalArgumentException {
            var payload = new JSONObject(record);
            if (payload.has("payload")) {
                payload = payload.getJSONObject("payload");
            }
            String repoPath = payload.getString("repoPath");
            if (repoPath == null) {
                throw new IllegalArgumentException("Invalid repository information: missing repository path.");
            }
            return repoPath;
        }

        /**
         * Retrieves the pom.xml file given a repository path.
         *
         * @param repoPath the repository path whose pom.xml file must be retrieved.
         * @return the pom.xml file of the repository.
         */
        protected Optional<File> retrievePomFile(String repoPath) {

            // Result
            Optional<File> pomFile = Optional.empty();

            // Repository folder
            File repoFolder = new File(repoPath);

            // Retrieving all repository's pom files
            File[] pomFiles = repoFolder.listFiles((dir, name) -> name.equalsIgnoreCase("pom.xml"));
            if (pomFiles == null) {
                throw new RuntimeException(repoPath + " does not denote a directory.");
            }
            logger.info("Found " + pomFiles.length + " pom.xml file" +
                    ((pomFiles.length == 1) ? "" : "s") + ": " + Arrays.toString(pomFiles));
            if (pomFiles.length == 0) {
                logger.error("No pom.xml file found in " + repoFolder.getAbsolutePath() + ".");
            } else if (pomFiles.length == 1) {
                pomFile = Optional.ofNullable(pomFiles[0]);
            } else {
                // Retrieving the pom.xml file having the shortest path (closest to it repository's root path)
                pomFile = Arrays.stream(pomFiles).min(Comparator.comparingInt(f -> f.getAbsolutePath().length()));
                logger.info("Multiple pom.xml files found. Using " + pomFile.get());
            }

            return pomFile;
        }

        @Override
        public Optional<String> produce() {
            return Optional.empty(); // this plugin only inserts data into the Metadata DB
        }

        @Override
        public String getOutputPath() {
            /*  A JSON file with detected licenses is available in another container.
                Licenses are inserted into the Metadata DB. */
            return null;
        }

        @Override
        public String name() {
            return "License Detector Plugin";
        }

        @Override
        public String description() {
            return "Detects licenses at the file level";
        }

        @Override
        public String version() {
            return "0.0.2";
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

        public void setPluginError(Exception throwable) {
            this.pluginError = throwable;
        }

        @Override
        public void freeResource() {
        }

        @Override
        public boolean isStaticMembership() {
            /*  The Pod behind license detection contains containers that are supposed to terminate
                upon consuming one record. This avoids rebalancing. */
            return true;
        }

        @Override
        public long getMaxConsumeTimeout() {
            return 1 * 60 * 60 * 1000; // FIXME 1 hour
        }
    }
}
