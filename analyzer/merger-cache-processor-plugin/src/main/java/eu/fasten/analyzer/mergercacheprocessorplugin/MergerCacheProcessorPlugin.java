package eu.fasten.analyzer.mergercacheprocessorplugin;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.dbconnectors.RocksDBConnector;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.DatabaseMerger;
import eu.fasten.core.plugins.KafkaPlugin;
import org.jooq.DSLContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MergerCacheProcessorPlugin extends Plugin {
    public MergerCacheProcessorPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class MergerCacheProcessorExtension implements KafkaPlugin {

        private final Logger logger = LoggerFactory.getLogger(MergerCacheProcessorExtension.class.getName());
        private String consumerTopic = "fasten.MergeCacheInvalidationPlugin.out";

        private Exception pluginError = null;

        private GraphMavenResolver graphResolver;

        private DSLContext dbContext;
        private MetadataDao kbDao;
        private RocksDao graphDao;


        /**
         * The helper method that creates a graph resolver.
         * It first creates a Database Context from Knowledge Base and
         * then uses it to build dependency graph in the graph resolver.
         *
         * @param kbUrl - the url of the knowledge base.
         * @param kbUser  - user for the knowledge base.
         * @param depGraphPath - the directory where the dependency graph can be found.
         * @param graphdbPath - the directory where the graph database can be found.
         */
        public void loadGraphResolver(String kbUrl, String kbUser, String depGraphPath, String graphdbPath) {
            logger.info("Building Dependency Graph from " + depGraphPath + "...");
            try {
                dbContext = PostgresConnector.getDSLContext(kbUrl, kbUser, true);
                kbDao = new MetadataDao(dbContext);
                graphDao = RocksDBConnector.createReadOnlyRocksDBAccessObject(graphdbPath);
                var graphResolver = new GraphMavenResolver();
                graphResolver.buildDependencyGraph(dbContext, depGraphPath);
                this.graphResolver = graphResolver;
            } catch (SQLException e) {
                var err = "Couldn't connect to the Knowledge Base";
                logger.error(err, e);
                this.setPluginError(new SQLException(err, e));
            } catch (RuntimeException e) {
                var err = "Couldn't connect to the Graph Database";
                logger.error(err, e);
                this.setPluginError(new RuntimeException(err, e));
            } catch (Exception e) {
                var err = "Couldn't build the Dependency Graph";
                logger.error(err, e);
                this.setPluginError(new RuntimeException(err, e));
            }
            logger.info("...Dependency Graph has been successfully built.");
        }

        /**
         * Helper method that traverses the directed graph and creates paths from source to vulnerable dependency.
         *
         * @param graph - directed graph of the dependencies.
         * @param source - source artifact.
         * @param target - target vulnerable dependency.
         * @param visited - helper argument for graph traverse.
         * @param path - helper argument for preliminary path.
         * @param vulnerablePaths - helper argument for preliminary vulnerable paths.
         * @return the list of paths for each vulnerable dependency.
         */
        private List<List<Long>> getPathsToVulnerableNode(DirectedGraph graph, long source, long target,
                                                  Set<Long> visited, List<Long> path, List<List<Long>> vulnerablePaths) {
            if (path.isEmpty()) {
                path.add(source);
            }
            if (source == target) {
                vulnerablePaths.add(new ArrayList<>(path));
                return vulnerablePaths;
            }
            visited.add(source);
            for (var node : graph.successors(source)) {
                if (!visited.contains(node)) {
                    path.add(node);
                    getPathsToVulnerableNode(graph, node, target, visited, path, vulnerablePaths);
                    path.remove(node);
                }
            }
            visited.remove(source);
            return vulnerablePaths;
        }

        @Override
        public String name() {
            return "Merger Cache Processor Plugin";
        }

        @Override
        public String description() {
            return "Merger Cache Processor Plugin. "
                    + "Consumes list MergeCacheInvalidationPlugin from Kafka"
                    + " topic and caches vulnerable paths to a file";
        }

        @Override
        public String version() {
            return "0.0.1";
        }

        @Override
        public void start() { }

        @Override
        public void stop() { }

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

            if (dbContext == null || kbDao == null || graphDao == null || graphResolver == null) {
                var errorMsg = "Graph Resolver is not initialized, but needed for the plugin. " +
                        "Please initialize the resolver with MergerCacheProcessorExtension.loadGraphResolver(...).";
                logger.error(errorMsg);
                setPluginError(new RuntimeException(errorMsg));
                return;
            }

            // Parse JSON object from kafka topic of GraphDBExtension.
            // Although it doesn't have output payload, the plugin serializes the graph for its input.
            // And we can use the input copy from this topic and the serialized graph to process our caching.
            var jsonPayload = new JSONObject(record);
            JSONArray json = new JSONArray();
            if (jsonPayload.has("payload")) {
                if (jsonPayload.get("payload").toString().isEmpty()) {
                    logger.error("Empty payload");
                    setPluginError(new RuntimeException("Empty payload"));
                    return;
                }
                json = jsonPayload.getJSONArray("payload");
            }

            for (Object revisionObj : json) {

                var revision = (Revision) revisionObj;

                var depSet = graphResolver.resolveDependencies(revision, dbContext, true);
                var depIds = depSet.stream().map(r -> r.id).collect(Collectors.toSet());
                var vulnerableDependencies = kbDao.findVulnerablePackageVersions(depIds);
                var databaseMerger = new DatabaseMerger(depIds, dbContext, graphDao);
                var graph = databaseMerger.mergeWithCHA(revision.product().toString() + Constants.mvnCoordinateSeparator + revision.version.toString());
                var vulnerabilities = kbDao.findVulnerableCallables(vulnerableDependencies, graph.nodes());
                var internalCallables = kbDao.getPackageInternalCallableIDs(revision.product().toString(), revision.version.toString());

                // Find all paths between any internal node and any vulnerable node in the graph
                var vulnerablePaths = new ArrayList<List<Long>>();
                for (var internal : internalCallables) {
                    for (var vulnerable : vulnerabilities.keySet()) {
                        vulnerablePaths.addAll(getPathsToVulnerableNode(graph, internal, vulnerable, new HashSet<>(), new ArrayList<>(), new ArrayList<>()));
                    }
                }

                // Group vulnerable path by the vulnerabilities
                var vulnerabilitiesMap = new HashMap<String, List<List<Long>>>();
                for (var path : vulnerablePaths) {
                    var pathVulnerabilities = vulnerabilities.get(path.get(path.size() - 1)).keySet();
                    for (var vulnerability : pathVulnerabilities) {
                        if (vulnerabilitiesMap.containsKey(vulnerability)) {
                            var paths = vulnerabilitiesMap.get(vulnerability);
                            var updatedPaths = new ArrayList<>(paths);
                            updatedPaths.add(path);
                            vulnerabilitiesMap.remove(vulnerability);
                            vulnerabilitiesMap.put(vulnerability, updatedPaths);
                        } else {
                            var paths = new ArrayList<List<Long>>();
                            paths.add(path);
                            vulnerabilitiesMap.put(vulnerability, paths);
                        }
                    }
                }

                var pathNodes = new HashSet<Long>();
                vulnerablePaths.forEach(pathNodes::addAll);
                var fastenUris = kbDao.getFullFastenUris(new ArrayList<>(pathNodes));

                // Generate JSON response
                var output = new JSONObject();
                for (var entry : vulnerabilitiesMap.entrySet()) {
                    var pathsJson = new JSONArray();
                    for (var path : entry.getValue()) {
                        var pathJson = new JSONArray();
                        for (var node : path) {
                            var jsonNode = new JSONObject();
                            jsonNode.put("id", node);
                            jsonNode.put("fasten_uri", fastenUris.get(node));
                            pathJson.put(jsonNode);
                        }
                        pathsJson.put(pathJson);
                    }
                    output.put(entry.getKey(), pathsJson);
                }

                // Write the output into the file
                try {
                    var firstLetter = revision.artifactId.substring(0, 1);
                    var outputPath = File.separator + firstLetter +
                            File.separator + revision.artifactId +
                            File.separator + revision.product().toString() +
                            File.separator + revision.version.toString() + ".json";

                    FileWriter outputFile = new FileWriter(outputPath);
                    outputFile.write(output.toString());
                } catch (IOException e) {
                    logger.error("Unable to persist the cache output in the file", e);
                    setPluginError(e);
                    return;
                }
            }

        }

        @Override
        public Optional<String> produce() {
            return Optional.empty();
        }

        @Override
        public String getOutputPath() {
            return null;
        }
    }
}

