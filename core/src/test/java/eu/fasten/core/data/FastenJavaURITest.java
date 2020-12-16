package eu.fasten.core.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FastenJavaURITest {

    @Test
    public void testCreationEmptyArgs() {
        var fastenJavaURI = new FastenJavaURI("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph.copy()BVGraph");
        assertEquals("fasten", fastenJavaURI.getScheme());
        assertNull(fastenJavaURI.getForge());
        assertEquals("webgraph.jar", fastenJavaURI.getProduct());
        assertNull(fastenJavaURI.getVersion());
        assertEquals("it.unimi.dsi.webgraph", fastenJavaURI.getNamespace());
        assertEquals("BVGraph", fastenJavaURI.getClassName());
        assertEquals("copy", fastenJavaURI.getEntityName());
        assertArrayEquals(new FastenJavaURI[0], fastenJavaURI.getArgs());
        //assertEquals(FastenJavaURI.create("BVGraph"), fastenJavaURI.getReturnType());
        //assertEquals(FastenJavaURI.create("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph"), fastenJavaURI.resolve(fastenJavaURI.getReturnType()));

        fastenJavaURI = new FastenJavaURI("fasten://mvn!webgraph.jar$3.6.2/it.unimi.dsi.webgraph/BVGraph.copy()BVGraph");
        assertEquals("fasten", fastenJavaURI.getScheme());
        assertEquals("mvn", fastenJavaURI.getForge());
        assertEquals("webgraph.jar", fastenJavaURI.getProduct());
        assertEquals("3.6.2", fastenJavaURI.getVersion());
        assertEquals("it.unimi.dsi.webgraph", fastenJavaURI.getNamespace());
        assertEquals("BVGraph", fastenJavaURI.getClassName());
        assertEquals("copy", fastenJavaURI.getEntityName());
        assertArrayEquals(new FastenJavaURI[0], fastenJavaURI.getArgs());
        //assertEquals(FastenJavaURI.create("BVGraph"), fastenJavaURI.getReturnType());
        //assertEquals(FastenJavaURI.create("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph"), fastenJavaURI.resolve(fastenJavaURI.getReturnType()));
    }

    @Test
    public void testCreationOneArg() {
        final var fastenJavaURI = new FastenJavaURI("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph.successors(%2Fjava.primitive%2Fint)LazyIntIterator");
        assertEquals("fasten", fastenJavaURI.getScheme());
        assertEquals("webgraph.jar", fastenJavaURI.getProduct());
        assertNull(fastenJavaURI.getForge());
        assertEquals("it.unimi.dsi.webgraph", fastenJavaURI.getNamespace());
        assertEquals("BVGraph", fastenJavaURI.getClassName());
        assertEquals("successors", fastenJavaURI.getEntityName());
        //assertArrayEquals(new FastenJavaURI[] { FastenJavaURI.create("LazyIntIterator")}, fastenJavaURI.getArgs());
        //assertEquals(FastenJavaURI.create("BVGraph"), fastenJavaURI.getReturnType());
        //assertEquals(FastenJavaURI.create("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph"), fastenJavaURI.resolve(fastenJavaURI.getReturnType()));
    }

    @Test
    public void testCreationNoEntity() {
        assertEquals("webgraph.jar", new FastenJavaURI("fasten://webgraph.jar").getProduct());
    }

    @Test
    public void testCreationError() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenJavaURI("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph.copy(");
        });
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenJavaURI("fasten://webgraph.jar/it.unimi.dsi.webgraph/");
        });
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenJavaURI("fasten://webgraph.jar/it.unimi.dsi.webgraph");
        });
    }

    @Test
    public void testExample1() {
        final var fastenJavaURI = new FastenJavaURI("fasten://xerces.xercesImpl$2.6.2/org.apache.html.dom/HTMLUListElementImpl.%3Cinit%3E(HTMLDocumentImpl,%2F%2Fjdk%2Fjava.lang%2FString)HTMLUListElementImpl");
        assertEquals("fasten", fastenJavaURI.getScheme());
        assertEquals("xerces.xercesImpl", fastenJavaURI.getProduct());
        assertEquals("2.6.2", fastenJavaURI.getVersion());
        assertNull(fastenJavaURI.getForge());
        assertEquals("org.apache.html.dom", fastenJavaURI.getNamespace());
        assertEquals("HTMLUListElementImpl", fastenJavaURI.getClassName());
        assertEquals("<init>", fastenJavaURI.getEntityName());
		assertEquals("HTMLUListElementImpl", fastenJavaURI.getReturnType().toString());
        final FastenJavaURI[] args = fastenJavaURI.getArgs();
        assertEquals(2, args.length);
		assertEquals("HTMLDocumentImpl", args[0].toString());
		assertEquals("//jdk/java.lang/String", args[1].toString());
    }

    @Test
    public void testExample2() {
        final var fastenJavaURI = new FastenJavaURI("fasten://com.faster.jackson.core.jackson-core/com.fasterxml.jackson.core.json.async/NonBlockingJsonParserBase._findName(%2F%2Fjdk%2Fjava.primitive%2Fint,%2F%2Fjdk%2Fjava.primitive%2Fint)%2F%2Fjdk%2Fjava.lang%2FString");
        assertEquals("fasten", fastenJavaURI.getScheme());
        assertEquals("com.faster.jackson.core.jackson-core", fastenJavaURI.getProduct());
        assertNull(fastenJavaURI.getVersion());
        assertNull(fastenJavaURI.getForge());
        assertEquals("com.fasterxml.jackson.core.json.async", fastenJavaURI.getNamespace());
        assertEquals("NonBlockingJsonParserBase", fastenJavaURI.getClassName());
        assertEquals("_findName", fastenJavaURI.getEntityName());
		assertEquals("//jdk/java.lang/String", fastenJavaURI.getReturnType().toString());
        final FastenJavaURI[] args = fastenJavaURI.getArgs();
        assertEquals(2, args.length);
		assertEquals("//jdk/java.primitive/int", args[0].toString());
		assertEquals("//jdk/java.primitive/int", args[1].toString());
    }

    @Test
    public void testNamespace() {
        final var uri = new FastenJavaURI("/my.package/A.f(A)B");
        assertEquals(FastenJavaURI.create("/my.package/A.f(A)B"), uri.getArgs()[0].resolve(uri));
    }

    @Test
    public void testCanonical() {
        FastenJavaURI uri = new FastenJavaURI("fasten://mvn$a/foo/Bar.jam(fasten%3A%2F%2Fmvn$a%2Ffoo%2FBar)%2Fbar%2FBar");
        assertEquals(FastenJavaURI.create("fasten://mvn$a/foo/Bar"), uri.getArgs()[0]);
        assertEquals(FastenJavaURI.create("/bar/Bar"), uri.getReturnType());
        assertEquals(FastenJavaURI.create("fasten://mvn$a/foo/Bar.jam(Bar)%2Fbar%2FBar"), uri.canonicalize());

        uri = new FastenJavaURI("fasten://mvn$a/foo/Bar.jam(%2F%2Fmvn$a%2Ffoo%2FBar)%2Fbar%2FBar");
        assertEquals(FastenJavaURI.create("fasten://mvn$a/foo/Bar.jam(Bar)%2Fbar%2FBar"), uri.canonicalize());

        uri = new FastenJavaURI("fasten://mvn$a/foo/Bar.jam(%2Ffoo%2FBar)%2Fbar%2FBar");
        assertEquals(FastenJavaURI.create("fasten://mvn$a/foo/Bar.jam(Bar)%2Fbar%2FBar"), uri.canonicalize());

        uri = new FastenJavaURI("fasten://mvn$a/foo/Bar.jam(%2Ffoo%2FBar)%2Ffoo%2FBar");
        assertEquals(FastenJavaURI.create("fasten://mvn$a/foo/Bar.jam(Bar)Bar"), uri.canonicalize());
    }


    @Test
    public void testRelativize() {
        FastenJavaURI u;

        Assertions.assertThrows(IllegalStateException.class, () -> {
            final FastenJavaURI v = FastenJavaURI.create("/foo/Bar");
            assertEquals(v, FastenJavaURI.create("Bar").relativize(v));
        });

        u = FastenJavaURI.create("fasten://mvn$a/foo/Bar");
        assertEquals(FastenJavaURI.create("/foo/Bar"), FastenJavaURI.create("fasten://mvn$a/nope/Bar").relativize(u));

        u = FastenJavaURI.create("fasten://mvn$b/foo/Bar");
        assertEquals(u, FastenJavaURI.create("fasten://mvn$a/foo/Bar").relativize(u));


        u = FastenJavaURI.create("//mvn$b/foo/Bar");
        assertEquals(u, FastenJavaURI.create("fasten://mvn$a/foo/Bar").relativize(u));

        u = FastenJavaURI.create("fasten://mvn$b/foo/Bar");
        assertEquals(u, FastenJavaURI.create("//mvn$a/foo/Bar").relativize(u));

        Assertions.assertThrows(IllegalStateException.class, () -> {
            final FastenJavaURI v = FastenJavaURI.create("fasten://mvn$b/foo/Bar");
            assertEquals(v, FastenJavaURI.create("Bar").relativize(v));
        });

        u = FastenJavaURI.create("fasten://mvn$a/foo/Bar");
        assertEquals(FastenJavaURI.create("Bar"), FastenJavaURI.create("fasten://mvn$a/foo/Dummy").relativize(u));

        u = FastenJavaURI.create("/foo/Bar");
        assertEquals(FastenJavaURI.create("Bar"), FastenJavaURI.create("fasten://mvn$a/foo/Bar").relativize(u));

        u = FastenJavaURI.create("Bar");
        assertEquals(FastenJavaURI.create("Bar"), FastenJavaURI.create("fasten://mvn$a/foo/Bar").relativize(u));
    }

    @Test
    public void testRelativizeResolve() {
        FastenJavaURI u, v;

        u = FastenJavaURI.create("fasten://mvn$a/foo/Bar");
        v = FastenJavaURI.create("fasten://mvn$a/nope/Bar");
        assertEquals(u, v.resolve(v.relativize(u)));
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenJavaURI.create("fasten://mvn$b/foo/Bar");
        v = FastenJavaURI.create("fasten://mvn$a/foo/Bar");
        assertEquals(u, v.resolve(v.relativize(u)));
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenJavaURI.create("//mvn$b/foo/Bar");
        v = FastenJavaURI.create("fasten://mvn$a/foo/Bar");
        assertEquals("fasten:" + u, v.resolve(v.relativize(u)).toString());
        assertEquals("fasten:" + v.relativize(u), v.relativize(v.resolve(u)).toString());

        u = FastenJavaURI.create("fasten://mvn$b/foo/Bar");
        v = FastenJavaURI.create("//mvn$a/foo/Bar");
        assertEquals(u, v.resolve(v.relativize(u)));
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenJavaURI.create("fasten://mvn$a/foo/Bar");
        v = FastenJavaURI.create("fasten://mvn$a/foo/Dummy");
        assertEquals(u, v.resolve(v.relativize(u)));
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenJavaURI.create("/foo/Bar");
        v = FastenJavaURI.create("fasten://mvn$a/foo/Bar");
        assertEquals("fasten://mvn$a" + u, v.resolve(v.relativize(u)).toString());
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenJavaURI.create("Bar");
        v = FastenJavaURI.create("fasten://mvn$a/foo/Bar");
        assertEquals("fasten://mvn$a/foo/" + u, v.resolve(v.relativize(u)).toString());
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));
    }


    @Test
    public void testCreateFromComponents() {
        FastenJavaURI u;

        u = FastenJavaURI.create(null, null, null, "foo", "Bar", "dummy", new FastenJavaURI[]{}, null);
        assertEquals("fasten:/foo/Bar.dummy", u.toString());

        u = FastenJavaURI.create(null, null, null, "foo", "Bar", "dummy", null, null);
        assertEquals("fasten:/foo/Bar.dummy", u.toString());

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            FastenJavaURI.create(null, null, null, "foo", "Bar", "dummy", new FastenJavaURI[]{FastenJavaURI.create("A")}, null);
        });

        u = FastenJavaURI.create(null, null, null, "foo", "Bar", "dummy", new FastenJavaURI[]{FastenJavaURI.create("A")}, FastenJavaURI.create("B"));
        assertEquals("fasten:/foo/Bar.dummy(A)B", u.toString());

        u = FastenJavaURI.create(null, null, null, "foo", "Bar", "dummy", new FastenJavaURI[]{FastenJavaURI.create("X"), FastenJavaURI.create("Y")}, FastenJavaURI.create("B"));
        assertEquals("fasten:/foo/Bar.dummy(X,Y)B", u.toString());

        u = FastenJavaURI.create(null, null, null, "foo", "Bar", "dummy", null, FastenJavaURI.create("B"));
        assertEquals("fasten:/foo/Bar.dummy()B", u.toString());

    }

    @Test
    public void testPctEncode() {
        assertEquals("a", FastenJavaURI.pctEncodeArg("a"));
        assertEquals("-", FastenJavaURI.pctEncodeArg("-"));
        assertEquals("µ", FastenJavaURI.pctEncodeArg("µ"));
        assertEquals("ò", FastenJavaURI.pctEncodeArg("ò"));
        assertEquals("%2F", FastenJavaURI.pctEncodeArg("/"));
    }

    @Test
    public void testExtendedCharactersInTypes() {
        final FastenJavaURI uri = new FastenJavaURI("/com.google.common.primitives/Ints$IntArrayAsList.%3cinit%3e(%2Fjava.lang%2F%2FInteger)%2Fjava.lang%2Fvoid");
        assertTrue(uri.getPath().contains("<init>"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            final FastenJavaURI uri2 = new FastenJavaURI("/com.google.common.primitives/Ints$IntArrayAsList.(init)(%2Fjava.lang%2F%2FInteger)%2Fjava.lang%2Fvoid");
        });
    }

    @Test
    public void testAttributeNames() {
        final FastenJavaURI uri = FastenJavaURI.create("fasten://mvn$a/foo/Bar.test");
        assertEquals("Bar", uri.getClassName());
        assertEquals("test", uri.getEntityName());
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            final FastenJavaURI uri2 = FastenJavaURI.create("fasten://mvn$a/foo/Bar.te.st");
        });
    }

    @Test
    public void testClassNames() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            final FastenJavaURI uri2 = FastenJavaURI.create("fasten://mvn$a/foo/Ba)r");
        });
    }
}
