package eu.fasten.core.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Component;
import java.awt.event.ActionListener;
import java.net.URISyntaxException;

import javax.swing.JColorChooser;

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
		final FastenJavaURI[] args = fastenJavaURI.getArgs();
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
		final FastenJavaURI[] args = fastenJavaURI.getArgs();
		assertEquals(2, args.length);
		assertEquals("//jdk/java.primitive/int", args[0].toString()); // TODO
		assertEquals("//jdk/java.primitive/int", args[1].toString()); // TODO
	}

	@Test
	void testGetURIJColorChooser() throws NoSuchMethodException, SecurityException, URISyntaxException {
		final var method = JColorChooser.class.getMethod("createDialog", Component.class, String.class, boolean.class, JColorChooser.class, ActionListener.class, ActionListener.class);
		final FastenJavaURI uri = FastenJavaURI.getURI(method, null);
		assertEquals("fasten", uri.getScheme());
		assertEquals("jdk", uri.getArtefact());
		assertEquals(null, uri.getVersion());
		assertEquals(null, uri.getForge());
		assertEquals("javax.swing", uri.getModule());
		assertEquals("JColorChooser", uri.getClassName());
		assertEquals("createDialog", uri.getFunctionName());
		assertEquals("JDialog", uri.getReturnType().toString());
		final FastenJavaURI[] args = uri.getArgs();
		assertEquals(6, args.length);
		assertEquals("/java.awt/Component", args[0].toString());
		assertEquals("/java.lang/String", args[1].toString());
		assertEquals("/java.lang/boolean", args[2].toString());
		assertEquals("JColorChooser", args[3].toString());
		assertEquals("/java.awt.event/ActionListener", args[4].toString());
		assertEquals("/java.awt.event/ActionListener", args[5].toString());
	}

	@Test
	void testSpecialCharacters() throws URISyntaxException {
		final var uri = new FastenJavaURI("//com.fasterxml.jackson.core_jackson-databind/com.fasterxml.jackson.databind.type/TypeBindings.%3Cinit%3E(//jdk/java.lang/String%255B%255D,//jdk/com.fasterxml.jackson.databind/JavaType%255B%255D,//jdk/java.lang/String%255B%255D)//com.fasterxml.jackson.core_jackson-databind/com.fasterxml.jackson.databind.type/TypeBindings");
	}
}
