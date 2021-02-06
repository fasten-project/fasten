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

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.nodeTypes.NodeWithMembers;
import com.github.javaparser.ast.stmt.BlockStmt;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.NotImplementedException;
import org.json.JSONArray;
import org.json.JSONObject;

public abstract class RepoAnalyzer {

    /**
     * Build managers that repo analyzer supports.
     */
    enum BuildManager {
        maven,
        gradle,
        gradleKotlin
    }

    public static final String DEFAULT_TESTS_PATH = "/src/test/java";
    public static final String DEFAULT_SOURCES_PATH = "/src/main/java";

    private final BuildManager buildManager;
    private final String rootPath;

    /**
     * Constructs a Repo Analyzer given a path the root of a repository to analyze.
     *
     * @param path         path to the repository
     * @param buildManager build manager info
     */
    public RepoAnalyzer(final String path, final BuildManager buildManager) {
        this.rootPath = path;
        this.buildManager = buildManager;
    }

    public static RepoAnalyzer of(final String repoPath) {
        var files = Arrays.stream(new File(repoPath).listFiles())
                .map(File::getName)
                .collect(Collectors.toSet());

        if (files.contains("pom.xml")) {
            return new MavenRepoAnalyzer(repoPath, BuildManager.maven);
        } else if (files.contains("build.gradle")) {
            return new GradleRepoAnalyzer(repoPath, BuildManager.gradle);
        } else if (files.contains("build.gradle.kts")) {
            return new GradleRepoAnalyzer(repoPath, BuildManager.gradleKotlin);
        } else {
            throw new UnsupportedOperationException("Only analysis of Maven, Gradle, and Ant "
                    + "repositories is available");
        }
    }

    public BuildManager getBuildManager() {
        return buildManager;
    }

    /**
     * Analyses tests in repository.
     *
     * @return JSON with statistics of the repository
     * @throws IOException if I/O exception occurs when accessing root file
     */
    public JSONObject analyze() throws IOException {
        var payload = new JSONObject();
        payload.put("repoPath", this.rootPath);
        payload.put("buildManager", this.buildManager);

        var moduleRoots = extractModuleRoots(Path.of(this.rootPath));

        var results = new JSONArray();
        for (var module : moduleRoots) {
            var statistics = new JSONObject();
            statistics.put("path", module.toAbsolutePath());

            var sourceFiles = getMatchingFiles(getPathToSourcesRoot(module), List.of("^.*\\.java"));
            statistics.put("sourceFiles", sourceFiles.size());

            var testFiles = getMatchingFiles(getPathToTestsRoot(module), getTestsPatterns());
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
     * Recursively get a list of files that have a name that matches one of the regular expressions.
     *
     * @param directory root to start searching from
     * @param patterns  list of regular expressions
     * @return list of files
     */
    private Set<Path> getMatchingFiles(final Path directory, final List<String> patterns) {
        var predicate = patterns.stream()
                .map(p -> Pattern.compile(p).asPredicate())
                .reduce(x -> false, Predicate::or);
        try {
            return Files.walk(directory)
                    .filter(f -> predicate.test(f.getFileName().toString()))
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            return new HashSet<>();
        }
    }

    /**
     * Get absolute path to the source files root. Extracts source file directory from pom.xml or
     * uses Maven default path.
     *
     * @param root root directory
     * @return root of the source files
     */
    protected abstract Path getPathToSourcesRoot(final Path root) throws IOException;

    /**
     * Get absolute path to the test files root. Extracts test file directory from pom.xml or
     * uses Maven default path.
     *
     * @param root root directory
     * @return root of the test files
     */
    protected abstract Path getPathToTestsRoot(final Path root) throws IOException;

    /**
     * Get a list of default Maven regular expressions that match test files names.
     * Extracts additional regular expressions from build file.
     *
     * @return list of regular expressions
     */
    protected abstract List<String> getTestsPatterns();

    /**
     * Get a map of files as keys and a list of test bodies as value.
     *
     * @param testClasses paths to test classes
     * @return a map of files and test bodies
     * @throws IOException if I/O exception occurs when reading a file
     */
    private Map<Path, List<BlockStmt>> getJUnitTests(final Set<Path> testClasses) throws IOException {
        var parser = new JavaParser();
        var testBodies = new HashMap<Path, List<BlockStmt>>();

        for (var testClass : testClasses) {
            var content = parser.parse(testClass)
                    .getResult()
                    .orElseThrow(IOException::new);

            var methods = content.findAll(MethodDeclaration.class)
                    .stream()
                    .filter(t -> t.getAnnotations()
                            .stream()
                            .anyMatch(a -> a.getName().equals(new Name("Test"))))
                    .map(t -> t.getBody().orElse(new BlockStmt()))
                    .collect(Collectors.toList());

            if (!methods.isEmpty()) {
                testBodies.put(testClass, methods);
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
        var parser = new JavaParser();
        var methodCounter = 0;

        for (var testClass : sourceFiles) {
            var content = parser.parse(testClass)
                    .getResult()
                    .orElseThrow(IOException::new);

            methodCounter += content.findAll(ClassOrInterfaceDeclaration.class)
                    .stream()
                    .map(NodeWithMembers::getMethods)
                    .map(List::size)
                    .reduce(0, Integer::sum);
        }
        return methodCounter;
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
    private Map<Path, List<BlockStmt>> getTestsWithMock(final Map<Path, List<BlockStmt>> testBodies,
                                                        final List<Path> filesWithMockImport) {
        var tests = new HashMap<Path, List<BlockStmt>>();

        var patterns = new String[]{
                "\\.mock\\(", "\\.when\\(", "\\.spy\\(", "\\.doNothing\\(", // Mockito
                "replayAll\\(\\)", "verifyAll\\(\\)", "\\.createMock\\(", // EasyMock
                "@Mocked", "new Expectations\\(\\)"}; // JMockit
        var predicate = Arrays.stream(patterns)
                .map(p -> Pattern.compile(p).asPredicate())
                .reduce(x -> false, Predicate::or);

        for (var file : filesWithMockImport) {
            tests.put(file, testBodies.get(file).stream().filter(t -> predicate.test(t.toString())).collect(Collectors.toList()));
        }

        return tests;
    }

    /**
     * Extract paths to all modules of the project.
     *
     * @param root root directory
     * @return a list of paths to modules
     */
    protected abstract List<Path> extractModuleRoots(final Path root) throws IOException;

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
