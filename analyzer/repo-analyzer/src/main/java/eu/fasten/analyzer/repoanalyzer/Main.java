package eu.fasten.analyzer.repoanalyzer;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "RepoAnalyzer", mixinStandardHelpOptions = true)
public class Main implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    @CommandLine.Option(names = {"-p", "--path"},
            paramLabel = "PATH",
            description = "Path to the repository",
            defaultValue = "")
    String repoPath;

    public static void main(String[] args) {
        new CommandLine(new Main()).execute(args);
    }

    public void run() {
        var analyzer = new RepoAnalyzerPlugin.RepoAnalyzerExtension();
        var json =  new JSONObject();
        json.put("repoPath", repoPath);
        analyzer.consume(json.toString());
        System.out.println(analyzer.produce().get());
    }
}
