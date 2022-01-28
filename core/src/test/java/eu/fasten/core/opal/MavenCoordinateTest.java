package eu.fasten.core.opal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import eu.fasten.core.data.opal.MavenCoordinate;

public class MavenCoordinateTest {

    @Test
    void constructorTest() {
        MavenCoordinate coordinate1 = new MavenCoordinate("group", "artifact", "version", "jar");

        assertEquals("group", coordinate1.getGroupID());

        assertEquals("artifact", coordinate1.getArtifactID());

        assertEquals("version", coordinate1.getVersionConstraint());

        assertEquals(new ArrayList<>(Collections
                        .singletonList("https://repo.maven.apache.org/maven2/")),
                coordinate1.getMavenRepos());
    }

    @Test
    void fromString() {
        var coordinate = MavenCoordinate.fromString("GroupID:ArtifactID:Version", "jar");

        assertEquals("GroupID", coordinate.getGroupID());
        assertEquals("ArtifactID", coordinate.getArtifactID());
        assertEquals("Version", coordinate.getVersionConstraint());
    }

    @Test
    void getProduct() {
        var coordinate = MavenCoordinate.fromString("GroupID:ArtifactID:Version", "jar");
        assertEquals("GroupID:ArtifactID", coordinate.getProduct());
    }

    @Test
    void getCoordinate() {
        var coordinate = MavenCoordinate.fromString("GroupID:ArtifactID:Version", "jar");
        assertEquals("GroupID:ArtifactID:Version", coordinate.getCoordinate());
    }

    @Test
    void toURL() {
        var mc = new MavenCoordinate("g1.g2", "aid", "v", "jar");
        assertEquals("https://repo.maven.apache.org/maven2/g1/g2/aid/v/aid-v.jar", mc.toProductUrl());
    }
}