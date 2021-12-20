/*
 * Copyright 2021 Delft University of Technology
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fasten.core.data.opal;

import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.utils.MavenUtilities;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Maven coordinate as g:a:v e.g. "com.google.guava:guava:jar:28.1-jre".
 */
public class MavenCoordinate {

    private LinkedList<String> mavenRepos;

    private final String groupID;
    private final String artifactID;
    private final String versionConstraint;

    private final String packaging;

    public LinkedList<String> getMavenRepos() {
        return mavenRepos;
    }

    public void setMavenRepos(List<String> mavenRepos) {
        this.mavenRepos = new LinkedList<>(mavenRepos);
    }

    public String getGroupID() {
        return groupID;
    }

    public String getArtifactID() {
        return artifactID;
    }

    public String getVersionConstraint() {
        return versionConstraint;
    }

    public String getPackaging() {
        return packaging;
    }

    /**
     * Construct MavenCoordinate form groupID, artifactID, and version.
     *
     * @param groupID    GroupID
     * @param artifactID ArtifactID
     * @param version    Version
     */
    public MavenCoordinate(final String groupID, final String artifactID, final String version,
                           final String packaging) {
        this.mavenRepos = MavenUtilities.getRepos();
        this.groupID = groupID;
        this.artifactID = artifactID;
        this.versionConstraint = version;
        this.packaging = packaging;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MavenCoordinate that = (MavenCoordinate) o;

        return this.getCoordinate().equals(that.getCoordinate());
    }

    @Override
    public int hashCode() {
        return this.getCoordinate().hashCode();
    }

    /**
     * Construct MavenCoordinate form json.
     *
     * @param kafkaConsumedJson json representation of Maven coordinate
     */
    public MavenCoordinate(final JSONObject kafkaConsumedJson) throws JSONException {
        this.mavenRepos = MavenUtilities.getRepos();
        this.groupID = kafkaConsumedJson.getString("groupId");
        this.artifactID = kafkaConsumedJson.getString("artifactId");
        this.versionConstraint = kafkaConsumedJson.getString("version");
        this.packaging = kafkaConsumedJson.optString("packagingType", "jar");
    }

    /**
     * Convert string to MavenCoordinate.
     *
     * @param coords String representation of a coordinate
     * @return MavenCoordinate
     */
    public static MavenCoordinate fromString(final String coords, final String packaging) {
        var coordinate = coords.split(Constants.mvnCoordinateSeparator);
        return new MavenCoordinate(coordinate[0], coordinate[1], coordinate[2], packaging);
    }

    public String getProduct() {
        return groupID + Constants.mvnCoordinateSeparator + artifactID;
    }

    public String getCoordinate() {
        return groupID + Constants.mvnCoordinateSeparator + artifactID
                + Constants.mvnCoordinateSeparator + versionConstraint;
    }

    /**
     * Convert to URL.
     *
     * @return URL
     */
    public String toURL(String repo) {
        return repo + this.groupID.replace('.', '/') + "/" + this.artifactID
                + "/" + this.versionConstraint;
    }

    /**
     * Convert to product URL.
     *
     * @return product URL
     */
    public String toProductUrl(String repo, String extension) {
        return this.toURL(repo) + "/" + this.artifactID + "-" + this.versionConstraint
                + "." + extension;
    }
    
    @Override
    public String toString() {
    	return getCoordinate();
    }
}