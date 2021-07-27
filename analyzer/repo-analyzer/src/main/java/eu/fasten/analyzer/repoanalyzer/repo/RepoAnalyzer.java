/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.analyzer.repoanalyzer.repo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.dom4j.DocumentException;
import org.json.JSONArray;
import org.json.JSONObject;

public abstract class RepoAnalyzer {

    public static final String DEFAULT_TESTS_PATH = "/src/test/java";
    public static final String DEFAULT_SOURCES_PATH = "/src/main/java";

    private final BuildManager buildManager;
    private final Path rootPath;

    /**
     * Constructs a Repo Analyzer given a path the root of a repository to analyze.
     *
     * @param path         path to the repository
     * @param buildManager build manager info
     */
    public RepoAnalyzer(final Path path, final BuildManager buildManager) {
        this.rootPath = path;
        this.buildManager = buildManager;
    }

    /**
     * Analyses tests in repository.
     *
     * @return JSON with statistics of the repository
     * @throws IOException if I/O exception occurs when accessing root file
     */
    public JSONObject analyze() throws IOException, DocumentException {
        var payload = new JSONObject();
        payload.put("repoPath", this.rootPath);
        payload.put("buildManager", this.buildManager);

        Map<TestCoverageType, Float> testCoverage = null;
        try {
            testCoverage = getTestCoverage(this.rootPath);
        } catch (Exception e) {
            System.err.println("Error getting the test coverage\n" + e);
        }
        payload.put("canExecuteTests", testCoverage != null);
        payload.put("testCoverage", testCoverage != null ? testCoverage : Collections.emptyMap());

        var moduleRoots = extractModuleRoots(this.rootPath);

        var results = new JSONArray();
        for (var module : moduleRoots) {
            var statistics = new JSONObject();
            statistics.put("path", module.toAbsolutePath().toString());

            var sourceFiles = getJavaFiles(getPathToSourcesRoot(module));
            statistics.put("sourceFiles", sourceFiles.size());

            var testFiles = getJavaFiles(getPathToTestsRoot(module));
            var testBodies = getJUnitTests(testFiles);
            testFiles = testBodies.keySet();
            statistics.put("testFiles", testFiles.size());

            var sourceFunctions = getNumberOfFunctions(sourceFiles);
            statistics.put("numberOfFunctions", sourceFunctions);

            var testToSourceRatio = roundTo3((double) testFiles.size() / (double) sourceFiles.size());
            statistics.put("testToSourceRatio", testToSourceRatio);

            var numberOfUnitTests = testBodies.values().stream()
                    .map(List::size)
                    .reduce(0, Integer::sum);
            statistics.put("numberOfUnitTests", numberOfUnitTests);

            var unitTestsToFunctionsRatio = roundTo3((double) numberOfUnitTests / (double) sourceFunctions);
            statistics.put("unitTestsToFunctionsRatio", unitTestsToFunctionsRatio);

            var mockImportFiles = getFilesWithMockImport(testFiles);
            statistics.put("filesWithMockImport", mockImportFiles.size());

            var testWithMocks = getTestsWithMock(testBodies, mockImportFiles);
            int numberOfUnitTestsWithMocks = testWithMocks.values().stream()
                    .map(List::size)
                    .reduce(0, Integer::sum);
            statistics.put("unitTestsWithMocks", numberOfUnitTestsWithMocks);

            var mockingRatio = roundTo3((double) numberOfUnitTestsWithMocks / (double) numberOfUnitTests);
            statistics.put("unitTestsMockingRatio", mockingRatio);


            if (sourceFiles.size() > 0) {
                results.put(statistics);
            }
        }
        payload.put("modules", results);

        return payload;
    }

    /**
     * Recursively get a list of all java files.
     *
     * @param directory root to start searching from
     * @return list of files
     */
    private Set<Path> getJavaFiles(final Path directory) {
        try {
            return Files.walk(directory)
                    .filter(f -> f.toFile().getName().endsWith(".java"))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            return new HashSet<>();
        }
    }

    /**
     * Integrates JaCoCO plugin, runs the test suite and returns the test coverage.
     *
     * @param root Path to project's root
     * @return Test coverage
     */
    protected abstract Map<TestCoverageType, Float> getTestCoverage(final Path root);

    /**
     * Get absolute path to the source files root. Extracts source file directory from pom.xml or
     * uses Maven default path.
     *
     * @param root root directory
     * @return root of the source files
     */
    protected abstract Path getPathToSourcesRoot(final Path root) throws IOException, DocumentException;

    /**
     * Get absolute path to the test files root. Extracts test file directory from pom.xml or
     * uses Maven default path.
     *
     * @param root root directory
     * @return root of the test files
     */
    protected abstract Path getPathToTestsRoot(final Path root) throws IOException, DocumentException;

    /**
     * Get a map of files as keys and a list of test bodies as value.
     *
     * @param testClasses paths to test classes
     * @return a map of files and test bodies
     * @throws IOException if I/O exception occurs when reading a file
     */
    private Map<Path, List<String>> getJUnitTests(final Set<Path> testClasses) throws IOException {
        var testBodies = new HashMap<Path, List<String>>();

        var pattern = Pattern.compile("@Test(\\(.*\\))?[^a-zA-Z0-9]");

        for (var testClass : testClasses) {
            var content = Files.readString(testClass);
            content = content.replaceAll("/\\*([\\S\\s]+?)\\*/", "");
            content = content.replaceAll("//.*", "");

            var matcher = pattern.matcher(content);

            var results = matcher.results().collect(Collectors.toList());
            for (int i = 0; i < results.size(); i++) {
                int start = results.get(i).start();
                int end = results.size() > i + 1 ? results.get(i + 1).start() : content.length();

                var method = content.substring(start, end);

                var currIndex = 0;
                var pseudoStack = 0;
                while (currIndex == 0 || pseudoStack != 0) {
                    var open = method.indexOf("{", currIndex);
                    var close = method.indexOf("}", currIndex);

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
                testBodies.get(testClass).add(method.substring(0, currIndex));
            }
        }

        return testBodies;
    }

    /**
     * Get number of functions in source files.
     *
     * @param sourceFiles paths to source files
     * @return number of source files
     * @throws IOException if I/O exception occurs when reading a file
     */
    private int getNumberOfFunctions(final Set<Path> sourceFiles) throws IOException {
        var pattern = Pattern.compile("(public|protected|private|static|\\s) +[\\w<>\\[\\]]+\\s+(\\w+) *\\([^)]*\\) *(\\{?|[^;])");
        var count = 0;

        for (var source : sourceFiles) {
            var content = Files.readString(source);
            content = content.replaceAll("/\\*([\\S\\s]+?)\\*/", "");
            content = content.replaceAll("//.*", "");

            var matcher = pattern.matcher(content);
            count += matcher.results().count();
        }
        return count;
    }

    /**
     * Get a list of files that have imported a mock framework.
     *
     * @param testClasses paths to test classes
     * @return a list of files with mock import
     * @throws IOException if I/O exception occurs when reading a file
     */
    private List<Path> getFilesWithMockImport(final Set<Path> testClasses) throws IOException {
        var files = new ArrayList<Path>();

        for (var testClass : testClasses) {
            var content = Files.readString(testClass);

            var header = content.split("class", 2)[0];
            var pattern = Pattern.compile("import[^;]*Mock.*;");
            if (pattern.matcher(header).find()) {
                files.add(testClass);
            }
        }
        return files;
    }

    /**
     * Get a map of files and respective test bodies that contain keywords of mocking frameworks.
     *
     * @param testBodies          all test bodies
     * @param filesWithMockImport files that have mock imports
     * @return map of files and test bodies with mocks
     */
    private Map<Path, List<String>> getTestsWithMock(final Map<Path, List<String>> testBodies,
                                                     final List<Path> filesWithMockImport) {
        var tests = new HashMap<Path, List<String>>();

        var patterns = new String[]{
                "\\.mock\\(", "\\.when\\(", "\\.spy\\(", "\\.doNothing\\(", // Mockito
                "replayAll\\(\\)", "verifyAll\\(\\)", "\\.createMock\\(", // EasyMock
                "@Mocked", "new Expectations\\(\\)"}; // JMockit
        var predicate = Arrays.stream(patterns)
                .map(p -> Pattern.compile(p).asPredicate())
                .reduce(x -> false, Predicate::or);

        for (var file : filesWithMockImport) {
            tests.put(file, testBodies.get(file).stream().filter(predicate).collect(Collectors.toList()));
        }

        return tests;
    }

    /**
     * Extract paths to all modules of the project.
     *
     * @param root root directory
     * @return a list of paths to modules
     */
    protected abstract Set<Path> extractModuleRoots(final Path root) throws IOException, DocumentException;

    /**
     * Rounds value with precision 3.
     *
     * @param value value to round
     * @return rounded value
     */
    private double roundTo3(double value) {
        double multiplier = Math.pow(10, 3);
        return (double) Math.round(multiplier * value) / multiplier;
    }
}
