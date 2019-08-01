package eu.fasten.analyzer.serverContact;

import java.util.StringTokenizer;

public class Artifact {

    private long artifactNO;
    private String groupId;
    private String artifactId;
    private String version;
    private String date;

    public Artifact(){

    }
    public Artifact(long artifactNO, String firstName, String lastName, String version, String date) {
        this.artifactNO = artifactNO;
        this.groupId = firstName;
        this.artifactId = lastName;
        this.version = version;
        this.date = date;
    }

    public void parseString(String csvStr){
        StringTokenizer st = new StringTokenizer(csvStr,",");
        artifactNO = Integer.parseInt(st.nextToken());
        groupId = st.nextToken();
        artifactId = st.nextToken();
        version = st.nextToken();
        date = st.nextToken();
    }


    public long getArtifactNO() {
        return artifactNO;
    }

    public void setArtifactNO(long artifactNO) {
        this.artifactNO = artifactNO;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "Artifact{" +
                "artifactId=" + artifactId +
                ", groupId='" + groupId + '\'' +
                ", lastName='" + artifactId + '\'' +
                ", version='" + version + '\'' +
                ", date='" + date + '\'' +
                '}';
    }
}