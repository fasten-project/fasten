package eu.fasten.analyzer.licensedetector.license;

/**
 * Where a certain licenses has been retrieved from.
 */
public enum DetectedLicenseSource {
    LOCAL_POM, // from the repository's local `pom.xml` file
    MAVEN_CENTRAL, // from Maven Central
    GITHUB // from GitHub APIs
}
