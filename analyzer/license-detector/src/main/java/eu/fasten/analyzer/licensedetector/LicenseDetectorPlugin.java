package eu.fasten.analyzer.licensedetector;

import eu.fasten.core.plugins.KafkaPlugin;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;


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
        protected String inputTopic = "fasten.RepoCloner.out";

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(Collections.singletonList(inputTopic));
        }

        @Override
        public void setTopic(String topicName) {
            this.inputTopic = topicName;
        }

        @Override
        public void consume(String record) {
            try { // Fasten error-handling guidelines

                this.pluginError = null;

                logger.info("License detector started.");

                // Retrieving the repository path on the shared volume
                String repoPath = extractRepoPath(record);
                logger.info("License detector: scanning repository in " + repoPath + "...");

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
            return 1 * 60 * 60 * 1000; // 1 hour
        }
    }
}
