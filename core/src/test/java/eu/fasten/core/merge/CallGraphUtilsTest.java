package eu.fasten.core.merge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CallGraphUtilsTest {

    @TempDir
    public File tempDir;

    @Test
    public void writeToFileWithSuffix() throws IOException {
        var path = new File(tempDir, "sut").getPath();
        var message = "foo bar xxx";
        CallGraphUtils.writeToFile(path, message, ".txt");

        var expectedPath = new File(tempDir, "sut.txt");
        assertTrue(expectedPath.exists());

        final var actual =
            FileUtils.readFileToString(expectedPath.getAbsoluteFile(), StandardCharsets.UTF_8);
        var expected = message;
        assertEquals(expected, actual);
    }

    @Test
    public void writeToFileWithSuffixDoesNotBreakUmlauts() throws IOException {
        var path = new File(tempDir, "sut").getPath();
        var message = "äöüÄÖÜß";
        CallGraphUtils.writeToFile(path, message, ".txt");

        var expectedPath = new File(tempDir, "sut.txt");
        assertTrue(expectedPath.exists());

        final var actual =
            FileUtils.readFileToString(expectedPath.getAbsoluteFile(), StandardCharsets.UTF_8);
        var expected = message;
        assertEquals(expected, actual);
    }

    @Test
    public void writeToFileWithoutSuffix() throws IOException {
        var path = new File(tempDir, "sut.md").getPath();
        var message = "foo bar yyy";
        CallGraphUtils.writeToFile(path, message);

        var expectedPath = new File(tempDir, "sut.md");
        assertTrue(expectedPath.exists());

        final var actual =
            FileUtils.readFileToString(expectedPath.getAbsoluteFile(), StandardCharsets.UTF_8);
        var expected = message;
        assertEquals(expected, actual);
    }
}