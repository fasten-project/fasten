package eu.fasten.analyzer.licensedetector;

import com.google.common.collect.Sets;
import eu.fasten.core.data.metadatadb.license.DetectedLicense;
import eu.fasten.core.data.metadatadb.license.DetectedLicenseSource;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class LicenseDetectorTest {

    private LicenseDetectorPlugin.LicenseDetector licenseDetector;

    @BeforeEach
    public void setup() {
        licenseDetector = new LicenseDetectorPlugin.LicenseDetector();
    }

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
                fail("Couldn't locate the test file at " + recordFilePath + ": " + e.getMessage(), e.getCause());
            }
            assertNotNull(recordContent, "Test record content hasn't been retrieved.");
            assertFalse(recordContent.isEmpty(), "Test record shouldn't be empty."); // shouldn't be empty

            // Extracting repository path
            assertEquals(
                    expectedExtractedRepoPath,
                    licenseDetector.extractRepoPath(recordContent),
                    "Extracted repository path did not match test record content."
            );
        });
    }

    @Test
    public void givenRepoPath_whenRetrievingPomFile_thenPomFileIsRetrieved() {

        // Test Maven repo paths
        List<String> repoPaths = Arrays.asList("complete-maven-project", "simple-maven-repo");

        // Absolute test Maven repo path -> expected retrieved pom file
        Map<String, File> inputToExpected = new HashMap<>();
        repoPaths.forEach(repoPath -> {

            // Retrieving test Maven repo absolute path
            String repoAbsolutePath = new File(Objects.requireNonNull(LicenseDetectorTest.class.getClassLoader()
                    .getResource(repoPath)).getFile()).getAbsolutePath();
            assertNotNull(repoAbsolutePath, "Test Maven repo's absolute path shouldn't be empty.");

            File repoPomFile = new File(Objects.requireNonNull(LicenseDetectorTest.class.getClassLoader()
                    .getResource(repoPath + "/pom.xml")).getFile());
            assertFalse(repoPomFile.isDirectory(), "Test Maven repo pom.xml file shouldn't be a directory.");

            inputToExpected.put(repoAbsolutePath, repoPomFile);
        });

        // For all inputs
        inputToExpected.forEach((repoAbsolutePath, expectedRetrievedPomFile) -> {

            try {
                assertTrue(
                        licenseDetector.retrievePomFile(repoAbsolutePath).exists(),
                        "pom.xml file does not exist."
                );

                assertTrue(
                        licenseDetector.retrievePomFile(repoAbsolutePath).isFile(),
                        "Retrieved pom.xml file is a directory."
                );

                assertEquals(
                        expectedRetrievedPomFile,
                        licenseDetector.retrievePomFile(repoAbsolutePath),
                        "Retrieved pom.xml file is not the one the test expected."
                );
            } catch (FileNotFoundException e) {
                fail("Test has failed with the following exception: " + e.getMessage(), e.getCause());
            }
        });
    }

    @Test
    public void givenRepo_whenRetrievingLicensesFromLocalPomFile_thenLicensesAreCorrectlyRetrieved() {

        // Relative Maven repo path -> expected detected licenses
        Map<String, Set<DetectedLicense>> inputToExpected = Map.ofEntries(
                Map.entry(
                        "complete-maven-project",
                        Stream.of(
                                // FIXME SPDX IDs
                                new DetectedLicense(
                                        "Apache License, Version 2.0",
                                        DetectedLicenseSource.LOCAL_POM
                                ),
                                new DetectedLicense(
                                        "GNU General Public License (GPL) version 2, or any later version",
                                        DetectedLicenseSource.LOCAL_POM
                                ),
                                new DetectedLicense(
                                        "GPLv2 with Classpath exception",
                                        DetectedLicenseSource.LOCAL_POM
                                )
                        ).collect(Collectors.toCollection(HashSet::new))
                ),
                Map.entry(
                        "empty-license-section-maven-project",
                        Collections.<DetectedLicense>emptySet()
                ),
                Map.entry(
                        "no-license-section-maven-project",
                        Collections.<DetectedLicense>emptySet()
                )
        );

        inputToExpected.forEach((relativeRepoPath, expectedDetectedLicenses) -> {

            // Retrieving the test Maven repo pom file
            File pomFile = new File(Objects.requireNonNull(LicenseDetectorTest.class.getClassLoader()
                    .getResource(relativeRepoPath + "/pom.xml")).getFile());
            assertFalse(pomFile.isDirectory(), "Test Maven repo pom.xml file shouldn't be a directory.");

            try {
                assertEquals(
                        licenseDetector.getLicensesFromPomFile(pomFile),
                        expectedDetectedLicenses,
                        "Retrieved and expected outbound licenses do not match."
                );
            } catch (RuntimeException | XmlPullParserException e) {
                fail("Test has failed with the following exception: " + e.getMessage(), e.getCause());
            }
        });
    }

    @Test
    public void givenScanResult_whenParsingScanResult_thenLicensesAreCorrectlyRetrieved() {

        // Relative scan result file path -> expected number of scanned files
        Map<String, Integer> inputToExpected = Map.ofEntries(
                Map.entry("scancode-results/javacv.json", 89),
                Map.entry("scancode-results/telegrambots.json", 30)
        );

        inputToExpected.forEach((relativeScanResultPath, expectedNumberScannedFiles) -> {

            // Retrieving scan result file absolute path
            String absoluteScanResultPath = new File(Objects.requireNonNull(LicenseDetectorTest.class.getClassLoader()
                    .getResource(relativeScanResultPath)).getFile()).getAbsolutePath();
            assertNotNull(absoluteScanResultPath, "Test scan result file absolute path shouldn't be empty.");


            try {

                // Parsing the scan result
                JSONArray fileLicenses = licenseDetector.parseScanResult(absoluteScanResultPath);

                // All test cases contain results with at least one file
                assertNotNull(fileLicenses, "Test case should contain at least one scanned file.");

                // Checking whether the number of scanned files is correct or not
                assertEquals(expectedNumberScannedFiles, fileLicenses.length(),
                        "Number of scanned files does not match with the expect value.");
            } catch (IOException e) {
                fail("Couldn't read the test scan result file: " + e.getMessage(), e.getCause());
            } catch (JSONException e) {
                fail("Coudln't retieve the root element of the test scan result file: " + e.getMessage(), e.getCause());
            }
        });
    }

    @Test
    public void givenDetectedLicensesObject_whenPluginGeneratesJsonResult_thenJsonResultIsCorrectlyGenerated() {

        // Input data
        DetectedLicense outboundLicense = new DetectedLicense("license", DetectedLicenseSource.LOCAL_POM);
        JSONArray files = new JSONArray().put(new JSONObject().put("path", "MyClass.java"));

        // Expected JSON result
        JSONArray expectedOutboundLicenses = new JSONArray().put(
                new JSONObject().put("name", outboundLicense.getName()).put("source", outboundLicense.getSource()));
        String expectedJsonResult = new JSONObject()
                .put("outbound", expectedOutboundLicenses).put("files", files).toString();

        // Pre-fill license detector with the licenses declared above
        licenseDetector.detectedLicenses.setOutbound(Sets.newHashSet(outboundLicense));
        licenseDetector.detectedLicenses.addFiles(files);

        // Producing the output JSON
        Optional<String> result = licenseDetector.produce();

        // Must generate a result
        assertTrue(result.isPresent(), "Plugin should have generated a JSON result, but it's empty.");

        // Checking whether the JSON result is the expected one or not
        assertEquals(expectedJsonResult.compareToIgnoreCase(result.get()), 0);
    }

    @Test
    public void givenRepoUrls_whenRetrievingOutboundLicenseFromGitHub_thenOutboudLicenseIsCorrectlyRetrieved() {

        // GitHub repo URL -> its SPDX license ID
        Map<String, String> inputToExpected = Map.ofEntries(
                Map.entry("HTTPS://GITHUB.COM/EsotericSoftware/reflectasm.git", "BSD-3-Clause"),
                Map.entry("https://github.com/EsotericSoftware/reflectasm.git", "BSD-3-Clause"),
                Map.entry("https://github.com/EsotericSoftware/reflectasm/", "BSD-3-Clause"),
                Map.entry("https://github.com/EsotericSoftware/reflectasm", "BSD-3-Clause"),
                Map.entry("http://github.com/EsotericSoftware/reflectasm", "BSD-3-Clause"),
                Map.entry("github.com/EsotericSoftware/reflectasm", "BSD-3-Clause"),
                Map.entry("https://github.com/remkop/picocli/tree/master", "Apache-2.0"),
                Map.entry("https://github.com/remkop/picocli", "Apache-2.0"),
                Map.entry("https://tomakehurst@github.com/tomakehurst/wiremock.git", "Apache-2.0")
        );

        inputToExpected.forEach((repoUrl, expectedLicense) -> {
            try {

                // Retrieving the outbound license from GitHub
                DetectedLicense retrievedLicense = licenseDetector.getLicenseFromGitHub(repoUrl);

                // Checking whether the retrieved license is equal to the expected one or not
                assertEquals(expectedLicense.compareToIgnoreCase(retrievedLicense.getName()), 0,
                        "The outbound license retrieved from GitHub is not equal to the expected one.");
            } catch (IllegalArgumentException | IOException e) { // not a valid GitHub repo URL
                fail("Invalid GitHub URL: " + e.getMessage(), e.getCause());
            } catch (@SuppressWarnings({"TryWithIdenticalCatches", "RedundantSuppression"}) RuntimeException e) {
                fail("Could not contact GitHub API: " + e.getMessage(), e.getCause());
            }
        });
    }
}
