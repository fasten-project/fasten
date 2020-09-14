package eu.fasten.analyzer.mavenresolver;

import eu.fasten.analyzer.mavenresolver.data.MavenCoordinate;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.plugins.DBConnector;
import eu.fasten.core.plugins.KafkaPlugin;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jooq.DSLContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

public class MavenResolverPlugin extends Plugin {

    public MavenResolverPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class MavenResolver implements KafkaPlugin, DBConnector {

        private String consumerTopic = "fasten.POMAnalyzer.out";
        private final Logger logger = LoggerFactory.getLogger(MavenResolver.class.getName());
        private Throwable pluginError = null;
        private static DSLContext dslContext;
        private String artifact = null;
        private String group = null;
        private String version = null;
        private long timestamp = -1;
        private List<MavenCoordinate> resolvedDependencies = null;

        @Override
        public void setDBConnection(DSLContext dslContext) {
            MavenResolver.dslContext = dslContext;
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
        public void consume(String record) {
            pluginError = null;
            artifact = null;
            group = null;
            version = null;
            timestamp = -1;
            resolvedDependencies = null;
            logger.info("Consumed: " + record);
            var jsonRecord = new JSONObject(record);
            var payload = new JSONObject();
            if (jsonRecord.has("payload")) {
                payload = jsonRecord.getJSONObject("payload");
            } else {
                payload = jsonRecord;
            }
            artifact = payload.getString("artifactId").replaceAll("[\\n\\t ]", "");
            group = payload.getString("groupId").replaceAll("[\\n\\t ]", "");
            version = payload.getString("version").replaceAll("[\\n\\t ]", "");
            timestamp = payload.optLong("timestamp", -1L);
            var coordinate = group + Constants.mvnCoordinateSeparator + artifact
                    + Constants.mvnCoordinateSeparator + version;
            logger.info("Resolving " + coordinate);
            try {
                resolvedDependencies = resolveArtifactDependencies(coordinate, timestamp);
            } catch (Exception e) {
                logger.error("Error resolving " + coordinate, e);
                pluginError = e;
            }
            if (pluginError == null) {
                logger.info("Successfully resolved " + coordinate);
            }
        }

        public List<MavenCoordinate> resolveArtifactDependencies(String mavenCoordinate,
                                                                 long timestamp) {
            var artifacts = Arrays.stream(
                    Maven.resolver()
                            .resolve(mavenCoordinate)
                            .withTransitivity()
                            .asResolvedArtifact()
            ).collect(Collectors.toList());
            if (artifacts.size() < 1) {
                throw new RuntimeException("Could not resolve artifact " + mavenCoordinate);
            }
            var dependencies = Arrays.stream(
                    artifacts.get(0).getDependencies()
            ).map(d -> new MavenCoordinate(
                            d.getCoordinate().getGroupId(),
                            d.getCoordinate().getArtifactId(),
                            d.getResolvedVersion()
                    )
            ).collect(Collectors.toList());
            if (timestamp != -1) {
                return filterByTimestamp(dependencies, timestamp);
            } else {
                return dependencies;
            }
        }

        private List<MavenCoordinate> filterByTimestamp(List<MavenCoordinate> artifacts,
                                                        long timestamp) {
            var filteredArtifacts = new ArrayList<MavenCoordinate>();
            for (var artifact : artifacts) {
                var filtered = false;
                var packageName = artifact.getGroupId() + Constants.mvnCoordinateSeparator
                        + artifact.getArtifactId();
                var timestampedVersions = getTimestampedVersionsFromDB(packageName);
                for (var versionEntry : timestampedVersions.entrySet()) {
                    if (versionEntry.getValue().equals(artifact.getVersion())
                            && versionEntry.getKey() <= timestamp) {
                        filteredArtifacts.add(artifact);
                        filtered = true;
                        break;
                    }
                }
                if (!filtered) {
                    var timestamps = new ArrayList<>(timestampedVersions.keySet());
                    Collections.sort(timestamps);
                    var latestTimestamp = -1L;
                    for (var t : timestamps) {
                        if (t <= timestamp) {
                            latestTimestamp = t;
                        }
                    }
                    var properVersion = timestampedVersions.get(latestTimestamp);
                    var downgradedArtifact = new MavenCoordinate(
                            artifact.getGroupId(),
                            artifact.getArtifactId(),
                            properVersion
                    );
                    filteredArtifacts.add(downgradedArtifact);
                }
            }
            return filteredArtifacts;
        }

        private Map<Long, String> getTimestampedVersionsFromDB(String packageName) {
            var versions = dslContext.select(
                    PackageVersions.PACKAGE_VERSIONS.VERSION,
                    PackageVersions.PACKAGE_VERSIONS.CREATED_AT
            ).from(PackageVersions.PACKAGE_VERSIONS)
                    .join(Packages.PACKAGES)
                    .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                    .where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName))
                    .and(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
                    .fetch();
            var timestampedVersionsMap = new HashMap<Long, String>();
            if (versions.isNotEmpty()) {
                versions.forEach((r) -> {
                    if (r.component2() != null) {
                        var timestamp = r.component2().getTime();
                        var version = r.component1();
                        timestampedVersionsMap.put(timestamp, version);
                    }
                });
            }
            return timestampedVersionsMap;
        }

        @Override
        public Optional<String> produce() {
            var json = new JSONObject();
            var jsonArr = new JSONArray();
            if (resolvedDependencies != null) {
                for (var artifact : resolvedDependencies) {
                    var artifactJson = new JSONObject();
                    artifactJson.put("artifactId", artifact.getArtifactId());
                    artifactJson.put("groupId", artifact.getGroupId());
                    artifactJson.put("version", artifact.getVersion());
                    jsonArr.put(artifactJson);
                }
            }
            json.put("artifactId", artifact);
            json.put("groupId", group);
            json.put("version", version);
            json.put("timestamp", timestamp);
            json.put("resolvedDependencies", jsonArr);
            return Optional.of(json.toString());
        }

        @Override
        public String getOutputPath() {
            return File.separator + artifact.charAt(0) + File.separator
                    + artifact + File.separator + artifact + "_" + group + "_" + version + ".json";
        }

        @Override
        public String name() {
            return "Maven Resolver Plugin";
        }

        @Override
        public String description() {
            return "Maven Resolver Plugin. Given a Maven coordinate, "
                    + "it resolves the artifact to the complete dependency tree. "
                    + "Optionally, a timestamp can be provided - no dependency versions released "
                    + "later than timestamp will be included in the tree.";
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
        public Throwable getPluginError() {
            return this.pluginError;
        }

        @Override
        public void freeResource() {

        }
    }

}
