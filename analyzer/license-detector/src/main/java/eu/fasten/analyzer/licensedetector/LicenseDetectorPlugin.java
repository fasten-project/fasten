package eu.fasten.analyzer.licensedetector;

import eu.fasten.analyzer.licensedetector.license.DetectedLicense;
import eu.fasten.analyzer.licensedetector.license.DetectedLicenseSource;
import eu.fasten.analyzer.licensedetector.license.DetectedLicenses;
import eu.fasten.core.maven.data.Revision;
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
import java.sql.Timestamp;
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

        /**
         * TODO
         */
        protected DetectedLicenses detectedLicenses = new DetectedLicenses();

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

                logger.info("License detector started");

                // Retrieving the repository path on the shared volume
                String repoPath = extractRepoPath(record);

                // Retrieving the Maven coordinate of this input record
                Revision coordinate = extractMavenCoordinate(record);

                // Retrieving the `pom.xml` file
                File pomFile = retrievePomFile(repoPath);

                // TODO Checking whether the repository has already been scanned (by querying the KB)

                // Retrieving the outbound license
                detectedLicenses.setOutbound(getOutboundLicenses(pomFile, coordinate));
                logger.info(
                        detectedLicenses.getOutbound().size() + " outbound license" +
                                (detectedLicenses.getOutbound().size() == 1 ? "" : "s") + " detected: " +
                                detectedLicenses.getOutbound()
                );

                // TODO Detecting inbound licenses by scanning the project

                // TODO Unzipping the JAR to determine which files actually form the package
                // TODO Use the `sourcesUrl` field in the `fasten.RepoCloner.out` input record


            } catch (Exception e) { // Fasten error-handling guidelines
                logger.error(e.getMessage(), e.getCause());
                setPluginError(e);
            }
        }

        /**
         * Retrieves all licenses declared in a `pom.xml` file.
         *
         * @param pomFile      the `pom.xml` file to be analyzed.
         * @param coordinate   the Maven coordinate this `pom.xml` file belongs to.
         * @return the detected licenses.
         * @throws XmlPullParserException in case the `pom.xml` file couldn't be parsed as an XML file.
         */
        protected Set<DetectedLicense> getLicensesFromPomFile(File pomFile,
                                                              Revision coordinate) throws XmlPullParserException {

            // Result
            List<License> licenses;

            // Maven `pom.xml` file parser
            MavenXpp3Reader reader = new MavenXpp3Reader();
            try (FileReader fileReader = new FileReader(pomFile)) {

                // Parsing and retrieving the `licenses` XML tag
                Model model = reader.read(fileReader);
                licenses = model.getLicenses();

                // If the pom file contains at least a license tag
                if (!licenses.isEmpty()) {

                    // Logging
                    logger.trace("Found " + licenses.size() + " outbound license" + (licenses.size() == 1 ? "" : "s") +
                            " in " + pomFile.getAbsolutePath() + ":");
                    for (int i = 0; i < licenses.size(); i++) {
                        logger.trace("License number " + i + ": " + licenses.get(i).getName());
                    }

                    // Returning the set of discovered licenses
                    Set<DetectedLicense> result = new HashSet<>(Collections.emptySet());
                    licenses.forEach(license -> result.add(new DetectedLicense(
                            license.getName(),
                            DetectedLicenseSource.LOCAL_POM,
                            coordinate)));
                    return result;
                }
            } catch (IOException e) {
                throw new RuntimeException("Pom file " + pomFile.getAbsolutePath() +
                        " exists but couldn't instantiate a FileReader object..");
            } catch (XmlPullParserException e) {
                throw new XmlPullParserException("Pom file " + pomFile.getAbsolutePath() +
                        " exists but couldn't be parsed as a Maven pom XML file: " + e.getMessage());
            }

            // No licenses were detected
            return Collections.emptySet();
        }

        /**
         * Retrieves outbound licenses of the analyzed project.
         *
         * @param pomFile    the repository `pom.xml` file.
         * @param coordinate the Maven coordinate this `pom.xml` file belongs to.
         * @return a set of detected outbound licenses.
         * @throws XmlPullParserException in case the `pom.xml` file couldn't be parsed as an XML file.
         */
        protected Set<DetectedLicense> getOutboundLicenses(File pomFile,
                                                           Revision coordinate) throws XmlPullParserException {

            // Retrieving outbound licenses from the local `pom.xml` file.
            Set<DetectedLicense> licenses = getLicensesFromPomFile(pomFile, coordinate);

            // TODO Retrieving licenses from Maven central
//            if (licenses.isEmpty()) { // in case no licenses have been found in the local `pom.xml` file
//
//            }

            // TODO Retrieving licenses from the GitHub API

            // Return all detected licenses
            return licenses;
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
         * Retrieves the Maven coordinate of the input record.
         *
         * @param record the input record containing repository information.
         * @return the Maven coordinate of the input record.
         * @throws IllegalArgumentException in case the function couldn't find coordinate information
         *                                  in the input record.
         */
        protected Revision extractMavenCoordinate(String record) {
            var payload = new JSONObject(record);
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
         * Retrieves the pom.xml file given a repository path.
         *
         * @param repoPath the repository path whose pom.xml file must be retrieved.
         * @return the pom.xml file of the repository.
         * @throws FileNotFoundException in case no pom.xml file could be found in the repository.
         */
        protected File retrievePomFile(String repoPath) throws FileNotFoundException {

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

            if (pomFile.isEmpty()) {
                throw new FileNotFoundException("No file named pom.xml found in " + repoPath + ". " +
                        "This plugin only analyzes Maven projects.");
            }

            return pomFile.get();
        }

        /**
         * Pretty-prints Maven coordinates.
         *
         * @param groupId    the maven coordinate's group ID.
         * @param artifactId the maven coordinate's artifact ID.
         * @param version    the maven coordinate's version.
         * @return a pretty String representation of the input Maven coordinate.
         */
        protected static String constructMavenArtifactName(String groupId, String artifactId, String version) {
            return groupId + ":" + artifactId + ":" + version;
        }

        @Override
        public Optional<String> produce() {
            if (detectedLicenses == null ||
                    (detectedLicenses.getOutbound().isEmpty() && detectedLicenses.getDependencies().isEmpty())) {
                return Optional.empty();
            } else {
                return Optional.of(new JSONObject(detectedLicenses).toString());
            }
        }

        @Override
        public String getOutputPath() {
            return null; // FIXME
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
            return false;
        }

        @Override
        public long getMaxConsumeTimeout() {
            return 1 * 60 * 60 * 1000; // FIXME 1 hour
        }
    }
}
