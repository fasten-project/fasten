package eu.fasten.analyzer.licensedetector.license;

/**
 * Where a certain licenses has been retrieved from.
 */

//here probably goes from which file the  outbound license is retrieved.
public enum DetectedLicenseSource {

    DEBIAN_API("Debian API"),
    //MAVEN_CENTRAL("Maven central"),
    //GITHUB("GitHub APIs");

    private final String description;

    DetectedLicenseSource(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return this.description;
    }
}
