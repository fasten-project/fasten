package eu.fasten.analyzer.restapiplugin;

import eu.fasten.server.db.PostgresConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.sql.SQLException;

@CommandLine.Command(name = "RestAPIPlugin")
public class Main implements Runnable{

    private static Logger logger = LoggerFactory.getLogger(Main.class);

    @CommandLine.Option(names = {"-d", "--database"},
            paramLabel = "dbURL",
            description = "Database URL for connection",
            defaultValue = "jdbc:postgresql:postgres")
    String dbUrl;

    @CommandLine.Option(names = {"-u", "--user"},
            paramLabel = "dbUser",
            description = "Database user name",
            defaultValue = "postgres")
    String dbUser;

    @CommandLine.Option(names = {"-p", "--pass"},
            paramLabel = "dbPass",
            description = "Database user password",
            defaultValue = "pass123")
    String dbPass;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            var restAPIPlugin = new RestAPIPlugin.RestAPIExtension();
            restAPIPlugin.setDBConnection(PostgresConnector.getDSLContext(dbUrl, dbUser, dbPass));

            restAPIPlugin.start();

        } catch (SQLException e) {
            logger.error("Could not connect to the database", e);
        }
    }
}
