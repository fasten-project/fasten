package eu.fasten.analyzer.repoanalyzer.repo;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

public class RepoAnalyzerFactory {

    /**
     * Create a new instance of RepoAnalyzer of the right type based on the build manager used
     * in the repository.
     *
     * @param repoPath repo path
     * @return RepoAnalyzer of right type
     */
    public RepoAnalyzer getAnalyzer(final String repoPath) {
        var files = Arrays.stream(new File(repoPath).listFiles())
                .map(File::getName)
                .collect(Collectors.toSet());

        if (files.contains("pom.xml")) {
            return new MavenRepoAnalyzer(Path.of(repoPath), BuildManager.maven);
        } else if (files.contains("build.gradle")) {
            return new GradleRepoAnalyzer(Path.of(repoPath), BuildManager.gradle);
        } else if (files.contains("build.gradle.kts")) {
            return new GradleRepoAnalyzer(Path.of(repoPath), BuildManager.gradleKotlin);
        } else if (files.contains("build.xml")) {
            return new AntRepoAnalyzer(Path.of(repoPath), BuildManager.ant);
        } else {
            throw new UnsupportedOperationException("Only analysis of Maven, Gradle, and Ant "
                    + "repositories is available");
        }
    }
}
