package eu.fasten.analyzer.repoclonerplugin.utils;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tmatesoft.svn.core.SVNException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SvnClonerTest {

    private SvnCloner svnCloner;
    private String baseDir;

    @BeforeEach
    public void setup() throws IOException {
        this.baseDir = Files.createTempDirectory("").toString();
        this.svnCloner = new SvnCloner(baseDir);
    }

    @AfterEach
    public void teardown() throws IOException {
        FileUtils.deleteDirectory(Path.of(baseDir).toFile());
    }

    @Test
    public void cloneRepoTest() throws SVNException {
        var repo = Path.of(baseDir, "n", "net.sf.sparta-spring-web-utils.spring-web-utils", "spring-web-utils").toFile();
        var result = this.svnCloner.cloneRepo("svn://svn.code.sf.net/p/sparta-spring-web-utils/code/", "spring-web-utils", "net.sf.sparta-spring-web-utils.spring-web-utils");
        assertTrue(repo.exists());
        assertTrue(repo.isDirectory());
        assertEquals(repo.getAbsolutePath(), result);
    }
}
