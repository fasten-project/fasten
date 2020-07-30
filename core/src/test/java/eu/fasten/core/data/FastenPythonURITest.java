package eu.fasten.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FastenPythonURITest {

    @Test
    public void testCreation() {
        var fastenPythonURI = new FastenPythonURI("fasten://myproduct/myproduct.mod/Cls.func()");
        assertEquals("fasten", fastenPythonURI.getScheme());
        assertNull(fastenPythonURI.getForge());
        assertEquals("myproduct", fastenPythonURI.getProduct());
        assertNull(fastenPythonURI.getVersion());
        assertEquals("myproduct.mod", fastenPythonURI.getNamespace());
        assertEquals("Cls.func", fastenPythonURI.getIdentifier());
        assertTrue(fastenPythonURI.isFunction());

        // A normal case with version and forge
        fastenPythonURI = new FastenPythonURI("fasten://PyPI!myproduct$0.1/myproduct.mod/Cls.func()");
        assertEquals("fasten", fastenPythonURI.getScheme());
        assertEquals("PyPI", fastenPythonURI.getForge());
        assertEquals("myproduct", fastenPythonURI.getProduct());
        assertEquals("0.1", fastenPythonURI.getVersion());
        assertEquals("myproduct.mod", fastenPythonURI.getNamespace());
        assertEquals("Cls.func", fastenPythonURI.getIdentifier());
        assertTrue(fastenPythonURI.isFunction());

        // Empty namespace
        fastenPythonURI = new FastenPythonURI("fasten://requests//requests.get");
        assertEquals("fasten", fastenPythonURI.getScheme());
        assertEquals("requests", fastenPythonURI.getProduct());
        assertEquals("", fastenPythonURI.getNamespace());
        assertEquals("requests.get", fastenPythonURI.getIdentifier());
        assertFalse(fastenPythonURI.isFunction());

        // only module namespace
        fastenPythonURI = new FastenPythonURI("fasten://something/mod1.mod2/");
        assertEquals("fasten", fastenPythonURI.getScheme());
        assertEquals("something", fastenPythonURI.getProduct());
        assertEquals("mod1.mod2", fastenPythonURI.getNamespace());
        assertEquals("", fastenPythonURI.getIdentifier());
        assertFalse(fastenPythonURI.isFunction());
    }

    @Test
    public void testCreationNoEntity() {
        assertEquals("myproduct", new FastenPythonURI("fasten://myproduct").getProduct());
    }

    @Test
    public void testCreationError() {
        // function names not ending with "()"
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenPythonURI("fasten://myproduct/mymod/func(");
        });
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenPythonURI("fasten://myproduct/mymod/func(]");
        });
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenPythonURI("fasten://myproduct/mymod/func()X");
        });
        // no identifier part
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenPythonURI("fasten://myproduct/mymod");
        });
        // no product nor namespace
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenPythonURI("fasten:////func()");
        });
        // no namespace nor identifier
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new FastenPythonURI("fasten://myproduct//");
        });
    }

    @Test
    public void testCreateFromComponents() {
        FastenPythonURI u;

        // no namespace nor product
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            FastenPythonURI.create(null, null, null, null, "cls.func", false);
        });

        // no namespace nor identifier
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            FastenPythonURI.create(null, "external", null, null, null, false);
        });

        // no namespace
        u = FastenPythonURI.create(null, "external", null, null, "cls.func", false);
        assertEquals("fasten://external//cls.func", u.toString());

        // no product
        u = FastenPythonURI.create(null, null, null, "internal", "cls.func", false);
        assertEquals("fasten:/internal/cls.func", u.toString());

        // no product and a function
        u = FastenPythonURI.create(null, null, null, "mod1.mod2", "cls.func", true);
        assertEquals("fasten:/mod1.mod2/cls.func()", u.toString());

        // full fledged example
        u = FastenPythonURI.create("PyPI", "mod", "0.1", "mod1.mod2", "cls.func", true);
        assertEquals("fasten://PyPI!mod$0.1/mod1.mod2/cls.func()", u.toString());
    }

    @Test
    public void testRelativizeResolve() {
        FastenPythonURI u, v;

        u = FastenPythonURI.create("fasten://product$a/myns/cls");
        v = FastenPythonURI.create("fasten://product$a/myns/cls");
        assertEquals(u, v.resolve(v.relativize(u)));
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenPythonURI.create("fasten://myproduct$b/myns/cls");
        v = FastenPythonURI.create("fasten://myproduct$a/myns/cls");
        assertEquals(u, v.resolve(v.relativize(u)));
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenPythonURI.create("//myproduct$b/myns/cls");
        v = FastenPythonURI.create("fasten://myproduct$a/myns/cls");
        assertEquals("fasten:" + u, v.resolve(v.relativize(u)).toString());
        assertEquals("fasten:" + v.relativize(u), v.relativize(v.resolve(u)).toString());

        u = FastenPythonURI.create("fasten://myproduct$b/myns/cls");
        v = FastenPythonURI.create("//myproduct$a/myns/cls");
        assertEquals(u, v.resolve(v.relativize(u)));
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenPythonURI.create("fasten://myproduct$a/myns/cls1");
        v = FastenPythonURI.create("fasten://myproduct$a/myns/cls2");
        assertEquals(u, v.resolve(v.relativize(u)));
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        u = FastenPythonURI.create("/myns/cls");
        v = FastenPythonURI.create("fasten://myproduct$a/myns/cls");
        assertEquals("fasten://myproduct$a" + u, v.resolve(v.relativize(u)).toString());
        assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            final FastenPythonURI uerr = FastenPythonURI.create("fasten://myproduct$a//cls");
            final FastenPythonURI verr = FastenPythonURI.create("fasten://myproduct$a/myns/cls");
            verr.relativize(uerr);
        });

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            final FastenPythonURI uerr = FastenPythonURI.create("fasten://myproduct$a/myns/");
            final FastenPythonURI verr = FastenPythonURI.create("fasten://myproduct$a/myns/cls");
            verr.relativize(uerr);
        });
    }
}
