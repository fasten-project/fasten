package eu.fasten.analyzer.javacgopal.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.opalj.br.DeclaredMethod;
import org.opalj.br.Method;
import org.opalj.tac.cg.CallGraph;

import com.google.common.collect.Sets;

import scala.collection.Iterator;

public class CallGraphConstructorIntegrationTest {

	@Test
	public void java8BasicExample() {
		CallGraphConstructor cgc = constructFromResources("java-8-basic/target/classes/");

		var actual = collectMethodSignatures(cgc);
		var expected = Sets.newHashSet();
		assertEquals(expected, actual);
	}

	@Test
	public void java8WithDependencies() {
		CallGraphConstructor cgc = constructFromResources( //
				"java-8-with-dependencies/target/classes/", //
				"java-8-basic/target/java-8-basic-0.0.1-SNAPSHOT.jar");

		var actual = collectMethodSignatures(cgc);
		var expected = Sets.newHashSet();
		assertEquals(expected, actual);
	}

	@Test
	public void java8OnlyUsePackages() {
		CallGraphConstructor cgc = constructFromResources( //
				null, // no classes
				"java-8-with-dependencies/target/java-8-with-dependencies-0.0.1-SNAPSHOT.jar", //
				"java-8-basic/target/java-8-basic-0.0.1-SNAPSHOT.jar");

		var actual = collectMethodSignatures(cgc);
		var expected = Sets.newHashSet();
		assertEquals(expected, actual);
	}

	private CallGraphConstructor constructFromResources(String projectClassFolder, String... deps) {
		File[] classFiles = projectClassFolder == null //
				? new File[0] //
				: findClassFiles(findInResources(projectClassFolder));
		File[] depFiles = Arrays.stream(deps).map(dep -> findInResources(dep)).toArray(File[]::new);
		return new CallGraphConstructor(classFiles, depFiles, null, CGAlgorithm.CHA);
	}

	private File[] findClassFiles(File projectClassFolder) {
		return FileUtils.listFiles(projectClassFolder, new String[] { "class" }, true).toArray(File[]::new);
	}

	private File findInResources(String path) {
		path = "opal-examples/" + path;
		URL url = getClass().getClassLoader().getResource("");
		File f = new File(url.getFile() + path);
		if (!f.exists()) {
			throw new RuntimeException(String.format("file does not exist in example folder: %s", f));
		}
		return f;
	}

	private Set<String> collectMethodSignatures(CallGraphConstructor cgc) {
		Set<String> names = new HashSet<>();

		CallGraph cg = cgc.getCallGraph();
		Iterator<DeclaredMethod> it = cg.reachableMethods();
		while (it.hasNext()) {
			DeclaredMethod dm = it.next();

			// find method if existing
			if (!dm.hasSingleDefinedMethod() && !dm.hasMultipleDefinedMethods()) {
				System.out.println("no defined method found for " + dm);
				continue;
			}

			if (dm.hasMultipleDefinedMethods()) {
				throw new IllegalStateException(
						"Not clear what this means. Failing this case until we understand what happens here.");
			}

			Method m = dm.definedMethod();
			String name = m.fullyQualifiedSignature();
			names.add(name);
		}

		return names;
	}
}