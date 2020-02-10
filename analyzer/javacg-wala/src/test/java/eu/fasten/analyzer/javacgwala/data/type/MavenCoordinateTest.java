package eu.fasten.analyzer.javacgwala.data.type;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MavenCoordinateTest {

    private MavenCoordinate mavenCoordinate;

    @BeforeEach
    public void setUp() {
        mavenCoordinate = new MavenCoordinate("group", "artifact", "1.0");
    }

    @Test
    void of() {
        var coordinate = MavenCoordinate.of("group:artifact:1.0");
        assertEquals(mavenCoordinate, coordinate);
    }

    @Test
    void getCanonicalForm() {
        var actualCanonicalForm = "group:artifact:1.0";
        assertEquals(actualCanonicalForm, mavenCoordinate.getCanonicalForm());
    }

    @Test
    void testToString() {
        var actualString = "MavenCoordinate(group,artifact,1.0)";
        assertEquals(actualString, mavenCoordinate.toString());
    }

    @Test
    void testEquals() {
        var sameCoordinate = new MavenCoordinate("group", "artifact", "1.0");
        var diffCoordinate1 = new MavenCoordinate("diff", "artifact", "1.0");
        var diffCoordinate2 = new MavenCoordinate("group", "diff", "1.0");
        var diffCoordinate3 = new MavenCoordinate("group", "artifact", "diff");

        assertEquals(mavenCoordinate, sameCoordinate);
        assertNotEquals(mavenCoordinate, diffCoordinate1);
        assertNotEquals(mavenCoordinate, diffCoordinate2);
        assertNotEquals(mavenCoordinate, diffCoordinate3);
    }

    @Test
    void getSegments() {
        var segArray = mavenCoordinate.getSegments();

        assertEquals("group", segArray[0]);
        assertEquals("artifact", segArray[1]);
        assertEquals("1.0", segArray[2]);
    }

    @Test
    void getNamespaceDelim() {
        assertEquals(":", mavenCoordinate.getNamespaceDelim());
    }
}