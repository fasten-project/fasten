package eu.fasten.core.utils;

import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.graphdb.ExtendedGidGraph;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.*;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallablesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.ModulesRecord;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.merge.LocalMerger;
import org.apache.commons.math3.util.Pair;
import org.apache.kafka.common.protocol.types.Field;
import org.jooq.DSLContext;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;

@CommandLine.Command(name = "GraphDataTransformer")
public class GraphDataTransformer implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(GraphDataTransformer.class);

    @CommandLine.Option(names = {"-d", "--database"},
            paramLabel = "DB_URL",
            description = "Database URL for connection",
            defaultValue = "jdbc:postgresql:fasten_java")
    String dbUrl;

    @CommandLine.Option(names = {"-u", "--user"},
            paramLabel = "DB_USER",
            description = "Database user name",
            defaultValue = "postgres")
    String dbUser;

    @CommandLine.Option(names = {"-gd", "--graph-db"},
            paramLabel = "Dir",
            description = "The directory of the RocksDB instance")
    String graphDbPath;

    @CommandLine.Option(names = {"-ngd", "--new-graph-db"},
            paramLabel = "Dir",
            description = "The directory of the new RocksDB instance")
    String oldGraphDbPath;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new GraphDataTransformer()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        RocksDao oldGraphDb;
        RocksDao newGraphDb;
        DSLContext metadataDb;
        try {
            oldGraphDb = new RocksDao(graphDbPath, true);
            newGraphDb = new RocksDao(oldGraphDbPath, false);
            metadataDb = PostgresConnector.getDSLContext(dbUrl, dbUser, true);
        } catch (RocksDBException | SQLException e) {
            logger.error("Could not setup connections to the database:", e);
            return;
        }
        var packageVersionIds = new HashSet<Long>();
        metadataDb.select(PackageVersions.PACKAGE_VERSIONS.ID).from(PackageVersions.PACKAGE_VERSIONS).fetch().forEach(r -> packageVersionIds.add(r.value1()));
        var packageVersions = new HashMap<Long, Pair<String, String>>();
        metadataDb.select(PackageVersions.PACKAGE_VERSIONS.ID, Packages.PACKAGES.PACKAGE_NAME, PackageVersions.PACKAGE_VERSIONS.VERSION)
                .from(Packages.PACKAGES).join(PackageVersions.PACKAGE_VERSIONS)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .where(PackageVersions.PACKAGE_VERSIONS.ID.in(packageVersionIds)).fetch()
                .forEach(r -> packageVersions.put(r.value1(), new Pair<>(r.value2(), r.value3())));
        for (var packageVersionId : packageVersionIds) {
            DirectedGraph oldGraphData;
            try {
                oldGraphData = oldGraphDb.getGraphData(packageVersionId);
            } catch (RocksDBException e) {
                logger.warn("Could not retrieve package version with id {}", packageVersionId);
                continue;
            }
            if (oldGraphData == null) {
                logger.warn("Directed graph with id {} is not found", packageVersionId);
                continue;
            }
            var callables = new HashSet<>(metadataDb.selectFrom(Callables.CALLABLES).where(Callables.CALLABLES.ID.in(oldGraphData.nodes())).fetch());
            var moduleIds = callables.stream().map(CallablesRecord::getModuleId).collect(Collectors.toList());
            var edges = new HashSet<>(metadataDb.selectFrom(CallSites.CALL_SITES).where(CallSites.CALL_SITES.SOURCE_ID.in(oldGraphData.nodes())).fetch());
            edges.addAll(metadataDb.selectFrom(CallSites.CALL_SITES).where(CallSites.CALL_SITES.TARGET_ID.in(oldGraphData.nodes())).fetch());
            var namespaceIds = new HashSet<Long>();
            metadataDb.select(Modules.MODULES.MODULE_NAME_ID).from(Modules.MODULES).where(Modules.MODULES.ID.in(moduleIds)).fetch().forEach(r -> namespaceIds.add(r.value1()));
            edges.forEach(e -> namespaceIds.addAll(Arrays.asList(e.getReceiverTypeIds())));
            var typesMap = new HashMap<Long, String>();
            metadataDb.select(ModuleNames.MODULE_NAMES.ID, ModuleNames.MODULE_NAMES.NAME).from(ModuleNames.MODULE_NAMES).where(ModuleNames.MODULE_NAMES.ID.in(namespaceIds)).fetch().forEach(r -> typesMap.put(r.value1(), r.value2()));
            var gidToUriMap = new HashMap<Long, String>();
            callables.forEach(c -> gidToUriMap.put(c.getId(), c.getFastenUri()));
            var extendedGidGraph = new ExtendedGidGraph(packageVersionId,
                    packageVersions.get(packageVersionId).getFirst(),
                    packageVersions.get(packageVersionId).getSecond(),
                    new ArrayList<>(oldGraphData.nodes()),
                    oldGraphData.nodes().size() - oldGraphData.externalNodes().size(),
                    new ArrayList<>(edges),
                    gidToUriMap,
                    typesMap);
            try {
                newGraphDb.saveToRocksDb(extendedGidGraph);
            } catch (IOException | RocksDBException e) {
                logger.error("Could not save the extended GID graph with ID {}", packageVersionId);
                continue;
            }
            logger.info("Successfully saved the new graph data with ID {}", packageVersionId);
        }
    }
}
