package eu.fasten.core.maven.data;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public class DependencyTree {

    public MavenArtifact artifact;
    public List<DependencyTree> dependencies;

    public DependencyTree(MavenArtifact artifact, List<DependencyTree> dependencies) {
        this.artifact = artifact;
        this.dependencies = dependencies;
    }

    public JSONObject toJSON() {
        var json = new JSONObject();
        json.put("artifact", artifact.toCanonicalForm());
        if (dependencies != null) {
            var jsonDependencies = new JSONArray();
            for (var dep : dependencies) {
                jsonDependencies.put(dep.toJSON());
            }
            json.put("dependencies", jsonDependencies);
        } else {
            json.put("dependencies", new JSONArray());
        }
        return json;
    }
}
