package eu.fasten.analyzer.licensedetector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import eu.fasten.analyzer.licensedetector.LicenseDetectorPlugin.LicenseDetector;
import eu.fasten.analyzer.licensedetector.exceptions.LicenseDetectorException;
import eu.fasten.analyzer.licensedetector.license.DetectedLicense;
import eu.fasten.analyzer.licensedetector.license.DetectedLicenseSource;
import eu.fasten.analyzer.licensedetector.license.DetectedLicenses;

public class JavaMavenLicenseDetector extends AbstractLicenseDetector {
	
    private final Logger logger = LoggerFactory.getLogger(LicenseDetector.class.getName());
    
	// TODO: verify if this approach is valid also for java
	// private static final List<String> LICENSE_FILES = Arrays.asList("pom.xml");
	
	@Override
	public DetectedLicenses detect(String repoPath, String repoUrl) throws LicenseDetectorException {
		DetectedLicenses result = new DetectedLicenses();
		
		JSONArray files = detectFileLicenses(repoPath);
		result.setFiles(files);
		
		// Set<DetectedLicense> outbound = detectOutboundLicenses(repoPath, LICENSE_FILES, files);

		Set<DetectedLicense> outbound = getOutboundLicenses(result, repoPath, repoUrl);
		result.setOutbound(outbound);
		
		return result;
	}

	 /**
     * Retrieves the outbound license(s) of the input project.
     *
     * @param repoPath the repository path whose outbound license(s) is(are) of interest.
     * @param repoUrl  the input repository URL. Might be `null`.
     * @return the set of detected outbound licenses.
     */
		protected Set<DetectedLicense> getOutboundLicenses(DetectedLicenses detectedLicenses, String repoPath,
				@Nullable String repoUrl) {

        try {

            // Retrieving the `pom.xml` file
            File pomFile = retrievePomFile(repoPath);

            // Retrieving the outbound license(s) from the `pom.xml` file
            return getLicensesFromPomFile(pomFile);

        } catch (FileNotFoundException | RuntimeException | XmlPullParserException e) {

            // In case retrieving the outbound license from the local `pom.xml` file was not possible
            logger.warn(e.getMessage(), e.getCause()); // why wasn't it possible
            logger.info("Retrieving outbound license from GitHub...");
            if ((detectedLicenses.getOutbound() == null || detectedLicenses.getOutbound().isEmpty())
                    && repoUrl != null) {

                // Retrieving licenses from the GitHub API
                try {
                    DetectedLicense licenseFromGitHub = getLicenseFromGitHub(repoUrl);
                    if (licenseFromGitHub != null) {
                        return Sets.newHashSet(licenseFromGitHub);
                    } else {
                        logger.warn("Couldn't retrieve the outbound license from GitHub.");
                    }
                } catch (IllegalArgumentException | IOException ex) { // not a valid GitHub repo URL
                    logger.warn(e.getMessage(), e.getCause());
                } catch (RuntimeException ex) {
                    logger.warn(e.getMessage(), e.getCause()); // could not contact GitHub API
                }
            }
        }

        return Collections.emptySet();
    }

    /**
     * Retrieves all licenses declared in a `pom.xml` file.
     *
     * @param pomFile the `pom.xml` file to be analyzed.
     * @return the detected licenses.
     * @throws XmlPullParserException in case the `pom.xml` file couldn't be parsed as an XML file.
     */
    protected Set<DetectedLicense> getLicensesFromPomFile(File pomFile) throws XmlPullParserException {

        // Result
        List<License> licenses;

        // Maven `pom.xml` file parser
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try (FileReader fileReader = new FileReader(pomFile)) {

            // Parsing and retrieving the `licenses` XML tag
            Model model = reader.read(fileReader);
            licenses = model.getLicenses();

            // If the pom file contains at least a license tag
            if (!licenses.isEmpty()) {

                // Logging
                logger.trace("Found " + licenses.size() + " outbound license" + (licenses.size() == 1 ? "" : "s") +
                        " in " + pomFile.getAbsolutePath() + ":");
                for (int i = 0; i < licenses.size(); i++) {
                    logger.trace("License number " + i + ": " + licenses.get(i).getName());
                }

                // Returning the set of discovered licenses
                Set<DetectedLicense> result = new HashSet<>(Collections.emptySet());
                licenses.forEach(license -> result.add(new DetectedLicense(
                        license.getName(),
                        DetectedLicenseSource.LOCAL_POM)));

                return result;
            }
        } catch (IOException e) {
            throw new RuntimeException("Pom file " + pomFile.getAbsolutePath() +
                    " exists but couldn't instantiate a FileReader object..", e.getCause());
        } catch (XmlPullParserException e) {
            throw new XmlPullParserException("Pom file " + pomFile.getAbsolutePath() +
                    " exists but couldn't be parsed as a Maven pom XML file: " + e.getMessage());
        }

        // No licenses were detected
        return Collections.emptySet();
    }

    /**
     * Retrieves the pom.xml file given a repository path.
     *
     * @param repoPath the repository path whose pom.xml file must be retrieved.
     * @return the pom.xml file of the repository.
     * @throws FileNotFoundException in case no pom.xml file could be found in the repository.
     */
    protected File retrievePomFile(String repoPath) throws FileNotFoundException {

        // Result
        Optional<File> pomFile = Optional.empty();

        // Repository folder
        File repoFolder = new File(repoPath);

        // Retrieving all repository's pom files
        File[] pomFiles = repoFolder.listFiles((dir, name) -> name.equalsIgnoreCase("pom.xml"));
        if (pomFiles == null) {
            throw new RuntimeException("Path " + repoPath + " does not denote a directory.");
        }
        logger.trace("Found " + pomFiles.length + " pom.xml file" +
                ((pomFiles.length == 1) ? "" : "s") + ": " + Arrays.toString(pomFiles));
        if (pomFiles.length == 1) {
            pomFile = Optional.ofNullable(pomFiles[0]);
        } else if (pomFiles.length > 1) {
            // Retrieving the pom.xml file having the shortest path (closest to it repository's root path)
            pomFile = Arrays.stream(pomFiles).min(Comparator.comparingInt(f -> f.getAbsolutePath().length()));
            logger.info("Multiple pom.xml files found. Using " + pomFile.get());
        }

        if (pomFile.isEmpty()) {
            throw new FileNotFoundException("No file named pom.xml found in " + repoPath + ".");
        }

        return pomFile.get();
    }

}