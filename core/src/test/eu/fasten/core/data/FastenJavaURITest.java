package eu.fasten.core.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

class FastenJavaURITest {

	@Test
	void testCreationEmptyArgs() throws URISyntaxException {
		var fastenJavaURI = new FastenJavaURI("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph.copy()BVGraph");
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

		fastenJavaURI = new FastenJavaURI("fasten://mvn!webgraph.jar$3.6.2/it.unimi.dsi.webgraph/BVGraph.copy()BVGraph");
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

	@Test
	void testCreationOneArg() throws URISyntaxException {
		final var fastenJavaURI = new FastenJavaURI("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph.successors(java.primitive%2Fint)LazyIntIterator");
		assertEquals("fasten", fastenJavaURI.getScheme());
		assertEquals("webgraph.jar", fastenJavaURI.getArtefact());
		assertNull(fastenJavaURI.getForge());
		assertEquals("it.unimi.dsi.webgraph", fastenJavaURI.getModule());
		assertEquals("BVGraph", fastenJavaURI.getClassName());
		assertEquals("successors", fastenJavaURI.getFunctionName());
		//assertArrayEquals(new FastenJavaURI[] { FastenJavaURI.create("LazyIntIterator")}, fastenJavaURI.getArgs());
		//assertEquals(FastenJavaURI.create("BVGraph"), fastenJavaURI.getReturnType());
		//assertEquals(FastenJavaURI.create("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph"), fastenJavaURI.resolve(fastenJavaURI.getReturnType()));
	}
	
	@Test
	void testExample1() throws URISyntaxException {
		final var fastenJavaURI = new FastenJavaURI("fasten://xerces.xercesImpl$2.6.2/org.apache.html.dom/HTMLUListElementImpl.%3Cinit%3E(HTMLDocumentImpl,%2F%2Fjdk%2Fjava.lang%2FString)HTMLUListElementImpl");
		assertEquals("fasten", fastenJavaURI.getScheme());
		assertEquals("xerces.xercesImpl", fastenJavaURI.getArtefact());
		assertEquals("2.6.2", fastenJavaURI.getVersion());
		assertNull(fastenJavaURI.getForge());
		assertEquals("org.apache.html.dom", fastenJavaURI.getModule());
		assertEquals("HTMLUListElementImpl", fastenJavaURI.getClassName());
		assertEquals("<init>", fastenJavaURI.getFunctionName());
		assertEquals("HTMLUListElementImpl", fastenJavaURI.getReturnType().toString()); // TODO
		FastenJavaURI[] args = fastenJavaURI.getArgs();
		assertEquals(2, args.length);
		assertEquals("HTMLDocumentImpl", args[0].toString()); // TODO
		assertEquals("//jdk/java.lang/String", args[1].toString()); // TODO
	}

	@Test
	void testExample2() throws URISyntaxException {
		final var fastenJavaURI = new FastenJavaURI("fasten://com.faster.jackson.core.jackson-core/com.fasterxml.jackson.core.json.async/NonBlockingJsonParserBase._findName(%2F%2Fjdk%2Fjava.primitive%2Fint,%2F%2Fjdk%2Fjava.primitive%2Fint)%2F%2Fjdk%2Fjava.lang%2FString");
		assertEquals("fasten", fastenJavaURI.getScheme());
		assertEquals("com.faster.jackson.core.jackson-core", fastenJavaURI.getArtefact());
		assertNull(fastenJavaURI.getVersion());
		assertNull(fastenJavaURI.getForge());
		assertEquals("com.fasterxml.jackson.core.json.async", fastenJavaURI.getModule());
		assertEquals("NonBlockingJsonParserBase", fastenJavaURI.getClassName());
		assertEquals("_findName", fastenJavaURI.getFunctionName());
		assertEquals("//jdk/java.lang/String", fastenJavaURI.getReturnType().toString()); // TODO
		FastenJavaURI[] args = fastenJavaURI.getArgs();
		assertEquals(2, args.length);
		assertEquals("//jdk/java.primitive/int", args[0].toString()); // TODO
		assertEquals("//jdk/java.primitive/int", args[1].toString()); // TODO
	}
	
}
