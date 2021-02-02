package eu.fasten.analyzer.repoanalyzer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.json.JSONObject;

public class RepoAnalyzer {

    private final String repoPath;

    public RepoAnalyzer(final String path) {
        this.repoPath = path;
    }

    public JSONObject analyze() throws IOException {
        var statistics = new JSONObject();

        var numberOfTestFile = countNumberOfMatchingFiles(new File(repoPath + "/src/test/java"),
                getTestsPatterns());
        statistics.put("testFile", numberOfTestFile);
        return statistics;
    }

    public int countNumberOfMatchingFiles(final File directory, final List<String> patterns) throws IOException {
        var predicate = patterns.stream()
                .map(p -> Pattern.compile(p).asPredicate())
                .reduce(x -> false, Predicate::or);
        return (int) Files.walk(directory.toPath())
                .map(p -> p.getFileName().toString())
                .filter(predicate)
                .count();
    }

    // TODO: take into account custom regex configurations of maven surefire plugin
    // https://maven.apache.org/surefire/maven-surefire-plugin/examples/inclusion-exclusion.html
    public List<String> getTestsPatterns() {
        var patterns = new ArrayList<String>();

        patterns.add("^.*Test\\.java");
        patterns.add("^Test.*\\.java");
        patterns.add("^.*Tests\\.java");
        patterns.add("^.*TestCase\\.java");

        return patterns;
    }
}
