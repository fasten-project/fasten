package eu.fasten.analyzer.repoanalyzer.repo;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.dom4j.DocumentException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AntRepoAnalyzerTest {

    private static RepoAnalyzer analyzer;
    private static String root;

    @BeforeAll
    static void setUp() {
        root = new File(Objects.requireNonNull(AntRepoAnalyzerTest.class.getClassLoader().getResource("simpleAntRepo")).getFile()).getAbsolutePath();
        analyzer = RepoAnalyzer.of(root);
    }

    @Test
    void analyzerTypeCheck() {
        assertTrue(analyzer instanceof AntRepoAnalyzer);
    }

    @Test
    void getPathToSourcesRoot() throws IOException, DocumentException {
        assertEquals(Path.of(root, "src/main/java"), analyzer.getPathToSourcesRoot(Path.of(root)));
    }

    @Test
    void getPathToTestsRoot() throws IOException, DocumentException {
        assertEquals(Path.of(root, "src/test/java"), analyzer.getPathToTestsRoot(Path.of(root)));
    }

    @Test
    void extractModuleRoots() throws IOException, DocumentException {
        assertEquals(List.of(Path.of(root)), analyzer.extractModuleRoots(Path.of(root)));
    }
}