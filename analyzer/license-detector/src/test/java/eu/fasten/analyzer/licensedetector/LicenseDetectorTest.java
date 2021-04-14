package eu.fasten.analyzer.licensedetector;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.*;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

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
        inputToExpected.forEach((repoAbsolutePath, expectedRetrievedPomFile) ->
                assertEquals(
                        expectedRetrievedPomFile,
                        new LicenseDetectorPlugin.LicenseDetector().retrievePomFile(repoAbsolutePath).get(),
                        "Retrieved pom.xml file is not the one the test expected."
                ));
    }

    @Test
    public void givenRepoPath_whenPatchingPomFile_thenPomPatchingCompletesSuccessfully() {

        // Test Maven repo paths
        List<String> repoPaths = Arrays.asList(
                "no-build-section-maven-project",
                "no-plugins-section-maven-project",
                "complete-maven-project",
                "simple-maven-repo"
        );

        // Absolute test Maven repo path -> expected patched pom file
        Map<String, File> inputToExpected = new HashMap<>();
        repoPaths.forEach(repoPath -> {

            // Retrieving test Maven repo absolute path
            String repoAbsolutePath = new File(Objects.requireNonNull(LicenseDetectorTest.class.getClassLoader()
                    .getResource(repoPath)).getFile()).getAbsolutePath();
            assertNotNull(repoAbsolutePath, "Test Maven repo's absolute path shouldn't be empty.");

            File repoPomFile = new File(Objects.requireNonNull(LicenseDetectorTest.class.getClassLoader()
                    .getResource(repoPath + "/expected-patched-pom.xml")).getFile());
            assertFalse(repoPomFile.isDirectory(),
                    "The expected test Maven repo patched pom.xml file shouldn't be a directory.");

            inputToExpected.put(repoAbsolutePath, repoPomFile);
        });

        // For all inputs
        inputToExpected.forEach((repoAbsolutePath, expectedPatchedPomFile) -> {

            // Patch the pom.xml file
            try {
                new LicenseDetectorPlugin.LicenseDetector().patchPomFile(repoAbsolutePath);
            } catch (ParserConfigurationException e) {
                System.err.println("XML parser's configuration is invalid.");
                fail();
            } catch (IOException | URISyntaxException e) {
                System.err.println("Couldn't find an XML file: " + e.getMessage());
                fail();
            } catch (TransformerException e) {
                System.err.println("Couldn't overwrite the XML file.");
                fail();
            }

            // Checking whether the patched pom.xml file contains the Quartermaster Maven plugin
            File patchedFile = new File(repoAbsolutePath + "/pom.xml");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            try {
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document patchedPomDocument = builder.parse(patchedFile);
                String xPathExpression = "//build/plugins/plugin/groupId[text() = 'org.qmstr']";
                assertTrue(documentContainsNode(patchedPomDocument, xPathExpression));
            } catch (ParserConfigurationException e) {
                System.err.println("Couldn't instantiate a DocumentBuilder while comparing XML documents.");
                fail();
            } catch (SAXException e) {
                System.err.println("Couldn't parse an XML document during comparison: " + e.getMessage());
                fail();
            } catch (IOException e) {
                System.err.println("An I/O error occurred while comparing XML documents: " + e.getMessage());
                fail();
            } catch (XPathExpressionException e) {
                System.err.println("Invalid XPath expression: " + e.getMessage());
                fail();
            }
        });
    }

    /**
     * Checks whether an XML document contains a XML node inside.
     *
     * @param document        the XML document to be checked.
     * @param xpathExpression the XPath expression to be used to locate the XML node to be checked.
     * @return true if the XML document contains the XML node specified by the XPath expression.
     * @throws XPathExpressionException in case the XPath expression is invalid.
     */
    private static boolean documentContainsNode(Document document, String xpathExpression)
            throws XPathExpressionException {
        boolean matches = false;
        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpath = xpathFactory.newXPath();
        XPathExpression expr = xpath.compile(xpathExpression);
        NodeList nodes = (NodeList) xpath.evaluate(xpathExpression, document, XPathConstants.NODE);
        if (nodes != null && nodes.getLength() > 0) {
            matches = true;
        }
        return matches;
    }
}
