package eu.fasten.core.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

class FastenJavaURITest {

	@Test
	void testCreationEmptyArgs() throws URISyntaxException {
		var fastenJavaURI = new FastenJavaURI("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph:copy()BVGraph");
		assertEquals("fasten", fastenJavaURI.getScheme());
		assertNull(fastenJavaURI.getForge());
		assertEquals("webgraph.jar", fastenJavaURI.getArtefact());
		assertNull(fastenJavaURI.getVersion());
		assertEquals("it.unimi.dsi.webgraph", fastenJavaURI.getModule());
		assertEquals("BVGraph", fastenJavaURI.getClassName());
		assertEquals("copy", fastenJavaURI.getFunctionName());
		assertArrayEquals(new FastenJavaURI[0], fastenJavaURI.getArgs());
		//assertEquals(FastenJavaURI.create("BVGraph"), fastenJavaURI.getReturnType());
		//assertEquals(FastenJavaURI.create("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph"), fastenJavaURI.resolve(fastenJavaURI.getReturnType()));

		fastenJavaURI = new FastenJavaURI("fasten://mvn!webgraph.jar$3.6.2/it.unimi.dsi.webgraph/BVGraph:copy()BVGraph");
		assertEquals("fasten", fastenJavaURI.getScheme());
		assertEquals("mvn", fastenJavaURI.getForge());
		assertEquals("webgraph.jar", fastenJavaURI.getArtefact());
		assertEquals("3.6.2", fastenJavaURI.getVersion());
		assertEquals("it.unimi.dsi.webgraph", fastenJavaURI.getModule());
		assertEquals("BVGraph", fastenJavaURI.getClassName());
		assertEquals("copy", fastenJavaURI.getFunctionName());
		assertArrayEquals(new FastenJavaURI[0], fastenJavaURI.getArgs());
		//assertEquals(FastenJavaURI.create("BVGraph"), fastenJavaURI.getReturnType());
		//assertEquals(FastenJavaURI.create("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph"), fastenJavaURI.resolve(fastenJavaURI.getReturnType()));
	}

	void testCreationOneArg() throws URISyntaxException {
		final var fastenJavaURI = new FastenJavaURI("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph:successors(java.primitive%2Fint)LazyIntIterator");
		assertEquals("fasten", fastenJavaURI.getScheme());
		assertEquals("webgraph.jar", fastenJavaURI.getArtefact());
		assertEquals("it.unimi.dsi.webgraph", fastenJavaURI.getModule());
		assertEquals("BVGraph", fastenJavaURI.getClassName());
		assertEquals("successors", fastenJavaURI.getFunctionName());
		//assertArrayEquals(new FastenJavaURI[] { FastenJavaURI.create("LazyIntIterator")}, fastenJavaURI.getArgs());
		//assertEquals(FastenJavaURI.create("BVGraph"), fastenJavaURI.getReturnType());
		//assertEquals(FastenJavaURI.create("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph"), fastenJavaURI.resolve(fastenJavaURI.getReturnType()));
	}

}
