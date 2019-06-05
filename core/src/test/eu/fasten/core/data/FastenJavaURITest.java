package eu.fasten.core.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URISyntaxException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FastenJavaURITest {

	@Test
	void testCreationEmptyArgs() throws URISyntaxException {
		var fastenJavaURI = new FastenJavaURI("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph.copy()BVGraph");
		assertEquals("fasten", fastenJavaURI.getScheme());
		assertNull(fastenJavaURI.getForge());
		assertEquals("webgraph.jar", fastenJavaURI.getProduct());
		assertNull(fastenJavaURI.getVersion());
		assertEquals("it.unimi.dsi.webgraph", fastenJavaURI.getNamespace());
		assertEquals("BVGraph", fastenJavaURI.getClassName());
		assertEquals("copy", fastenJavaURI.getFunctionName());
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
		assertEquals("copy", fastenJavaURI.getFunctionName());
		assertArrayEquals(new FastenJavaURI[0], fastenJavaURI.getArgs());
		//assertEquals(FastenJavaURI.create("BVGraph"), fastenJavaURI.getReturnType());
		//assertEquals(FastenJavaURI.create("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph"), fastenJavaURI.resolve(fastenJavaURI.getReturnType()));
	}

	@Test
	void testCreationOneArg() throws URISyntaxException {
		final var fastenJavaURI = new FastenJavaURI("fasten://webgraph.jar/it.unimi.dsi.webgraph/BVGraph.successors(%2Fjava.primitive%2Fint)LazyIntIterator");
		assertEquals("fasten", fastenJavaURI.getScheme());
		assertEquals("webgraph.jar", fastenJavaURI.getProduct());
		assertNull(fastenJavaURI.getForge());
		assertEquals("it.unimi.dsi.webgraph", fastenJavaURI.getNamespace());
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
		assertEquals("xerces.xercesImpl", fastenJavaURI.getProduct());
		assertEquals("2.6.2", fastenJavaURI.getVersion());
		assertNull(fastenJavaURI.getForge());
		assertEquals("org.apache.html.dom", fastenJavaURI.getNamespace());
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
		assertEquals("com.faster.jackson.core.jackson-core", fastenJavaURI.getProduct());
		assertNull(fastenJavaURI.getVersion());
		assertNull(fastenJavaURI.getForge());
		assertEquals("com.fasterxml.jackson.core.json.async", fastenJavaURI.getNamespace());
		assertEquals("NonBlockingJsonParserBase", fastenJavaURI.getClassName());
		assertEquals("_findName", fastenJavaURI.getFunctionName());
		assertEquals("//jdk/java.lang/String", fastenJavaURI.getReturnType().toString()); // TODO
		final FastenJavaURI[] args = fastenJavaURI.getArgs();
		assertEquals(2, args.length);
		assertEquals("//jdk/java.primitive/int", args[0].toString()); // TODO
		assertEquals("//jdk/java.primitive/int", args[1].toString()); // TODO
	}

	@Test
	public void testNamespace() throws URISyntaxException {
		final var uri = new FastenJavaURI("/my.package/A.f(A)B");
		assertEquals(FastenJavaURI.create("/my.package/A.f(A)B"), uri.args[0].resolve(uri));
	}

	@Test
	public void testCanonical() throws URISyntaxException {
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

		final FastenURI w = FastenURI.create("fasten://mvn$b/foo/Bar");
		assertEquals(FastenJavaURI.create(w.uri), FastenJavaURI.create("fasten://mvn$a/foo/Bar").relativize(w));

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

}
