package eu.fasten.analyzer.licensedetector;

import eu.fasten.core.plugins.KafkaPlugin;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
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

        /**
         * Patch to be applied to pom.xml files before the analysis begins.
         */
        protected static final String POM_PATCH_FILE = "pom-patch.xml";

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

                patchPomFile(repoPath);

            } catch (Exception e) { // Fasten error-handling guidelines
                logger.error(e.getMessage());
                setPluginError(e);
            }
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

        protected File patchPomFile(String repoPath) throws IllegalArgumentException, ParserConfigurationException {

            // Retrieving the pom file
            Optional<File> pomFile = retrievePomFile(repoPath);
            if (pomFile.isEmpty()) {
                throw new IllegalArgumentException("No file named pom.xml found in " + repoPath + ". " +
                        "This plugin only analyzes Maven projects.");
            }

            // Retrieving the patch XML file
            File patchFile = new File(Objects.requireNonNull(LicenseDetector.class.getClassLoader()
                    .getResource(POM_PATCH_FILE)).getFile());

            // Checking whether these two XML files are well-formed
            if (xmlFileIsWellFormed(pomFile.get())) {
                throw new IllegalArgumentException("Repository's pom.xml is malformed.");
            }
            if (xmlFileIsWellFormed(patchFile)) {
                throw new IllegalArgumentException("Path file is malformed.");
            }

            return null; // FIXME
        }

        protected boolean xmlFileIsWellFormed(File xmlFile) throws ParserConfigurationException {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                    logger.warn(exception.getMessage());
                }

                @Override
                public void error(SAXParseException exception) {
                    logger.error(exception.getMessage());
                }

                @Override
                public void fatalError(SAXParseException exception) {
                    logger.error("Fatal: " + exception.getMessage());
                }
            });

            try {
                Document parse = builder.parse(xmlFile);
            } catch (SAXException e) {
                logger.warn("XML file " + xmlFile.getAbsolutePath() + " is malformed.");
                return true;
            } catch (IOException e) {
                throw new IllegalArgumentException("File " + xmlFile.getAbsolutePath() + " not found.");
            }

            return false;
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
