package eu.fasten.core.maven.data;

public interface MavenArtifact {

    public String getGroupId();

    public String getArtifactId();

    public String getVersion();

    public String toCanonicalForm();

}
