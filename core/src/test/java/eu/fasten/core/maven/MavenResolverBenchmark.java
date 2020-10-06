package eu.fasten.core.maven;

import eu.fasten.core.data.Constants;
import eu.fasten.core.dbconnectors.PostgresConnector;
import org.jooq.DSLContext;
import picocli.CommandLine;
import java.io.File;
import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Scanner;

/**
 * This class is a benchmark for MavenResolver to compare database and online resolution
 */
@CommandLine.Command(name = "MavenResolverBenchmark")
public class MavenResolverBenchmark implements Runnable {

    @CommandLine.Option(names = {"-f", "--file"},
            paramLabel = "COORDS_FILE",
            description = "Path to file with coordinates")
    String file;

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

    @CommandLine.Option(names = {"-s", "--skip"},
            description = "Skip first line of the file")
    boolean skipFirstLine;

    /**
     * NB! Before running main() make sure to run POM Analyzer on the same coordinates as benchmark
     */
    public static void main(String[] args) {
        final int exitCode = new CommandLine(new MavenResolverBenchmark()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        Scanner input;
        try {
            input = new Scanner(new File(file));
        } catch (FileNotFoundException e) {
            System.err.println(e.getMessage());
            return;
        }
        if (skipFirstLine && input.hasNextLine()) {
            input.nextLine();
        }
        DSLContext dbContext;
        try {
            dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser);
        } catch (SQLException e) {
            System.err.println("Could not connect to the database: " + e.getMessage());
            return;
        }
        var mavenResolver = new MavenResolver();
        var artifactCount = 0;
        var dbCount = 0;
        var onlineCount = 0;
        var result = 0F;
        var dbResolutionSuccess = 0;
        var onlineResolutionSuccess = 0;
        while (input.hasNextLine()) {
            var line = input.nextLine();
            var coordinate = line.split(Constants.mvnCoordinateSeparator);
            var groupId = coordinate[0];
            var artifactId = coordinate[1];
            var version = coordinate[2];
            try {
                dbCount++;
                var dbDependencySet = mavenResolver.resolveFullDependencySet(groupId, artifactId, version, dbContext);
                dbResolutionSuccess++;
                onlineCount++;
                var onlineDependencySet = mavenResolver.resolveFullDependencySetOnline(artifactId, groupId, version);
                onlineResolutionSuccess++;
                var setSize = Math.max(dbDependencySet.size(), onlineDependencySet.size());
                var intersection = new HashSet<>(dbDependencySet);
                intersection.retainAll(onlineDependencySet);
                var matching = intersection.size() > 0 ? (float) intersection.size() / (float) setSize : 0F;
                if (dbDependencySet.isEmpty() && onlineDependencySet.isEmpty()) {
                    matching = 1F;
                }
                result += matching;
                artifactCount++;
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
        var finalResult = result / (float) artifactCount;
        System.out.println("--------------------------------------------------");
        System.out.println("Successful match rate is " + finalResult + " for " + artifactCount + " artifacts");
        System.out.println("Database resolution success rate: " + (float) dbResolutionSuccess / (float) dbCount);
        System.out.println("Online resolution success rate: " + (float) onlineResolutionSuccess / (float) onlineCount);
        System.out.println("--------------------------------------------------");
    }
}
