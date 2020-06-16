package eu.fasten.analyzer.pomanalyzer;

import org.json.JSONObject;
import org.json.JSONTokener;
import picocli.CommandLine;
import java.io.FileNotFoundException;
import java.io.FileReader;

@CommandLine.Command(name = "POMAnalyzer")
public class Main implements Runnable {

    @CommandLine.Option(names = {"-f", "--file"},
            paramLabel = "JSON",
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

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        var pomAnalyzer = new POMAnalyzerPlugin.POMAnalyzer();
        if (artifact != null && group != null && version != null) {
            var mvnCoordinate = new JSONObject();
            mvnCoordinate.put("artifactId", artifact);
            mvnCoordinate.put("groupId", group);
            mvnCoordinate.put("version", version);
            var record = new JSONObject();
            record.put("payload", mvnCoordinate);
            pomAnalyzer.consume(record.toString());
            pomAnalyzer.produce().ifPresent(System.out::println);
        } else if (jsonFile != null) {
            FileReader reader;
            try {
                reader = new FileReader(jsonFile);
            } catch (FileNotFoundException e) {
                System.err.println("Could not find the JSON file at " + jsonFile);
                return;
            }
            var record = new JSONObject(new JSONTokener(reader));
            pomAnalyzer.consume(record.toString());
            pomAnalyzer.produce().ifPresent(System.out::println);
        } else {
            System.err.println("You need to specify Maven coordinate either by providing its "
                    + "artifactId ('-a'), groupId ('-g') and version ('-v') or by providing path "
                    + "to JSON file that contains that Maven coordinate as payload.");
        }
    }
}
