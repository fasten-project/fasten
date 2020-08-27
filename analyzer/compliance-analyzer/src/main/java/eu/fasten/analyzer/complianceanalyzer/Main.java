package eu.fasten.analyzer.complianceanalyzer;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.FileNotFoundException;
import java.io.FileReader;


@CommandLine.Command(name = "ComplianceAnalyzer")
public class Main implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @CommandLine.Option(names = {"-c", "--credentials", "--cluster-credentials"},
            paramLabel = "credentials",
            description = "Path to the Kubernetes cluster credentials file")
    String clusterCredentialsFilePath;

    // TODO This plugin would consume a Kafka Topic to retrieve repository information
    /* @CommandLine.Option(names = {"-d", "--database"},
        paramLabel = "dbURL",
        description = "Database URL for connection",
        defaultValue = "jdbc:postgresql:postgres")
    String dbUrl;

    @CommandLine.Option(names = {"-u", "--user"},
        paramLabel = "dbUser",
        description = "Database user name",
        defaultValue = "postgres")
    String dbUser; */

    // FIXME To be deleted upon releasing the Kafka Topic containing repository information
    @CommandLine.Option(names = {"-r", "--repo", "--repository"},
            paramLabel = "repo",
            description = "Path to JSON file containing repository information")
    String repoInfoFilePath;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        var compliancePlugin = new ComplianceAnalyzerPlugin.CompliancePluginExtension(clusterCredentialsFilePath);

        // Retrieving repository information
        var repoInfo = retrieveRepositoryInformation();
        // FIXME To be changed when the repo info Kafka topic will become available
        assert repoInfo != null : "Couldn't find the repository info file";

        compliancePlugin.consume(repoInfo.toString());
        compliancePlugin.produce().ifPresent(System.out::println);
    }

    /**
     * Retrieves and returns a JSON file containing information about the repository that will be analyzed by QMSTR.
     * <p>
     * TODO This information would be retrieved as a Kafka Topic.
     * Given its current absence, such retrieval will be simulated by reading a file.
     * <p>
     * // @param dbUrl          the DB URL from which the repository information will be retrieved.
     * // @param dbUser         username used to access the DB containing repository information.
     *
     * @return a JSON file containing repository information.
     */
    private JSONObject retrieveRepositoryInformation(/*String dbUrl, String dbUser*/) {

        // TODO Consuming a Kafka Topic
        /* try {
            compliancePlugin.setDBConnection(PostgresConnector.getDSLContext(dbUrl, dbUser));
            // ...
        } catch (IllegalArgumentException | SQLException e) {
            logger.error("Could not connect to the database", e);
            return;
        } */

        // Simulating the Kafka Topic retrieval
        // FIXME As soon as Kafka Topics become available
        final FileReader reader;
        try {
            reader = new FileReader(repoInfoFilePath);
        } catch (FileNotFoundException e) {
            logger.error("Couldn't find the repository info file at: " + repoInfoFilePath, e);
            return null;
        }

        // Returning repository information
        return new JSONObject(new JSONTokener(reader));
    }
}