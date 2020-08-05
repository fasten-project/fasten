package eu.fasten.analyzer.repoclonerplugin.utils;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tmatesoft.hg.core.HgException;
import org.tmatesoft.hg.util.CancelledException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HgClonerTest {

    private HgCloner hgCloner;
    private String baseDir;

    @BeforeEach
    public void setup() throws IOException {
        this.baseDir = Files.createTempDirectory("").toString();
        this.hgCloner = new HgCloner(baseDir);
    }

    @AfterEach
    public void teardown() throws IOException {
        FileUtils.deleteDirectory(Path.of(baseDir).toFile());
    }

    @Test
    public void cloneRepoTest() throws CancelledException, HgException, MalformedURLException {
        var repo = Path.of(baseDir, "b", "bluefen", "html5app").toFile();
        var result = this.hgCloner.cloneRepo("https://bitbucket.org/bluefen/html5app");
        assertTrue(repo.exists());
        assertTrue(repo.isDirectory());
        assertEquals(repo.getAbsolutePath(), result);
    }

    @Test
    public void cloneRepoWithVersionTest() throws CancelledException, HgException, MalformedURLException {
        var repo = Path.of(baseDir, "r", "redberry", "redberry-physics").toFile();
        var result = this.hgCloner.cloneRepo("https://bitbucket.org/redberry/redberry-physics/src/?at=v1.1");
        assertTrue(repo.exists());
        assertTrue(repo.isDirectory());
        assertEquals(repo.getAbsolutePath(), result);
    }
}
