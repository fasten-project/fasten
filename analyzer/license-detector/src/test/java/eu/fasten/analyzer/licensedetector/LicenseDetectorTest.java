package eu.fasten.analyzer.licensedetector;

import eu.fasten.analyzer.licensedetector.license.DetectedLicense;
import eu.fasten.analyzer.licensedetector.license.DetectedLicenseSource;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            assertTrue(
                    new LicenseDetectorPlugin.LicenseDetector().retrievePomFile(repoAbsolutePath).isPresent(),
                    "pom.xml file has not been retrieved."
            );

            assertEquals(
                    expectedRetrievedPomFile,
                    new LicenseDetectorPlugin.LicenseDetector().retrievePomFile(repoAbsolutePath).get(),
                    "Retrieved pom.xml file is not the one the test expected."
            );
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
                                new DetectedLicense("Apache License, Version 2.0", DetectedLicenseSource.LOCAL_POM),
                                new DetectedLicense("GNU General Public License (GPL) version 2, or any later version", DetectedLicenseSource.LOCAL_POM),
                                new DetectedLicense("GPLv2 with Classpath exception", DetectedLicenseSource.LOCAL_POM)
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
                        new LicenseDetectorPlugin.LicenseDetector().getOutboundLicenses(pomFile),
                        expectedDetectedLicenses,
                        "Retrieved and expected outbound licenses do not match."
                );
            } catch (RuntimeException | XmlPullParserException e) {
                fail("Test has failed with the following exception: " + e.getMessage());
            }
        });
    }

    @Test
    public void givenRepoWithDependencies_whenRetrievingDependenciesLicenses_thenDependencyLicensesAreCorrectlyRetrieved() {

        // Relative Maven repo path -> expected detected dependency licenses
        Map<String, Set<DetectedLicense>> inputToExpected = Map.ofEntries(
                Map.entry(
                        // Two dependencies with clearly defined licenses
                        "complete-maven-project",
                        Stream.of(
                                // FIXME SPDX IDs
                                new DetectedLicense("Apache-2.0", DetectedLicenseSource.MAVEN_CENTRAL),
                                new DetectedLicense("Eclipse Public License 1.0", DetectedLicenseSource.MAVEN_CENTRAL)
                        ).collect(Collectors.toCollection(HashSet::new))
                ),
                Map.entry(
                        "empty-license-section-maven-project",
                        Collections.<DetectedLicense>emptySet()
                ),
                Map.entry(
                        "no-license-section-maven-project",
                        Collections.<DetectedLicense>emptySet()
                ),
                Map.entry(
                        // This case has a lot of dependencies without the `licenses` XML tag in their `pom.xml` file
                        "licensed-dependencies-maven-project",
                        Stream.of(
                                // FIXME SPDX IDs
                                new DetectedLicense("Apache-2.0", DetectedLicenseSource.MAVEN_CENTRAL),
                                new DetectedLicense("Eclipse Public License 1.0", DetectedLicenseSource.MAVEN_CENTRAL)
                        ).collect(Collectors.toCollection(HashSet::new))
                )
        );

        inputToExpected.forEach((relativeRepoPath, expectedDetectedDependencyLicenses) -> {

            // Retrieving the test Maven repo pom file
            File pomFile = new File(Objects.requireNonNull(LicenseDetectorTest.class.getClassLoader()
                    .getResource(relativeRepoPath + "/pom.xml")).getFile());
            assertFalse(pomFile.isDirectory(), "Test Maven repo pom.xml file shouldn't be a directory.");

            try {
                assertEquals(
                        new LicenseDetectorPlugin.LicenseDetector().getDependencyLicensesFromMavenCentral(pomFile),
                        expectedDetectedDependencyLicenses,
                        "Retrieved and expected dependency inbound licenses do not match."
                );
            } catch (RuntimeException | XmlPullParserException e) {
                fail("Test has failed with the following exception: " + e.getMessage());
            }
        });
    }
}
