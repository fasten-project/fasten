package eu.fasten.analyzer.repoanalyzer;

import eu.fasten.core.plugins.KafkaPlugin;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.json.JSONException;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepoAnalyzerPlugin extends Plugin {

    public RepoAnalyzerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class RepoAnalyzerExtension implements KafkaPlugin {

        private final Logger logger = LoggerFactory.getLogger(RepoAnalyzerPlugin.class);

        private String consumerTopic = "fasten.RepoCloner.out";
        private Throwable pluginError;
        private JSONObject statistics;

        @Override
        public void consume(String record) {
            try {
                statistics = null;
                pluginError = null;
                long startTime = System.nanoTime();

                var json = new JSONObject(record);
                if (json.has("payload")) {
                    if (json.get("payload").toString().isEmpty()) {
                        var e = new RuntimeException("Empty payload");
                        setPluginError(e);
                        logger.error("[RECORD-PARSING] [FAILED] [-1] [NONE] " +
                                "[" + e.getClass().getSimpleName() + "]" +
                                "[" + e.getMessage() + "]", e);
                        return;
                    }
                    json = json.getJSONObject("payload");
                }

                final String repoPath = json.getString("repoPath");

                final var coordinate =
                        json.has("groupId") && json.has("artifactId") && json.has("version")
                        ? json.get("groupId") + ":" + json.get("artifactId") + ":" + json.get("version")
                        : "UNKNOWN-ARTIFACT";

                var analyzer = new RepoAnalyzer(repoPath);
                this.statistics = analyzer.analyze();

                long endTime = System.nanoTime();
                long duration = (endTime - startTime) / 1000000;
                logger.info("[REPO-ANALYSIS] [SUCCESS] [" + duration + "] [" + coordinate + "] [NONE]");
            } catch (JSONException e) {
                logger.error("[RECORD-PARSING] [FAILED] [-1] [NONE] " +
                        "[" + e.getClass().getSimpleName() + "]" +
                        "[" + e.getMessage() + "]", e);
                setPluginError(e);
            } catch (Exception e) {
                logger.error("[REPO-ANALYSIS] [FAILED] [-1] [NONE] " +
                        "[" + e.getClass().getSimpleName() + "]" +
                        "[" + e.getMessage() + "]", e);
                setPluginError(e);
            }
        }

        @Override
        public Optional<String> produce() {
            return Optional.of(statistics.toString());
        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public void setPluginError(Throwable throwable) {
            this.pluginError = throwable;
        }

        @Override
        public Throwable getPluginError() {
            return pluginError;
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
        public String getOutputPath() {
            return null;
        }

        @Override
        public String name() {
            return "Repo Analyzer Plugin";
        }

        @Override
        public String description() {
            return "Consumes records from RepoCloner and produces statistics " +
                    "about tests present in the cloned repository";
        }

        @Override
        public String version() {
            return "0.0.1";
        }

        @Override
        public void freeResource() {

        }
    }
}
