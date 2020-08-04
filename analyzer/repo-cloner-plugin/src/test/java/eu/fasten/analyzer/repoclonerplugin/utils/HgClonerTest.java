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
    public void cloneRepoTest() {
        try {
            this.hgCloner.cloneRepo("https://bitbucket.org/bluefen/html5app");
        } catch (MalformedURLException | HgException | CancelledException e) {
            e.printStackTrace();
        }
    }
}
