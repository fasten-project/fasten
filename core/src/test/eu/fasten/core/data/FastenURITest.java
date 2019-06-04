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

}
