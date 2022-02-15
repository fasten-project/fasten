package eu.fasten.analyzer.licensedetector.license;

import org.json.JSONArray;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DetectedLicenses {

    protected Set<DetectedLicense> outbound = new HashSet<>(Collections.emptySet());
    protected JSONArray files = new JSONArray();

    public Set<DetectedLicense> getOutbound() {
        return outbound;
    }

    public void setOutbound(Set<DetectedLicense> outbound) {
        this.outbound = outbound;
    }

    public JSONArray getFiles() {
        return files;
    }

    public void setFiles(JSONArray files) {
        this.files = files;
    }

    public void addFiles(JSONArray files) {
        files.forEach(file -> this.files.put(file));
    }
}
