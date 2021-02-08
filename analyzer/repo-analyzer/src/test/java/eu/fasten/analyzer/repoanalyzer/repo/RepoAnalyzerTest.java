package eu.fasten.analyzer.repoanalyzer.repo;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.dom4j.DocumentException;
import org.junit.jupiter.api.Test;

class RepoAnalyzerTest {

    @Test
    void analyze() throws IOException, DocumentException {
        var root = new File(Objects.requireNonNull(MavenRepoAnalyzer.class.getClassLoader().getResource("completeMavenProject")).getFile()).getAbsolutePath();
        var analyzer = RepoAnalyzer.of(root);
        var result = analyzer.analyze();

        assertNotNull(result);

        assertEquals(RepoAnalyzer.BuildManager.maven, result.get("buildManager"));
        assertEquals(root, result.get("repoPath"));

        var modules = result.getJSONArray("modules");
        assertEquals(2, modules.length());

        var module1Index = 0;
        var module2Index = 1;
        var module1 = modules.getJSONObject(module1Index);
        if (!module1.getString("path").equals(Path.of(root, "module1").toString())) {
            module1Index = 1;
            module2Index = 0;
            module1 = modules.getJSONObject(module1Index);
        }

        assertEquals(0, module1.getInt("unitTestsWithMocks"));
        assertEquals(Path.of(root, "module1").toString(), module1.getString("path"));
        assertEquals(0, module1.getInt("filesWithMockImport"));
        assertEquals(2, module1.getInt("sourceFiles"));
        assertEquals(6, module1.getInt("numberOfFunctions"));
        assertEquals(2, module1.getInt("numberOfUnitTests"));
        assertEquals(1, module1.getInt("testFiles"));
        assertEquals(0.333, module1.getDouble("unitTestsToFunctionsRatio"));
        assertEquals(0.5, module1.getDouble("testToSourceRatio"));
        assertEquals(0, module1.getDouble("unitTestsMockingRatio"));

        var module2 = modules.getJSONObject(module2Index);
        assertEquals(1, module2.getInt("unitTestsWithMocks"));
        assertEquals(Path.of(root, "module2").toString(), module2.getString("path"));
        assertEquals(1, module2.getInt("filesWithMockImport"));
        assertEquals(1, module2.getInt("sourceFiles"));
        assertEquals(1, module2.getInt("numberOfFunctions"));
        assertEquals(3, module2.getInt("numberOfUnitTests"));
        assertEquals(2, module2.getInt("testFiles"));
        assertEquals(3, module2.getDouble("unitTestsToFunctionsRatio"));
        assertEquals(2, module2.getDouble("testToSourceRatio"));
        assertEquals(0.333, module2.getDouble("unitTestsMockingRatio"));
    }
}