package eu.fasten.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FastenCURITest {

    @Test
    public void testCreation() {
        var fastenCURI = new FastenCURI("fasten://libc6-dev/libc.a;C/fprintf()");
        assertEquals("fasten", fastenCURI.getScheme());
        assertNull(fastenCURI.getForge());
        assertEquals("libc6-dev", fastenCURI.getProduct());
        assertNull(fastenCURI.getVersion());
        assertEquals("libc.a", fastenCURI.getBinary());
        assertEquals("C", fastenCURI.getNamespace());
        assertTrue(fastenCURI.isPublic());
        assertNull(fastenCURI.getFilename());
        assertEquals("fprintf", fastenCURI.getName());
        assertTrue(fastenCURI.isFunction());

        fastenCURI = new FastenCURI("fasten:///libc.so;C/strchr()");
        assertEquals("fasten", fastenCURI.getScheme());
        assertNull(fastenCURI.getForge());
        assertNull(fastenCURI.getProduct());
        assertNull(fastenCURI.getVersion());
        assertEquals("libc.so", fastenCURI.getBinary());
        assertEquals("C", fastenCURI.getNamespace());
        assertTrue(fastenCURI.isPublic());
        assertNull(fastenCURI.getFilename());
        assertEquals("strchr", fastenCURI.getName());
        assertTrue(fastenCURI.isFunction());

        fastenCURI = new FastenCURI("fasten://debian!recsplit$0.1/;%2Ftmp%2F/dir;var");
        assertEquals("fasten", fastenCURI.getScheme());
        assertEquals(Constants.debianForge, fastenCURI.getForge());
        assertEquals("recsplit", fastenCURI.getProduct());
        assertEquals("0.1", fastenCURI.getVersion());
        assertNull(fastenCURI.getBinary());
        assertEquals("/tmp/", fastenCURI.getNamespace());
        assertFalse(fastenCURI.isPublic());
        assertEquals("dir", fastenCURI.getFilename());
        assertEquals("var", fastenCURI.getName());
        assertFalse(fastenCURI.isFunction());

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenCURI("fasten://a/b/c");
        });
    }

    @Test
    public void testCreationNoEntity() {
        assertEquals("recsplit", new FastenCURI("fasten://recsplit").getProduct());
    }

    @Test
    public void testCreationError() {
        // Namespace must contain semicolon
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenCURI("fasten://libc6-dev/C/fprintf()");
        });
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenCURI("fasten://recsplit/;C/copy(");
        });
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenCURI("fasten://recsplit/;C/copy(]");
        });
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenCURI("fasten://recsplit/;C/copy()X");
        });
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenCURI("fasten://recsplit/;C/");
        });
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenCURI("fasten://recsplit/;C");
        });
    }

    @Test
    public void testCreateFromComponents() {
        FastenCURI u;

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            FastenCURI.create(null, null, null, "Foo", null, "Bar", "dummy", false);
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            FastenCURI.create(null, null, null, null, null, "Bar", "dummy", false);
        });

        u = FastenCURI.create(null, null, null, null, "C", "Bar", "dummy", false);
        assertEquals("fasten:/;C/Bar;dummy", u.toString());

        u = FastenCURI.create(null, null, null, null, "%2Ffoo%2F", "Bar", "dummy", false);
        assertEquals("fasten:/;%2Ffoo%2F/Bar;dummy", u.toString());

        u = FastenCURI.create(null, null, null, null, "C", "Bar", "dummy", true);
        assertEquals("fasten:/;C/Bar;dummy()", u.toString());

        u = FastenCURI.create(null, null, null, null, "C", "Bar", "dummy", false);
        assertEquals("fasten:/;C/Bar;dummy", u.toString());
    }

    @Test
    public void testRelativizeResolve() {
        FastenCURI u, v;

        u = FastenCURI.create("fasten://mvn$a/;C/Bar");
        v = FastenCURI.create("fasten://mvn$a/;C/Bar");
        assertEquals(u, v.resolve(v.relativize(u)));
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenCURI.create("fasten://mvn$b/;C/Bar");
        v = FastenCURI.create("fasten://mvn$a/;C/Bar");
        assertEquals(u, v.resolve(v.relativize(u)));
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenCURI.create("//mvn$b/;C/Bar");
        v = FastenCURI.create("fasten://mvn$a/;C/Bar");
        assertEquals("fasten:" + u, v.resolve(v.relativize(u)).toString());
        assertEquals("fasten:" + v.relativize(u), v.relativize(v.resolve(u)).toString());

        u = FastenCURI.create("fasten://mvn$b/;C/Bar");
        v = FastenCURI.create("//mvn$a/;C/Bar");
        assertEquals(u, v.resolve(v.relativize(u)));
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenCURI.create("fasten://mvn$a/;C/Bar");
        v = FastenCURI.create("fasten://mvn$a/;C/Dummy");
        assertEquals(u, v.resolve(v.relativize(u)));
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenCURI.create("/;C/Bar");
        v = FastenCURI.create("fasten://mvn$a/;C/Bar");
        assertEquals("fasten://mvn$a" + u, v.resolve(v.relativize(u)).toString());
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenCURI.create("Bar");
        v = FastenCURI.create("fasten://mvn$a/;C/Bar");
        assertEquals("fasten://mvn$a/;C/" + u, v.resolve(v.relativize(u)).toString());
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));
    }
}
