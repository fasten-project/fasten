package eu.fasten.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URISyntaxException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FastenURITest {

	@Test
	void testCreation() throws URISyntaxException {
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

		fastenURI = new FastenURI("fasten://b/∂∂∂/€");
		assertEquals("fasten", fastenURI.getScheme());
		assertNull(fastenURI.getForge());
		assertEquals("b", fastenURI.getProduct());
		assertNull(fastenURI.getVersion());
		assertEquals("∂∂∂", fastenURI.getNamespace());
		assertEquals("€", fastenURI.getEntity());

	}
	@Test
	void testRawNonRow() throws URISyntaxException {
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
	public void testDecodeEmptyString() {
		assertEquals(0, FastenURI.decode("").length());
	}

	@Test
	public void testRelativize() {
		FastenURI u;

		Assertions.assertThrows(IllegalStateException.class, () -> {
			final FastenURI v = FastenURI.create("/foo/Bar");
			assertEquals(v, FastenURI.create("Bar").relativize(v));
		});

		u = FastenURI.create("fasten://mvn$a/foo/Bar");
		assertEquals(FastenURI.create("/foo/Bar"), FastenURI.create("fasten://mvn$a/nope/Bar").relativize(u));

		u = FastenURI.create("fasten://mvn$b/foo/Bar");
		assertEquals(u, FastenURI.create("fasten://mvn$a/foo/Bar").relativize(u));

		u = FastenURI.create("//mvn$b/foo/Bar");
		assertEquals(u, FastenURI.create("fasten://mvn$a/foo/Bar").relativize(u));

		u = FastenURI.create("fasten://mvn$b/foo/Bar");
		assertEquals(u, FastenURI.create("//mvn$a/foo/Bar").relativize(u));

		Assertions.assertThrows(IllegalStateException.class, () -> {
			final FastenURI v = FastenURI.create("fasten://mvn$b/foo/Bar");
			assertEquals(v, FastenURI.create("Bar").relativize(v));
		});

		u = FastenURI.create("fasten://mvn$a/foo/Bar");
		assertEquals(FastenURI.create("Bar"), FastenURI.create("fasten://mvn$a/foo/Dummy").relativize(u));

		u = FastenURI.create("/foo/Bar");
		assertEquals(FastenURI.create("Bar"), FastenURI.create("fasten://mvn$a/foo/Bar").relativize(u));

		u = FastenURI.create("Bar");
		assertEquals(FastenURI.create("Bar"), FastenURI.create("fasten://mvn$a/foo/Bar").relativize(u));
	}


}
