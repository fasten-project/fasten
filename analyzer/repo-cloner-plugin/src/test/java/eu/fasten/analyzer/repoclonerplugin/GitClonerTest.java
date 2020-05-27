package eu.fasten.analyzer.repoclonerplugin;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GitClonerTest {

    private GitCloner gitCloner;
    private String baseDir;

    @BeforeEach
    public void setup() throws IOException {
        this.baseDir = Files.createTempDirectory("").toString();
        this.gitCloner = new GitCloner(baseDir);
    }

    @AfterEach
    public void teardown() throws IOException {
        FileUtils.deleteDirectory(Path.of(baseDir).toFile());
    }

    @Test
    public void cloneRepoTest() throws GitAPIException, IOException {
        var repo = Path.of(baseDir, "mvn/f/fasten").toFile();
        var path = gitCloner.cloneRepo("fasten", "https://github.com/fasten-project/fasten.git");
        Assertions.assertEquals(repo.getAbsolutePath(), path);
        Assertions.assertTrue(repo.exists());
        Assertions.assertTrue(repo.isDirectory());
    }

    @Test
    public void cloneRepoWithoutExtensionTest() throws GitAPIException, IOException {
        var repo = Path.of(baseDir, "mvn/f/fasten").toFile();
        var path = gitCloner.cloneRepo("fasten", "https://github.com/fasten-project/fasten");
        Assertions.assertEquals(repo.getAbsolutePath(), path);
        Assertions.assertTrue(repo.exists());
        Assertions.assertTrue(repo.isDirectory());
    }

    @Test
    public void cloneRepoWithoutExtensionWithSlashTest() throws GitAPIException, IOException {
        var repo = Path.of(baseDir, "mvn/f/fasten").toFile();
        var path = gitCloner.cloneRepo("fasten", "https://github.com/fasten-project/fasten/");
        Assertions.assertEquals(repo.getAbsolutePath(), path);
        Assertions.assertTrue(repo.exists());
        Assertions.assertTrue(repo.isDirectory());
    }
}
