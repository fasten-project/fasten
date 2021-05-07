package eu.fasten.analyzer.mergercacheinvalidationplugin;

import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.plugins.KafkaPlugin;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MergerCacheInvalidationPlugin extends Plugin {
    public MergerCacheInvalidationPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class MergerCacheInvalidationExtension implements KafkaPlugin {

        private final Logger logger = LoggerFactory.getLogger(MergerCacheInvalidationExtension.class.getName());

        private String consumerTopic = "fasten.GraphDBExtension.out";

        private GraphMavenResolver graphResolver;
        private Exception pluginError = null;
        private ObjectLinkedOpenHashSet<Revision> depSet;

        /**
         * The helper method that creates a graph resolver.
         * It first creates a Database Context from Knowledge Base and
         * then uses it to build dependency graph in the graph resolver.
         *
         * @param depGraphPath - the directory where the dependency graph can be found.
         */
        public void loadGraphResolver(String depGraphPath) {
            logger.info("Building Dependency Graph from " + depGraphPath + "...");
            try {
                var graphResolver = new GraphMavenResolver();
                graphResolver.buildDependencyGraph(null, depGraphPath);
                this.graphResolver = graphResolver;
            } catch (Exception e) {
                logger.error("Couldn't build the dependency graph", e);
                System.exit(1);
            }
            logger.info("...Dependency Graph has been successfully built.");
        }

        @Override
        public String name() {
            return "Merger Cache Invalidation Plugin";
        }

        @Override
        public String description() {
            return "Merger Cache Invalidation Plugin. "
                    + "Consumes list of updated product from Kafka"
                    + " topic and invalidates cache of vulnerable paths"
                    + " for all its transitive dependants.";
        }

        @Override
        public String version() {
            return "0.0.1";
        }

        @Override
        public void start() { }

        @Override
        public void stop() {
            this.graphResolver = null;
        }

        public void setPluginError(Exception throwable) {
            this.pluginError = throwable;
        }

        @Override
        public Exception getPluginError() {
            return this.pluginError;
        }

        @Override
        public void freeResource() { }

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
            this.pluginError = null;

            // Parse JSON object from kafka topic of GraphDBExtension.
            // Although it doesn't have output payload, the plugin serializes the graph for its input.
            // And we can use the input copy from this topic and the serialized graph to process our caching.
            var json = new JSONObject(record);
            if (json.has("input")) {
                if (json.get("input").toString().isEmpty()) {
                    logger.error("Empty input");
                    setPluginError(new RuntimeException("Empty input"));
                    return;
                }
                json = json.getJSONObject("input");
            }

            // Parse input values for the root product.
            String groupId = "";
            String artifactId = "";
            String version = "";
            try {
                if (!json.has("product")) {
                    throw new JSONException("product");
                }
                if (!json.has("version")) {
                    throw new JSONException("version");
                }
                version = json.get("version").toString();
                var product = json.get("product").toString();
                var splits = product.split(Constants.mvnCoordinateSeparator);
                groupId = splits[0];
                artifactId = splits[1];
            } catch (JSONException e) {
                logger.error("Error parsing product for vulnerability cache invalidator", e);
                setPluginError(e);
            }

            // Resolve the set of transitive dependants for this product.
            this.depSet = this.graphResolver.dependentBFS(groupId, artifactId, version, -1, true);

            // Go over the set and invalidate the cache for each dependant.
            for (Revision revision : depSet) {
                var firstLetter = revision.artifactId.substring(0, 1);
                var outputPath = File.separator + firstLetter +
                                 File.separator + revision.artifactId +
                                 File.separator + revision.product().toString() +
                                 File.separator + revision.version.toString() + ".json";

                File outputFile = new File(outputPath);
                if(!outputFile.exists() || outputFile.length() == 0) return;

                // TODO: possible optimization: we don't really need to delete the whole file, or do we?
                var result = outputFile.delete();
                if(result) {
                    logger.debug("Successfully invalidated cache for a dependant: " + outputPath);
                } else {
                    logger.error("Error in invalidating a dependant cache file: " + outputPath);
                }
            }
        }

        @Override
        public Optional<String> produce() {
            if (this.depSet == null) {
                return Optional.empty();
            } else {
                var json = new JSONArray();
                // TODO: possible optimization: we don't really need to pass the whole revision.
                var depSetJson = depSet.stream().map(Revision::toJSON);
                json.put(depSetJson);
                return Optional.of(json.toString());
            }
        }

        @Override
        public String getOutputPath() {
            return null;
        }
    }

}

