package eu.fasten.analyzer.mavenresolver;

import eu.fasten.server.connectors.PostgresConnector;
import org.json.JSONObject;
import org.json.JSONTokener;
import picocli.CommandLine;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.sql.SQLException;

@CommandLine.Command(name = "MavenResolver")
public class Main implements Runnable {

    @CommandLine.Option(names = {"-f", "--file"},
            paramLabel = "JSON_FILE",
            description = "Path to JSON file which contains the Maven coordinate")
    String jsonFile;

    @CommandLine.Option(names = {"-a", "--artifactId"},
            paramLabel = "ARTIFACT",
            description = "artifactId of the Maven coordinate")
    String artifact;

    @CommandLine.Option(names = {"-g", "--groupId"},
            paramLabel = "GROUP",
            description = "groupId of the Maven coordinate")
    String group;

    @CommandLine.Option(names = {"-v", "--version"},
            paramLabel = "VERSION",
            description = "version of the Maven coordinate")
    String version;

    @CommandLine.Option(names = {"-t", "--timestamp"},
            paramLabel = "TS",
            description = "Timestamp for resolution")
    Long timestamp;

    @CommandLine.Option(names = {"-d", "--database"},
            paramLabel = "DB_URL",
            description = "Database URL for connection",
            defaultValue = "jdbc:postgresql:postgres")
    String dbUrl;

    @CommandLine.Option(names = {"-u", "--user"},
            paramLabel = "DB_USER",
            description = "Database user name",
            defaultValue = "postgres")
    String dbUser;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        var mavenResolver = new MavenResolverPlugin.MavenResolver();
        try {
            mavenResolver.setDBConnection(PostgresConnector.getDSLContext(dbUrl, dbUser));
        } catch (SQLException e) {
            System.err.println("Error connecting to the database:");
            e.printStackTrace(System.err);
            return;
        }
        if (artifact != null && group != null && version != null) {
            var mvnCoordinate = new JSONObject();
            mvnCoordinate.put("artifactId", artifact);
            mvnCoordinate.put("groupId", group);
            mvnCoordinate.put("version", version);
            mvnCoordinate.put("timestamp", timestamp);
            var record = new JSONObject();
            record.put("payload", mvnCoordinate);
            mavenResolver.consume(record.toString());
            mavenResolver.produce().ifPresent(System.out::println);
        } else if (jsonFile != null) {
            FileReader reader;
            try {
                reader = new FileReader(jsonFile);
            } catch (FileNotFoundException e) {
                System.err.println("Could not find the JSON file at " + jsonFile);
                return;
            }
            var record = new JSONObject(new JSONTokener(reader));
            mavenResolver.consume(record.toString());
            mavenResolver.produce().ifPresent(System.out::println);
        } else {
            System.err.println("You need to specify Maven coordinate either by providing its "
                    + "artifactId ('-a'), groupId ('-g') and version ('-v') or by providing path "
                    + "to JSON file that contains that Maven coordinate as payload.");
        }
    }
}
