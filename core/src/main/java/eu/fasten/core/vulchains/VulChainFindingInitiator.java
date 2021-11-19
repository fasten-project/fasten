package eu.fasten.core.vulchains;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.callableindex.utils.CallableIndexChecker;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.CallGraphUtils;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import picocli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@CommandLine.Command(name = "VulChainFindingInitiator")
public class VulChainFindingInitiator implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(VulChainFindingInitiator.class);

    @CommandLine.Option(names = {"-d", "--database"},
        paramLabel = "DB_URL",
        description = "Database URL for connection",
        defaultValue = "jdbc:postgresql:fasten_java")
    String metadataDbUrl;

    @CommandLine.Option(names = {"-u", "--user"},
        paramLabel = "DB_USER",
        description = "Database user name",
        defaultValue = "fastenro")
    String metadataDbUser;

    @CommandLine.Option(names = {"-o", "--outputPath"},
        paramLabel = "OUTPUT",
        description = "The path to the directory to write the results")
    String outputDir;

    @CommandLine.Option(names = {"-dg", "--depGraphPath"},
        paramLabel = "DEPGRAPH_PATH",
        description = "The path to the dependency graph")
    String depGraphPath;

    public static void main(String[] args) {

        final int exitCode = new CommandLine(new VulChainFindingInitiator()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {

        checkIfArgumentsAreProvided(this.outputDir, this.metadataDbUrl, this.metadataDbUser);

            var metadataDb =
                CallableIndexChecker.connectToPostgres(this.metadataDbUrl, this.metadataDbUser);
            var graphResolver = buildResolver(metadataDb);

            var vulRevisions = queryVulRevisions(metadataDb);
            logger.info("Retrieved {} vulnerable packages, writing them to file.", vulRevisions.size());
            writeVulCoordsToFile(vulRevisions);

            final var dependentsMap = getDependents(graphResolver, vulRevisions);
            logger.info("Resolved dependents, writing them to file.");
            writeDependentsToFile(dependentsMap);

            for (final var vulDependents : dependentsMap.entrySet()) {
                for (Revision dependent : vulDependents.getValue()) {
                    final var ids =
                        resolveDepIds(metadataDb, graphResolver, vulDependents, dependent);
                }
            }

    }

    private Set<Long> resolveDepIds(DSLContext metadataDb,
                                    GraphMavenResolver dependencyGraphResolver,
                                    Map.Entry<Revision, ObjectLinkedOpenHashSet<Revision>> vulDependents,
                                    Revision dependent) {
        final var dependencySet =
            dependencyGraphResolver.resolveDependencies(dependent, metadataDb, true);
        dependencySet.add(dependent);
        dependencySet.add(vulDependents.getKey());
        addIDsToRevisionsWithoutID(metadataDb, dependencySet);
        return dependencySet.stream().map(r -> r.id).collect(Collectors.toSet());
    }

    private void addIDsToRevisionsWithoutID(
        DSLContext metadataDb, ObjectLinkedOpenHashSet<Revision> dependencySet) {
        for (Revision revision : dependencySet) {
            if (revision.id == 0) {
                final var id = metadataDb
                    .select(PackageVersions.PACKAGE_VERSIONS.ID)
                    .from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES)
                    .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                    .where(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(revision.version.toString()))
                    .and(Packages.PACKAGES.PACKAGE_NAME
                        .eq(revision.groupId + ":" + revision.artifactId))
                    .and(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
                    .fetchAny();
                if (id != null) {
                    revision.id = id.getValue(PackageVersions.PACKAGE_VERSIONS.ID, Long.class);
                }
            }
        }
    }

    private Map<Revision, ObjectLinkedOpenHashSet<Revision>> getDependents(
        final GraphMavenResolver resolver,
        final List<Revision> vulCoords) {
        Map<Revision, ObjectLinkedOpenHashSet<Revision>> dependentsMap = new HashMap<>();
        int counter = 0;
        for (final var revision : vulCoords) {
            ObjectLinkedOpenHashSet<Revision> dependents = null;
            try {
                dependents = resolver.resolveDependents(revision, true);
            } catch (RuntimeException e) {
                counter++;
            }
            if (dependents != null) {
                dependentsMap.put(revision, dependents);
            }
        }
        logger.warn("Failed to fetch dependents of {} vul packages, perhaps they are not in the DB",
            counter);
        return dependentsMap;
    }

    private List<Long> queryCallableVuls(final DSLContext metadataDb,
                                         final Revision revison) {
        logger.info("getting vulnerable callables of package version {}", revison.toString());

        var modules =
            metadataDb.select(Modules.MODULES.ID).from(Modules.MODULES)
                .where(Modules.MODULES.PACKAGE_VERSION_ID.eq(revison.id)).fetch()
                .intoSet(Modules.MODULES.ID);
        if (modules.size() > 0) {
            var modulesString = modules.stream()
                .map(Object::toString).collect(Collectors.joining(", "));

            String sql =
                "SELECT id FROM callables WHERE callables.module_id IN (" + modulesString + ")" +
                    " AND metadata -> 'vulnerabilities' IS NOT NULL";

            return metadataDb.fetch(sql).getValues(Callables.CALLABLES.ID, Long.class);
        }
        return Collections.emptyList();
    }


    private void writeDependentsToFile(
        Map<Revision, ObjectLinkedOpenHashSet<Revision>> dependentsMap) {

        List<String[]> content = new ArrayList<>();
        content.add(new String[] {"package", "dependents"});
        for (final var dependents : dependentsMap.entrySet()) {
            content.add(new String[] {dependents.getKey().toString(),
                dependents.getValue().stream().map(Revision::toString).collect(
                    Collectors.joining(";"))});
        }

        try {
            CallGraphUtils.writeToCSV(content, this.outputDir + "/vulnerableDependents.csv");
        } catch (IOException e) {
            throw new RuntimeException("Failed to write dependents map to file", e);
        }
    }

    private GraphMavenResolver buildResolver(DSLContext metadataDb) {
        var resolver = new GraphMavenResolver();
        try {
            resolver.buildDependencyGraph(metadataDb, this.depGraphPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return resolver;
    }

    private void writeVulCoordsToFile(List<Revision> packageVersionCoords) {

        try {
            final var coords = String.join("\n",
                packageVersionCoords.stream().map(Revision::toString).collect(Collectors.toSet()));
            CallGraphUtils
                .writeToFile(this.outputDir + "/vulnerablePackageVersionCoords.txt", coords);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write vulnerable coords to file", e);
        }
    }

    private void checkIfArgumentsAreProvided(String... arguments) {
        for (String argument : arguments) {
            if (argument == null) {
                throw new RuntimeException("Please provide all arguments");
            }
        }

    }

    private List<Revision> queryVulRevisions(DSLContext metadataDb) {
        logger.info("getting vulnerable package version ids ...");

        String sql =
            "select CONCAT(packages.package_name,':', package_versions.version), package_versions.created_at, package_versions.id from package_versions JOIN packages on (packages.id = package_versions.package_id)\n" +
                "WHERE package_versions.metadata -> 'vulnerabilities' IS NOT NULL";

        List<Revision> vulRevisions = new ArrayList<>();
        metadataDb.fetch(sql).forEach(record -> {
            final var coord = (String) record.get(0);
            if (coord != null) {
                final var gav = coord.split(":");
                final var timestamp = (Timestamp) record.get(1);
                final var id = (Long) record.get(2);
                Revision currentRevision;
                if (id != null) {
                    currentRevision = new Revision(id, gav[0], gav[1], gav[2], timestamp);
                } else {
                    currentRevision = new Revision(gav[0], gav[1], gav[2], timestamp);
                }
                vulRevisions.add(currentRevision);
            }
        });
        return vulRevisions;
    }

}


