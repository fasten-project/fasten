package eu.fasten.analyzer.licensedetector.license;

import eu.fasten.core.maven.data.Revision;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * A license detected by the license detector plugin.
 */
public class DetectedLicense {

    /**
     * License name.
     */
    protected String name; // FIXME Use SPDX IDs

    /**
     * Where does the license come from.
     */
    protected DetectedLicenseSource source;

    /**
     * The Maven coordinate this detected license belongs to.
     * Might be `null` during tests.
     */
    @Nullable
    protected Revision coordinate;

    public DetectedLicense(String name, DetectedLicenseSource source, @Nullable Revision coordinate) {
        this.name = name;
        this.source = source;
        this.coordinate = coordinate;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, source);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof DetectedLicense))
            return false;
        DetectedLicense other = (DetectedLicense) o;
        return this.name.compareToIgnoreCase(other.name) == 0 && this.source == other.source;
    }

    @Override
    public String toString() {
        return "DetectedLicense{" +
                "name='" + name + '\'' +
                ", source=" + source +
                '}';
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DetectedLicenseSource getSource() {
        return source;
    }

    public void setSource(DetectedLicenseSource source) {
        this.source = source;
    }

    @Nullable
    public Revision getCoordinate() {
        return coordinate;
    }

    public void setCoordinate(@Nullable Revision coordinate) {
        this.coordinate = coordinate;
    }
}
