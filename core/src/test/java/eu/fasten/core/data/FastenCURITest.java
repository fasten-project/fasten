package eu.fasten.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FastenCURITest {

	@Test
	void testCreation() {
		var fastenCURI = new FastenCURI("fasten://recsplit/C/copy()");
		assertEquals("fasten", fastenCURI.getScheme());
		assertNull(fastenCURI.getForge());
		assertEquals("recsplit", fastenCURI.getProduct());
		assertNull(fastenCURI.getVersion());
		assertEquals("C", fastenCURI.getNamespace());
		assertNull(fastenCURI.getFilename());
		assertEquals("copy", fastenCURI.getName());
		assertTrue(fastenCURI.isFunction());

		fastenCURI = new FastenCURI("fasten://debian!recsplit$0.1/C/%2Ftmp%2Fdir;var");
		assertEquals("fasten", fastenCURI.getScheme());
		assertEquals("debian", fastenCURI.getForge());
		assertEquals("recsplit", fastenCURI.getProduct());
		assertEquals("0.1", fastenCURI.getVersion());
		assertEquals("C", fastenCURI.getNamespace());
		assertEquals("/tmp/dir", fastenCURI.getFilename());
		assertEquals("var", fastenCURI.getName());
		assertFalse(fastenCURI.isFunction());

		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new FastenCURI("fasten://a/b/c");
		});
	}

	@Test
	void testCreationNoEntity() {
		assertEquals("recsplit", new FastenCURI("fasten://recsplit").getProduct());
	}

	@Test
	void testCreationError() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new FastenCURI("fasten://recsplit/C/copy(");
		});
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new FastenCURI("fasten://recsplit/C/copy(]");
		});
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new FastenCURI("fasten://recsplit/C/copy()X");
		});
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new FastenCURI("fasten://recsplit/C/");
		});
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new FastenCURI("fasten://recsplit/C");
		});
	}

	@Test
	public void testCreateFromComponents() {
		FastenCURI u;

		u = FastenCURI.create(null, null, null, "Bar", "dummy", false);
		assertEquals("fasten:/C/Bar;dummy", u.toString());

		u = FastenCURI.create(null, null, null, "Bar", "dummy", false);
		assertEquals("fasten:/C/Bar;dummy", u.toString());

		u = FastenCURI.create(null, null, null, "Bar", "dummy", true);
		assertEquals("fasten:/C/Bar;dummy()", u.toString());

		u = FastenCURI.create(null, null, null, "Bar", "dummy", true);
		assertEquals("fasten:/C/Bar;dummy()", u.toString());

		u = FastenCURI.create(null, null, null, "Bar", "dummy", true);
		assertEquals("fasten:/C/Bar;dummy()", u.toString());


		u = FastenCURI.create(null, null, null, null, "dummy", false);
		assertEquals("fasten:/C/dummy", u.toString());

		u = FastenCURI.create(null, null, null, null, "dummy", false);
		assertEquals("fasten:/C/dummy", u.toString());

		u = FastenCURI.create(null, null, null, null, "dummy", true);
		assertEquals("fasten:/C/dummy()", u.toString());

		u = FastenCURI.create(null, null, null, null, "dummy", true);
		assertEquals("fasten:/C/dummy()", u.toString());

		u = FastenCURI.create(null, null, null, null, "dummy", true);
		assertEquals("fasten:/C/dummy()", u.toString());
	}

	@Test
	public void testRelativizeResolve() {
		FastenCURI u, v;

		u = FastenCURI.create("fasten://mvn$a/C/Bar");
		v = FastenCURI.create("fasten://mvn$a/C/Bar");
		assertEquals(u, v.resolve(v.relativize(u)));
		assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

		u = FastenCURI.create("fasten://mvn$b/C/Bar");
		v = FastenCURI.create("fasten://mvn$a/C/Bar");
		assertEquals(u, v.resolve(v.relativize(u)));
		assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

		u = FastenCURI.create("//mvn$b/C/Bar");
		v = FastenCURI.create("fasten://mvn$a/C/Bar");
		assertEquals("fasten:" + u, v.resolve(v.relativize(u)).toString());
		assertEquals("fasten:" + v.relativize(u), v.relativize(v.resolve(u)).toString());

		u = FastenCURI.create("fasten://mvn$b/C/Bar");
		v = FastenCURI.create("//mvn$a/C/Bar");
		assertEquals(u, v.resolve(v.relativize(u)));
		assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

		u = FastenCURI.create("fasten://mvn$a/C/Bar");
		v = FastenCURI.create("fasten://mvn$a/C/Dummy");
		assertEquals(u, v.resolve(v.relativize(u)));
		assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

		u = FastenCURI.create("/C/Bar");
		v = FastenCURI.create("fasten://mvn$a/C/Bar");
		assertEquals("fasten://mvn$a" + u, v.resolve(v.relativize(u)).toString());
		assertEquals(v.relativize(u), v.relativize(v.resolve(u)));

		u = FastenCURI.create("Bar");
		v = FastenCURI.create("fasten://mvn$a/C/Bar");
		assertEquals("fasten://mvn$a/C/" + u, v.resolve(v.relativize(u)).toString());
		assertEquals(v.relativize(u), v.relativize(v.resolve(u)));
	}

}
