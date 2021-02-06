package eu.fasten.analyzer.repoanalyzer.repo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class MavenRepoAnalyzer extends RepoAnalyzer {

    /**
     * Constructs a Repo Analyzer given a path the root of a repository to analyze.
     *
     * @param path         path to the repository
     * @param buildManager build manager info
     */
    public MavenRepoAnalyzer(String path, RepoAnalyzer.BuildManager buildManager) {
        super(path, buildManager);
    }

    @Override
    protected Path getPathToSourcesRoot(Path root) throws IOException {
        var pomContent = Files.readString(Path.of(root.toString(), "pom.xml"));
        var sourcePath = StringUtils.substringBetween(pomContent, "<sourceDirectory>", "</sourceDirectory>");
        sourcePath = sourcePath == null ? DEFAULT_SOURCES_PATH : sourcePath;
        while (sourcePath.contains("$")) {
            sourcePath = sourcePath.replaceFirst("\\$\\{.*}", "");
        }
        return Path.of(root.toAbsolutePath().toString(), sourcePath);
    }

    @Override
    protected Path getPathToTestsRoot(Path root) throws IOException {
        var pomContent = Files.readString(Path.of(root.toString(), "pom.xml"));
        var sourcePath = StringUtils.substringBetween(pomContent, "<testSourceDirectory>", "</testSourceDirectory>");
        sourcePath = sourcePath == null ? DEFAULT_TESTS_PATH : sourcePath;
        while (sourcePath.contains("$")) {
            sourcePath = sourcePath.replaceFirst("\\$\\{.*}", "");
        }
        return Path.of(root.toAbsolutePath().toString(), sourcePath);
    }

    @Override
    protected List<String> getTestsPatterns() {
        // TODO: take into account custom regex configurations of maven surefire plugin
        // https://maven.apache.org/surefire/maven-surefire-plugin/examples/inclusion-exclusion.html

        var patterns = new ArrayList<String>();

        patterns.add("^.*Test\\.java");
        patterns.add("^Test.*\\.java");
        patterns.add("^.*Tests\\.java");
        patterns.add("^.*TestCase\\.java");

        return patterns;
    }

    @Override
    protected List<Path> extractModuleRoots(Path root) throws IOException {
        var moduleRoots = new ArrayList<Path>();

        var pomContent = Files.readString(Path.of(root.toAbsolutePath().toString(), "pom.xml"));
        var modules = StringUtils.substringBetween(pomContent, "<modules>", "</modules>");

        if (modules == null) {
            moduleRoots.add(root);
            return moduleRoots;
        }

        var moduleTags = modules.split("</module>");
        var moduleNames = Arrays.stream(moduleTags)
                .filter(t -> t.contains("<module>"))
                .map(t -> t.substring(t.indexOf("<module>") + 8))
                .map(t -> Path.of(root.toAbsolutePath().toString(), t))
                .collect(Collectors.toList());
        for (var module : moduleNames) {
            moduleRoots.addAll(extractModuleRoots(module));
        }
        return moduleRoots;
    }
}
