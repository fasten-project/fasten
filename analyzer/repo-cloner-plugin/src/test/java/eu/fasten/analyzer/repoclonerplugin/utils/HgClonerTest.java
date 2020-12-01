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
        // TODO: Find a new Hg repo that works
//        var repo = Path.of(baseDir, "t", "test", "repo").toFile();
//        var result = this.hgCloner.cloneRepo("http://pvmanager.hg.sourceforge.net/hgweb/pvmanager/pvmanager", "repo", "test");
//        assertTrue(repo.exists());
//        assertTrue(repo.isDirectory());
//        assertEquals(repo.getAbsolutePath(), result);
    }
}
