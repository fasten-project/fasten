package eu.fasten.analyzer.baseanalyzer;

import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.plugins.KafkaPlugin;
import java.util.ArrayList;
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

    public abstract static class ANALYZER implements KafkaPlugin<String, String> {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        private String consumeTopic = "fasten.maven.pkg";
        private Throwable pluginError;
        private String record;

        @Override
        public Optional<List<String>> consumeTopics() {
            return Optional.of(new ArrayList<>(Collections.singletonList(consumeTopic)));
        }

        @Override
        public void consume(String kafkaRecord) {
            pluginError = null;
            this.record = kafkaRecord;
        }

        @Override
        public Optional<String> produce() {
            try {
                final var kafkaConsumedJson = new JSONObject(this.record);
                final var mavenCoordinate = getMavenCoordinate(kafkaConsumedJson);

                logger.info("Generating call graph for {}", mavenCoordinate.getCoordinate());
                final var cg = generateCallGraph(mavenCoordinate, kafkaConsumedJson);

                if (cg == null) {
                    return Optional.empty();
                }
                if (cg.isCallGraphEmpty()) {
                    logger.warn("Empty call graph for {}", mavenCoordinate.getCoordinate());
                    return Optional.of(cg.toJSON().toString());
                }

                logger.info("Call graph successfully generated for {}!",
                        mavenCoordinate.getCoordinate());

                return Optional.of(cg.toJSON().toString());

            } catch (Exception e) {
                setPluginError(e);
                logger.error("", e);
                return Optional.empty();
            }
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
        public abstract ExtendedRevisionCallGraph generateCallGraph(
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
            return "Generates call graphs for Java packages using OPAL";
        }

        @Override
        public boolean recordProcessSuccessful() {
            return this.pluginError == null;
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

        @Override
        public void setPluginError(Throwable throwable) {
            this.pluginError = throwable;
        }

        @Override
        public void freeResource() {
        }
    }
}
