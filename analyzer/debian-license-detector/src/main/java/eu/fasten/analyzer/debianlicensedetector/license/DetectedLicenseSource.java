package eu.fasten.analyzer.debianlicensedetector.license;

/**
 * Where a certain licenses has been retrieved from.
 */
public enum DetectedLicenseSource {

    //LOCAL_POM("Local pom file"),
    DEBIAN_PACKAGES("Debian packages"),
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