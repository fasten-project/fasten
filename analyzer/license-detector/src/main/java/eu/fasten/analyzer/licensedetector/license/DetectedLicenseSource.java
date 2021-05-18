package eu.fasten.analyzer.licensedetector.license;

/**
 * Where a certain licenses has been retrieved from.
 */
public enum DetectedLicenseSource {

    LOCAL_POM("Local pom file"),
    MAVEN_CENTRAL("Maven central"),
    GITHUB("GitHub APIs");

    private final String description;

    DetectedLicenseSource(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return this.description;
    }
}
