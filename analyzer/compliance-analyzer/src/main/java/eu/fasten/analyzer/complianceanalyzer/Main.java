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

    @CommandLine.Option(names = {"-r", "--repo", "--repository"},
            paramLabel = "repo",
            description = "Path to JSON file containing repository information")
    String repoInfoFilePath;

    @CommandLine.Option(names = {"-n", "--ns", "--namespace"},
            paramLabel = "ns",
            description = "Kubernetes namespace to be used",
            defaultValue = "default")
    String namespace;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        var compliancePlugin = new ComplianceAnalyzerPlugin.CompliancePluginExtension(namespace);

        // Retrieving repository information
        var repoInfo = retrieveRepositoryInformation();
        assert repoInfo != null : "Couldn't find the repository info file";

        compliancePlugin.consume(repoInfo.toString());
        compliancePlugin.produce().ifPresent(System.out::println);
    }

    /**
     * Retrieves and returns a JSON file containing information about the repository that will be analyzed by QMSTR.
     *
     * @return a JSON file containing repository information.
     */
    private JSONObject retrieveRepositoryInformation() {

        // Retrieving repository information
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