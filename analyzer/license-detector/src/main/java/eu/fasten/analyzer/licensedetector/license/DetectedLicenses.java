package eu.fasten.analyzer.licensedetector.license;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DetectedLicenses {

    protected Set<DetectedLicense> outbound;
    protected Set<DetectedLicense> dependencies;

    public DetectedLicenses() {
        this.outbound = new HashSet<>(Collections.emptySet());
        this.dependencies = new HashSet<>(Collections.emptySet());
    }

    public DetectedLicenses(Set<DetectedLicense> outbound, Set<DetectedLicense> dependencies) {
        this.outbound = outbound;
        this.dependencies = dependencies;
    }

    public Set<DetectedLicense> getOutbound() {
        return outbound;
    }

    public void setOutbound(Set<DetectedLicense> outbound) {
        this.outbound = outbound;
    }

    public Set<DetectedLicense> getDependencies() {
        return dependencies;
    }

    public void setDependencies(Set<DetectedLicense> dependencies) {
        this.dependencies = dependencies;
    }
}
