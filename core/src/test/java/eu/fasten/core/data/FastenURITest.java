package eu.fasten.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URISyntaxException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FastenURITest {

    @Test
    public void testCreation() throws URISyntaxException {
        var fastenURI = new FastenURI("fasten://a!b$c/∂∂∂/πππ");
        assertEquals("fasten", fastenURI.getScheme());
        assertEquals("a", fastenURI.getForge());
        assertEquals("b", fastenURI.getProduct());
        assertEquals("c", fastenURI.getVersion());
        assertEquals("∂∂∂", fastenURI.getNamespace());
        assertEquals("πππ", fastenURI.getEntity());

        fastenURI = new FastenURI("fasten://b$c/∂∂∂/πππ");
        assertEquals("fasten", fastenURI.getScheme());
        assertNull(fastenURI.getForge());
        assertEquals("b", fastenURI.getProduct());
        assertEquals("c", fastenURI.getVersion());
        assertEquals("∂∂∂", fastenURI.getNamespace());
        assertEquals("πππ", fastenURI.getEntity());

        fastenURI = new FastenURI("fasten://b/∂∂∂/πππ");
        assertEquals("fasten", fastenURI.getScheme());
        assertNull(fastenURI.getForge());
        assertEquals("b", fastenURI.getProduct());
        assertNull(fastenURI.getVersion());
        assertEquals("∂∂∂", fastenURI.getNamespace());
        assertEquals("πππ", fastenURI.getEntity());

        fastenURI = new FastenURI("fasten://it.unimi.dsi.fastutil");
        assertEquals("fasten", fastenURI.getScheme());
        assertNull(fastenURI.getForge());
        assertNull(fastenURI.getVersion());
        assertEquals("it.unimi.dsi.fastutil", fastenURI.getProduct());
        assertNull(fastenURI.getNamespace());
        assertNull(fastenURI.getEntity());


        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenURI("fasten://a!$c/∂∂∂/πππ");
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenURI("fasten://$c/∂∂∂/πππ");
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenURI("fasten://a!/∂∂∂/πππ");
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenURI("fasten://a!b!c/∂∂∂/πππ");
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenURI("fasten://a$b!c/∂∂∂/πππ");
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenURI("fasten://a!b$c$d/∂∂∂/πππ");
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenURI("fasten://a!b$c/∂∂∂/πππ:pippo");
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenURI("fasten://a!b$c//πππ:pippo");
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenURI("unfasten://webgraph.jar/foo");
        });

        fastenURI = new FastenURI("fasten://webgraph.jar");
        assertNull(fastenURI.getPath());
        assertNull(fastenURI.getRawPath());
        assertEquals("", fastenURI.uri.getPath());

        fastenURI = new FastenURI("fasten://webgraph.jar/p/a");
        assertEquals("/p/a", fastenURI.getPath());
        assertEquals("/p/a", fastenURI.getRawPath());
        assertEquals("/p/a", fastenURI.uri.getPath());

        fastenURI = new FastenURI("fasten://b/∂∂∂/€");
        assertEquals("fasten", fastenURI.getScheme());
        assertNull(fastenURI.getForge());
        assertEquals("b", fastenURI.getProduct());
        assertNull(fastenURI.getVersion());
        assertEquals("∂∂∂", fastenURI.getNamespace());
        assertEquals("€", fastenURI.getEntity());

    }

    @Test
    public void testCreateFromComponents() {
        FastenURI u;

        u = FastenURI.create(null, null, null, "foo", "Bar");
        assertEquals("fasten:/foo/Bar", u.toString());

        u = FastenURI.create("mvn", "prod", null, "foo", "Bar");
        assertEquals("fasten://mvn!prod/foo/Bar", u.toString());

        u = FastenURI.create("mvn!prod", "foo", "Bar");
        assertEquals("fasten://mvn!prod/foo/Bar", u.toString());

		u = FastenURI.create(null, null, null, "/foo/Bar");
		assertEquals("fasten:/foo/Bar", u.toString());

		u = FastenURI.create("mvn", "prod", null, "/foo/Bar");
		assertEquals("fasten://mvn!prod/foo/Bar", u.toString());
    }

    @Test
    public void testRawNonRow() throws URISyntaxException {
        final var fastenURI = new FastenURI("fasten://a%2F!b%2F$c%2F/∂∂∂%2F/πππ%2F");
        assertEquals("fasten", fastenURI.getScheme());
        assertEquals("a/", fastenURI.getForge());
        assertEquals("b/", fastenURI.getProduct());
        assertEquals("c/", fastenURI.getVersion());
        assertEquals("∂∂∂/", fastenURI.getNamespace());
        assertEquals("πππ/", fastenURI.getEntity());
        assertEquals("a%2F", fastenURI.getRawForge());
        assertEquals("b%2F", fastenURI.getRawProduct());
        assertEquals("c%2F", fastenURI.getRawVersion());
        assertEquals("∂∂∂%2F", fastenURI.getRawNamespace());
        assertEquals("πππ%2F", fastenURI.getRawEntity());
    }

    @Test
    public void testNoBar() throws URISyntaxException {
        final FastenURI fastenURI = new FastenURI("fasten:/a/b");
        assertEquals("/a/b", fastenURI.getPath());

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenURI("a/b");
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenURI("fasten:a/b");
        });
    }


    @Test
    public void testEmptyForgeProductRevision() throws URISyntaxException {
        final FastenURI fastenURI = new FastenURI("fasten:///a/b");
        assertEquals(null, fastenURI.getForge());
        assertEquals(null, fastenURI.getProduct());
        assertEquals(null, fastenURI.getVersion());
    }

    @Test
    public void testDecodeEmptyString() {
        assertEquals(0, FastenURI.decode("").length());
    }

    @Test
    public void testRelativize() {
        FastenURI u;

        Assertions.assertThrows(IllegalStateException.class, () -> {
            final FastenURI v = FastenURI.create("fasten://mvn$a/foo/Bar");
            assertEquals(FastenURI.create("/foo/Bar"), FastenURI.create("Bar").relativize(v));
        });

        u = FastenURI.create("fasten://mvn$a/foo/Bar");
        assertEquals(FastenURI.create("/foo/Bar"), FastenURI.create("fasten://mvn$a/nope/Bar").relativize(u));

        u = FastenURI.create("fasten://mvn$b/foo/Bar");
        assertEquals(u, FastenURI.create("fasten://mvn$a/foo/Bar").relativize(u));

        u = FastenURI.create("//mvn$b/foo/Bar");
        assertEquals(u, FastenURI.create("fasten://mvn$a/foo/Bar").relativize(u));

        u = FastenURI.create("fasten://mvn$b/foo/Bar");
        assertEquals(u, FastenURI.create("//mvn$a/foo/Bar").relativize(u));

        u = FastenURI.create("fasten://mvn$a/foo/Bar");
        assertEquals(FastenURI.create("Bar"), FastenURI.create("fasten://mvn$a/foo/Dummy").relativize(u));

        u = FastenURI.create("/foo/Bar");
        assertEquals(FastenURI.create("Bar"), FastenURI.create("fasten://mvn$a/foo/Bar").relativize(u));

        u = FastenURI.create("Bar");
        assertEquals(FastenURI.create("Bar"), FastenURI.create("fasten://mvn$a/foo/Bar").relativize(u));
    }


    @Test
    public void testRelativizeResolve() {
        FastenURI u, v;

        u = FastenURI.create("fasten://mvn$a/foo/Bar");
        v = FastenURI.create("fasten://mvn$a/nope/Bar");
        assertEquals(u, v.resolve(v.relativize(u)));
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenURI.create("fasten://mvn$b/foo/Bar");
        v = FastenURI.create("fasten://mvn$a/foo/Bar");
        assertEquals(u, v.resolve(v.relativize(u)));
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenURI.create("//mvn$b/foo/Bar");
        v = FastenURI.create("fasten://mvn$a/foo/Bar");
        assertEquals("fasten:" + u, v.resolve(v.relativize(u)).toString());
        assertEquals("fasten:" + v.relativize(u), v.relativize(v.resolve(u)).toString());

        u = FastenURI.create("fasten://mvn$b/foo/Bar");
        v = FastenURI.create("//mvn$a/foo/Bar");
        assertEquals(u, v.resolve(v.relativize(u)));
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenURI.create("fasten://mvn$a/foo/Bar");
        v = FastenURI.create("fasten://mvn$a/foo/Dummy");
        assertEquals(u, v.resolve(v.relativize(u)));
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenURI.create("/foo/Bar");
        v = FastenURI.create("fasten://mvn$a/foo/Bar");
        assertEquals("fasten://mvn$a" + u, v.resolve(v.relativize(u)).toString());
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenURI.create("Bar");
        v = FastenURI.create("fasten://mvn$a/foo/Bar");
        assertEquals("fasten://mvn$a/foo/" + u, v.resolve(v.relativize(u)).toString());
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));
    }
}
