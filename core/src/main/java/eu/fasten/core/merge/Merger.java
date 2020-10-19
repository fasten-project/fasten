package eu.fasten.core.merge;

import ch.qos.logback.classic.Level;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.dbconnectors.RocksDBConnector;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.List;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "MavenMerger", mixinStandardHelpOptions = true)
public class Merger implements Runnable {

    @CommandLine.Option(names = {"-a", "--artifact"},
            paramLabel = "ARTIFACT",
            description = "Maven coordinate of an artifact")
    String artifact;

    @CommandLine.Option(names = {"-d", "--dependencies"},
            paramLabel = "DEPS",
            description = "Maven coordinates of dependencies",
            split = ",")
    List<String> dependencies;

    @CommandLine.Option(names = {"-md", "--database"},
            paramLabel = "dbURL",
            description = "Metadata database URL for connection")
    String dbUrl;

    @CommandLine.Option(names = {"-du", "--user"},
            paramLabel = "dbUser",
            description = "Metadata database user name")
    String dbUser;

    @CommandLine.Option(names = {"-gd", "--graphdb_dir"},
            paramLabel = "dir",
            description = "Path to directory with RocksDB database")
    String graphDbDir;

    private static final Logger logger = LoggerFactory.getLogger(Merger.class);

    public static void main(String[] args) {
        System.exit(new CommandLine(new Merger()).execute(args));
    }

    @Override
    public void run() {
        if (artifact != null && dependencies != null && !dependencies.isEmpty()) {
            var root = (ch.qos.logback.classic.Logger) LoggerFactory
                    .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            root.setLevel(Level.INFO);

            DSLContext dbContext;
            RocksDao rocksDao;
            try {
                dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser);
                rocksDao = RocksDBConnector.createReadOnlyRocksDBAccessObject(graphDbDir);
            } catch (SQLException | IllegalArgumentException e) {
                logger.error("Could not connect to the metadata database: " + e.getMessage());
                return;
            } catch (RuntimeException e) {
                logger.error("Could not connect to the graph database: " + e.getMessage());
                return;
            }

            System.out.println("--------------------------------------------------");
            System.out.println("Artifact: " + artifact);
            System.out.println("--------------------------------------------------");
            final long startTime = System.currentTimeMillis();
            var databaseMerger = new DatabaseMerger(artifact, dependencies, dbContext, rocksDao);
            var mergedGraph = databaseMerger.mergeWithCHA();
            logger.info("Resolved {} calls in {} seconds", mergedGraph.nodes().size(),
                    new DecimalFormat("#0.000")
                            .format((System.currentTimeMillis() - startTime) / 1000d));
            System.out.println("==================================================");

            dbContext.close();
            rocksDao.close();
        }
    }
}
