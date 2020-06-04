package eu.fasten.analyzer.baseanalyzer;

import eu.fasten.core.data.RevisionCallGraph;
import eu.fasten.core.plugins.KafkaPlugin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.json.JSONException;
import org.json.JSONObject;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AnalyzerPlugin extends Plugin {
    public AnalyzerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    public abstract static class ANALYZER implements KafkaPlugin {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        private String consumeTopic = "fasten.maven.pkg";
        private Throwable pluginError;
        private RevisionCallGraph graph;

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(new ArrayList<>(Collections.singletonList(consumeTopic)));
        }

        @Override
        public void consume(String kafkaRecord) {
            pluginError = null;
            try {
                final var kafkaConsumedJson = new JSONObject(kafkaRecord);
                final var mavenCoordinate = getMavenCoordinate(kafkaConsumedJson);

                logger.info("Generating call graph for {}", mavenCoordinate.getCoordinate());
                this.graph = generateCallGraph(mavenCoordinate, kafkaConsumedJson);

                if (graph == null || graph.isCallGraphEmpty()) {
                    logger.warn("Empty call graph for {}", mavenCoordinate.getCoordinate());
                    return;
                }

                logger.info("Call graph successfully generated for {}!",
                        mavenCoordinate.getCoordinate());

            } catch (Exception e) {
                setPluginError(e);
                logger.error("", e);
            }
        }

        @Override
        public Optional<String> produce() {
            if (this.graph != null) {
                return Optional.of(graph.toJSON().toString());
            } else {
                return Optional.empty();
            }
        }

        @Override
        public String getOutputPath() {
            var productSplit = this.graph.product.split("\\.");

            var groupId = String.join(".", Arrays.copyOf(productSplit, productSplit.length - 1));
            var artifactId = productSplit[productSplit.length - 1];
            var version = this.graph.version;
            var product = artifactId + "_" + groupId + "_" + version;

            var firstLetter = artifactId.substring(0, 1);

            return "/mvn/" + firstLetter + "/" + artifactId + "/" + product + ".json";
        }

        /**
         * Convert consumed JSON from Kafka to {@link MavenCoordinate}.
         *
         * @param kafkaConsumedJson Coordinate JSON
         * @return MavenCoordinate
         */
        public MavenCoordinate getMavenCoordinate(final JSONObject kafkaConsumedJson) {

            try {
                return new MavenCoordinate(
                        kafkaConsumedJson.get("groupId").toString(),
                        kafkaConsumedJson.get("artifactId").toString(),
                        kafkaConsumedJson.get("version").toString());
            } catch (JSONException e) {
                setPluginError(e);
                logger.error("Could not parse input coordinates: {}\n{}", kafkaConsumedJson, e);
            }
            return null;
        }

        /**
         * Generate an ExtendedRevisionCallGraph.
         *
         * @param mavenCoordinate   Maven coordinate
         * @param kafkaConsumedJson Consumed JSON
         * @return Generated ExtendedRevisionCallGraph
         */
        public abstract RevisionCallGraph generateCallGraph(
                final MavenCoordinate mavenCoordinate,
                final JSONObject kafkaConsumedJson) throws Exception;

        @Override
        public void setTopic(String topicName) {
            this.consumeTopic = topicName;
        }

        @Override
        public String name() {
            return this.getClass().getCanonicalName();
        }

        @Override
        public String description() {
            return "Generates call graphs for Java packages";
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Throwable getPluginError() {
            return this.pluginError;
        }

        public void setPluginError(Throwable throwable) {
            this.pluginError = throwable;
        }

        @Override
        public void freeResource() {
        }
    }
}
