package eu.fasten.core.maven;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.codegen.tables.Dependencies;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.maven.data.Dependency;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.DSLContext;
import org.json.JSONObject;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DependencyGraphBuilder {

    public Map<Dependency, List<Pair<Dependency, Timestamp>>> buildMavenDependencyGraph(DSLContext dbContext) {

        // Get all artifact and their dependencies from database
        var dependenciesResult = dbContext
                .select(Packages.PACKAGES.PACKAGE_NAME,
                        PackageVersions.PACKAGE_VERSIONS.VERSION,
                        Dependencies.DEPENDENCIES.METADATA)
                .from(Packages.PACKAGES)
                .join(PackageVersions.PACKAGE_VERSIONS)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .join(Dependencies.DEPENDENCIES)
                .on(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID.eq(PackageVersions.PACKAGE_VERSIONS.ID))
                .where(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
                .fetch();
        if (dependenciesResult == null || dependenciesResult.isEmpty()) {
            return new HashMap<>();
        }

        // Get timestamps from the database
        var timestampsResult = dbContext
                .select(
                        Packages.PACKAGES.PACKAGE_NAME,
                        PackageVersions.PACKAGE_VERSIONS.VERSION,
                        PackageVersions.PACKAGE_VERSIONS.CREATED_AT)
                .from(Packages.PACKAGES)
                .join(PackageVersions.PACKAGE_VERSIONS)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .where(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
                .fetch();
        var timestampedArtifacts = new HashMap<Dependency, Timestamp>();
        for (var record : timestampsResult) {
            var mavenCoordinate = record.component1() + Constants.mvnCoordinateSeparator + record.component2();
            var timestamp = record.component3();
            if (timestamp == null) {
                timestamp = new Timestamp(-1);
            }
            timestampedArtifacts.put(new Dependency(mavenCoordinate), timestamp);
        }

        // Build dependency graph
        var graph = new HashMap<Dependency, List<Pair<Dependency, Timestamp>>>(dependenciesResult.size());
        for (var record : dependenciesResult) {
            var mavenCoordinate = record.component1() + Constants.mvnCoordinateSeparator + record.component2();
            var artifact = new Dependency(mavenCoordinate);
            var dependency = Dependency.fromJSON(new JSONObject(record.component3().data()));
            var timestamp = timestampedArtifacts.get(new Dependency(
                    dependency.groupId, dependency.artifactId, dependency.getVersion()
            ));
            var edge = new ImmutablePair<>(dependency, timestamp);
            var edges = graph.get(artifact);
            if (edges == null) {
                graph.put(artifact, List.of(edge));
            } else {
                edges.add(edge);
                graph.put(artifact, edges);
            }
        }
        return graph;
    }
}