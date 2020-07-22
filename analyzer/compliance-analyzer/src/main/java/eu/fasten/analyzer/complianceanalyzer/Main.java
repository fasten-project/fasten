package eu.fasten.analyzer.complianceanalyzer;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.sql.SQLException;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fasten.server.connectors.PostgresConnector;
import picocli.CommandLine;


@CommandLine.Command(name = "ComplianceAnalyzer")
public class Main implements Runnable{

    private static Logger logger = LoggerFactory.getLogger(Main.class);

    @CommandLine.Option(names = {"-f", "--file"},
        paramLabel = "JSON",
        description = "Path to JSON file which contains the repo info")
    String jsonFile;

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
    public static void main(String[] args) {
      final int exitCode = new CommandLine(new Main()).execute(args);
      System.exit(exitCode);
    }

    @Override
    public void run() {
      var compliancePlugin = new ComplianceAnalyzerPlugin.CompliancePluginExtension();
      try {
        compliancePlugin.setDBConnection(PostgresConnector.getDSLContext(dbUrl, dbUser));
      } catch (IllegalArgumentException | SQLException e) {
        logger.error("Could not connect to the database", e);
        return;
      }
      final FileReader reader;
      try {
        reader = new FileReader(jsonFile);
      } catch (FileNotFoundException e) {
        logger.error("Could not find the JSON file at " + jsonFile, e);
        return;
      }
      final JSONObject input = new JSONObject(new JSONTokener(reader));
      compliancePlugin.consume(input.toString());
      compliancePlugin.produce().ifPresent(System.out::println);
    }
}