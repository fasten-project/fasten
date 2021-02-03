package eu.fasten.analyzer.repoanalyzer.repo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.json.JSONObject;

public class RepoAnalyzer {

    private static final String DEFAULT_TESTS_PATH = "/src/test/java";
    private static final String DEFAULT_SOURCES_PATH = "/src/main/java";

    private static final double ESTIMATED_COVERAGE_THRESHOLD = 0.5;

    private final File repoPath;

    /**
     * Constructs a Repo Analyzer given a path the root of a repository to analyze.
     *
     * @param path path to the repository
     */
    public RepoAnalyzer(final String path) {
        this.repoPath = new File(path);
    }

    /**
     * Analyses tests in repository.
     *
     * @return JSON with statistics of the repository
     * @throws IOException if I/O exception occurs when accessing root file
     */
    public JSONObject analyze() throws IOException {
        var statistics = new JSONObject();

        var testFiles = getMatchingFiles(getPathToTestsRoot(), getTestsPatterns());
        statistics.put("testFiles", testFiles.size());

        var sourceFiles = getMatchingFiles(getPathToSourcesRoot(), List.of("^.*\\.java"));
        statistics.put("sourceFiles", sourceFiles.size());

        var estimatedCoverage = (double) Math.round(1000 * (double) testFiles.size() / (double) sourceFiles.size()) / 1000;
        statistics.put("estimatedCoverage", estimatedCoverage);

        if (estimatedCoverage < ESTIMATED_COVERAGE_THRESHOLD) {
            return statistics;
        }

        var testBodies = getJUnitTests(testFiles);
        int numberOfUnitTests = testBodies.values().stream()
                .map(List::size)
                .reduce(0, Integer::sum);
        statistics.put("unitTests", numberOfUnitTests);

        statistics.put("filesWithMockitoImport", 0);


        statistics.put("unitTestsWithMocks", 0);
        statistics.put("mockingRatio", 0);

        statistics.put("statementCoverage", 0);

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
    public List<Path> getMatchingFiles(final File directory, final List<String> patterns) throws IOException {
        var predicate = patterns.stream()
                .map(p -> Pattern.compile(p).asPredicate())
                .reduce(x -> false, Predicate::or);
        return Files.walk(directory.toPath())
                .filter(f -> predicate.test(f.getFileName().toString()))
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

    /**
     * Get a map of files as keys and a list of test bodies as value.
     *
     * @param testClasses paths to test classes
     * @return a map of files and test bodies
     * @throws IOException if I/O exception occurs when reading a file
     */
    public Map<Path, List<String>> getJUnitTests(final List<Path> testClasses) throws IOException {
        var testBodies = new HashMap<Path, List<String>>();
        for (var testClass : testClasses) {
            String content = Files.readString(testClass);

            var unitTests = content.split("@Test");

            var pseudoStack = 0;
            for (int i = 1; i < unitTests.length; i++) {
                var currIndex = 0;
                while (currIndex == 0 || pseudoStack != 0) {
                    var open = unitTests[i].indexOf("{", currIndex);
                    var close = unitTests[i].indexOf("}", currIndex);

                    open = open == -1 ? Integer.MAX_VALUE : open;
                    close = close == -1 ? Integer.MAX_VALUE : close;

                    if (open == Integer.MAX_VALUE && close == Integer.MAX_VALUE) {
                        break;
                    }

                    if (open < close) {
                        pseudoStack++;
                        currIndex = open + 1;
                    } else {
                        pseudoStack--;
                        currIndex = close + 1;
                    }
                }
                testBodies.putIfAbsent(testClass, new ArrayList<>());
                testBodies.get(testClass).add(unitTests[i].substring(0, currIndex));
            }
        }
        return testBodies;
    }
}
