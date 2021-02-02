package eu.fasten.analyzer.repoanalyzer.repo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepoAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(RepoAnalyzer.class);

    private static final String DEFAULT_TESTS_PATH = "/src/test/java";
    private static final String DEFAULT_SOURCES_PATH = "/src/main/java";

    private final File repoPath;

    public RepoAnalyzer(final String path) {
        this.repoPath = new File(path);
    }

    public JSONObject analyze() throws IOException {
        var statistics = new JSONObject();

        var testFiles = getMatchingFiles(getPathToTestsRoot(), getTestsPatterns());
        statistics.put("testFiles", testFiles.size());

        var sourceFiles = getMatchingFiles(getPathToSourcesRoot(), List.of("^.*\\.java"));
        statistics.put("sourceFiles", sourceFiles.size());

        var ration = (float) testFiles.size() / (float) sourceFiles.size();
        statistics.put("estimatedCoverage", (float) Math.round(1000 * ration) / 1000);

        return statistics;
    }

    /**
     * Recursively get a list of files that have a name that matches one of the regular expressions.
     *
     * @param directory root to start searching from
     * @param patterns  list of regular expressions
     * @return list of files
     * @throws IOException if I/O exception occurs when accessing root file
     */
    public List<File> getMatchingFiles(final File directory, final List<String> patterns) throws IOException {
        var predicate = patterns.stream()
                .map(p -> Pattern.compile(p).asPredicate())
                .reduce(x -> false, Predicate::or);
        return Files.walk(directory.toPath())
                .map(p -> p.getFileName().toString())
                .filter(predicate)
                .map(File::new)
                .collect(Collectors.toList());
    }

    /**
     * Get absolute path to the source files root. Extracts source file directory from pom.xml or
     * uses Maven default path.
     *
     * @return root of the source files
     */
    public File getPathToSourcesRoot() {
        // TODO: take into account custom source dir in pom.xml
        return new File(repoPath.getAbsolutePath() + DEFAULT_SOURCES_PATH);
    }

    /**
     * Get absolute path to the test files root. Extracts test file directory from pom.xml or
     * uses Maven default path.
     *
     * @return root of the test files
     */
    public File getPathToTestsRoot() {
        // TODO: take into account custom test dir in pom.xml
        return new File(repoPath.getAbsolutePath() + DEFAULT_TESTS_PATH);
    }

    /**
     * Get a list of default Maven regular expressions that match test files names.
     * Extracts additional regular expressions from pom.xml.
     *
     * @return list of regular expressions
     */
    public List<String> getTestsPatterns() {
        // TODO: take into account custom regex configurations of maven surefire plugin
        // https://maven.apache.org/surefire/maven-surefire-plugin/examples/inclusion-exclusion.html

        var patterns = new ArrayList<String>();

        patterns.add("^.*Test\\.java");
        patterns.add("^Test.*\\.java");
        patterns.add("^.*Tests\\.java");
        patterns.add("^.*TestCase\\.java");

        return patterns;
    }
}
