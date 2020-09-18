package eu.fasten.core.maven;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.codegen.tables.Dependencies;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.MavenCoordinate;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jooq.DSLContext;
import org.json.JSONObject;
import picocli.CommandLine;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@CommandLine.Command(name = "MavenResolver")
public class MavenResolver implements Runnable {

    @CommandLine.Option(names = {"-a", "--artifactId"},
            paramLabel = "ARTIFACT",
            description = "artifactId of the Maven coordinate")
    protected String artifact;

    @CommandLine.Option(names = {"-g", "--groupId"},
            paramLabel = "GROUP",
            description = "groupId of the Maven coordinate")
    protected String group;

    @CommandLine.Option(names = {"-v", "--version"},
            paramLabel = "VERSION",
            description = "version of the Maven coordinate")
    protected String version;

    @CommandLine.Option(names = {"-t", "--timestamp"},
            paramLabel = "TS",
            description = "Timestamp for resolution",
            defaultValue = "-1")
    protected long timestamp;

    @CommandLine.Option(names = {"-d", "--database"},
            paramLabel = "DB_URL",
            description = "Database URL for connection",
            defaultValue = "jdbc:postgresql:postgres")
    protected String dbUrl;

    @CommandLine.Option(names = {"-u", "--user"},
            paramLabel = "DB_USER",
            description = "Database user name",
            defaultValue = "postgres")
    protected String dbUser;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new MavenResolver()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        var mavenResolver = new MavenResolver();
        if (artifact != null && group != null && version != null) {
            DSLContext dbContext;
            try {
                dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser);
            } catch (SQLException e) {
                System.err.println("Could not connect to the database: " + e.getMessage());
                return;
            }
            var mavenCoordinate = group + Constants.mvnCoordinateSeparator
                    + artifact + Constants.mvnCoordinateSeparator + version;
            var dependencySet = mavenResolver.resolveDependencies(group, artifact, version, timestamp, dbContext);
            System.out.println("--------------------------------------------------");
            System.out.println("Maven coordinate: " + mavenCoordinate);
            System.out.println("--------------------------------------------------");
            System.out.println("Full dependency set:");
            for (var dependency : dependencySet) {
                System.out.println(dependency.toCanonicalForm());
            }
        } else {
            System.err.println("You need to specify Maven coordinate by providing its "
                    + "artifactId ('-a'), groupId ('-g') and version ('-v'). "
                    + "Optional timestamp (-t) can also be provided.");
        }
    }

    /**
     * Resolves full dependency set of certain Maven artifact.
     *
     * @param coordinate Maven coordinate in the form of "groupId:artifactId:version"
     * @param timestamp  Optional timestamp. Use -1 in order not to provide the timestamp.
     *                   If provided then any dependency version with release timestamp
     *                   later than the provided timestamp will not be included
     *                   in the dependency set (they will downgraded to the suitable version).
     * @param dbContext  Database connection context
     * @return Full dependency set (including transitive dependencies) of the maven coordinate
     */
    public Set<MavenCoordinate> resolveArtifactDependencies(String coordinate,
                                                            long timestamp,
                                                            DSLContext dbContext) {
        var mavenCoordinate = new MavenCoordinate(coordinate);
        var artifacts = Arrays.stream(
                Maven.resolver()
                        .resolve(mavenCoordinate.toCanonicalForm())
                        .withTransitivity()
                        .asResolvedArtifact()
        ).collect(Collectors.toList());
        if (artifacts.size() < 1) {
            throw new RuntimeException("Could not resolve artifact "
                    + mavenCoordinate.toCanonicalForm());
        }
        var dependencies = Arrays.stream(
                artifacts.get(0).getDependencies()
        ).map(d -> new MavenCoordinate(
                        d.getCoordinate().getGroupId(),
                        d.getCoordinate().getArtifactId(),
                        d.getResolvedVersion()
                )
        ).collect(Collectors.toSet());
        var fullDependencySet = new HashSet<>(dependencies);
        for (var dependency : dependencies) {
            fullDependencySet.addAll(this.resolveArtifactDependencies(
                    dependency.toCanonicalForm(), timestamp, dbContext
            ));
        }
        if (timestamp != -1) {
            return filterByTimestamp(fullDependencySet, timestamp, dbContext);
        } else {
            return fullDependencySet;
        }
    }

    public Set<Dependency> resolveDependencies(String groupId, String artifactId, String version,
                                               long timestamp, DSLContext dbContext) {
        var directDependencies = getArtifactDependencies(groupId, artifactId, version, dbContext);
        var fullDependencySet = new HashSet<>(directDependencies);
        for (var dependency : directDependencies) {
            var depVersion = String.join(",", dependency.getVersionConstraints());
            fullDependencySet.addAll(getArtifactDependencies(dependency.groupId, dependency.artifactId, depVersion, dbContext));
        }
        if (timestamp == -1) {
            return fullDependencySet;
        } else {
            return filterDependenciesByTimestamp(fullDependencySet, new Timestamp(timestamp), dbContext);
        }
    }

    public List<Dependency> getArtifactDependencies(String groupId, String artifactId,
                                                    String version, DSLContext context) {
        var packageName = groupId + Constants.mvnCoordinateSeparator + artifactId;
        var result = context.select(Dependencies.DEPENDENCIES.METADATA)
                .from(Dependencies.DEPENDENCIES)
                .join(PackageVersions.PACKAGE_VERSIONS)
                .on(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID
                        .eq(PackageVersions.PACKAGE_VERSIONS.ID))
                .join(Packages.PACKAGES)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName))
                .and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version))
                .fetch();
        return result.map(r -> Dependency.fromJSON(new JSONObject(r.component1().data())));
    }

    private Set<Dependency> filterDependenciesByTimestamp(Set<Dependency> dependencies,
                                                          Timestamp timestamp, DSLContext context) {
        var filteredDependencies = new HashSet<Dependency>(dependencies.size());
        for (var dependency : dependencies) {
            var packageName = dependency.groupId + Constants.mvnCoordinateSeparator + dependency.artifactId;
            var result = context.select(PackageVersions.PACKAGE_VERSIONS.VERSION)
                    .from(PackageVersions.PACKAGE_VERSIONS)
                    .join(Packages.PACKAGES)
                    .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                    .where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName))
                    .and(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
                    .and(PackageVersions.PACKAGE_VERSIONS.CREATED_AT.lessOrEqual(timestamp))
                    .orderBy(PackageVersions.PACKAGE_VERSIONS.CREATED_AT.desc())
                    .limit(1)
                    .fetchOne();
            String suitableVersion = null;
            if (result != null) {
                suitableVersion = result.value1();
            }
            if (suitableVersion == null) {
                filteredDependencies.add(dependency);
            } else {
                filteredDependencies.add(
                        new Dependency(dependency.groupId, dependency.artifactId, suitableVersion)
                );
            }
        }
        return filteredDependencies;
    }

    private Set<MavenCoordinate> filterByTimestamp(Set<MavenCoordinate> artifacts,
                                                   long timestamp, DSLContext dbContext) {
        var filteredArtifacts = new HashSet<MavenCoordinate>();
        for (var artifact : artifacts) {
            var filtered = false;
            var packageName = artifact.getGroupId() + Constants.mvnCoordinateSeparator
                    + artifact.getArtifactId();
            var timestampedVersions = getTimestampedVersionsFromDB(packageName, dbContext);
            if (timestampedVersions.isEmpty()) {
                filteredArtifacts.add(artifact);
                continue;
            }
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

    private Map<Long, String> getTimestampedVersionsFromDB(String packageName,
                                                           DSLContext dbContext) {
        var versions = dbContext.select(
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
}
