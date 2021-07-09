package eu.fasten.core.data.metadatadb.license;

import org.json.JSONArray;
import org.json.JSONObject;

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

    /**
     * Generates a JSON snippet containing outbound licenses only.
     * E.g.: '{"outbound": [{"name": "apache-2.0", source: "LOCAL_POM"}]}'.
     *
     * @return a JSON snippet containing outbound licenses only.
     */
    public String generateOutboundJson() {
        JSONObject outboundObject = new JSONObject();
        JSONArray licensesArray = new JSONArray();
        outbound.forEach(l -> licensesArray.put(
                new JSONObject().put("name", l.getName()).put("source", l.getSource())
        ));
        outboundObject.put("outbound", licensesArray);
        return outboundObject.toString().strip();
    }
}
