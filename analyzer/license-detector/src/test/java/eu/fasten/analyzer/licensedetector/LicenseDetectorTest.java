package eu.fasten.analyzer.licensedetector;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LicenseDetectorTest {

    @Test
    public void givenRepoClonerRecordContainingRepoPath_whenExtractingRepoPath_thenRepoPathIsExtracted() {

        // Input RepoCloner record -> expected extracted repo path
        Map<String, String> inputToExpected = Map.ofEntries(
                Map.entry(
                        "well-formed-input-records/wiremock.json",
                        "/mnt/fasten/c/com.github.tomakehurst/wiremock-standalone"
                ),
                Map.entry(
                        "well-formed-input-records/log4j.json",
                        ""
                )
        );

        // For all inputs
        inputToExpected.forEach((recordFilePath, expectedExtractedRepoPath) -> {

            // Retrieving the well-formed record's content
            String recordContent = null;
            try {
                recordContent = Files.readString(Paths.get(ClassLoader.getSystemResource(recordFilePath).toURI()));
            } catch (IOException | URISyntaxException e) {
                System.err.print("Couldn't locate test file " + recordFilePath + ".");
                fail();
            }
            assertNotNull(recordContent, "Test record content hasn't been retrieved.");
            assertFalse(recordContent.isEmpty(), "Test record shouldn't be empty."); // shouldn't be empty

            // Extracting repository path
            assertEquals(
                    expectedExtractedRepoPath,
                    new LicenseDetectorPlugin.LicenseDetector().extractRepoPath(recordContent),
                    "Extracted repository path did not match test record content."
            );
        });
    }
}
