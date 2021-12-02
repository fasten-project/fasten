/*
 * Copyright 2021 Delft University of Technology
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fasten.analyzer.javacgopal;

import static eu.fasten.core.utils.TestUtils.getTestResource;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.junit.jupiter.api.Test;
import org.opalj.br.DeclaredMethod;
import org.opalj.br.Method;
import org.opalj.br.MethodDescriptor;
import org.opalj.br.VirtualDeclaredMethod;
import org.opalj.tac.cg.CallGraph;

import com.google.common.collect.Sets;

import eu.fasten.analyzer.javacgopal.data.OPALCallGraph;
import eu.fasten.core.data.callgraph.CGAlgorithm;

public class OPALCallGraphConstructorIntegrationTest {

	@Test
	public void java8BasicExample() {
		OPALCallGraph ocg = constructFromResources("java-8-basic/target/java-8-basic-0.0.1-SNAPSHOT.jar");

		var actual = collectCalls(ocg);
		var expected = Sets.newHashSet(//
				new Call("B.b1", "A.<init>"), //
				new Call("B.b1", "A.a1")//
		);
		assertEquals(expected, actual);
	}

	@Test
	public void java8WithDependencies() {
		OPALCallGraph ocg = constructFromResources( //
				"java-8-with-dependencies/target/java-8-with-dependencies-0.0.1-SNAPSHOT.jar");

		var actual = collectCalls(ocg);
		var expected = Sets.newHashSet( //
				new Call("C.c1", "B.b1"), //
				new Call("C.c2", "C.c1") //
		);
		assertEquals(expected, actual);
	}

	@Test
	public void shouldNotFindEntrypointsInDependencies() {
		OPALCallGraph ocg = constructFromResources( //
				null, // no classes/jars used as analyzed unit
				"java-8-with-dependencies/target/java-8-with-dependencies-0.0.1-SNAPSHOT.jar"); // only dependencies

		var actual = collectCalls(ocg);
		var expected = Sets.newHashSet();
		assertEquals(expected, actual);
	}

	private OPALCallGraph constructFromResources(String path, String... deps) {

		File[] classFiles = null;
		File f = null;

		if (path == null) {
			classFiles = new File[0];
		} else if ((f = findInResources(path)).exists()) {
			if (f.isDirectory()) {
				classFiles = findClassFiles(f);
			} else {
				classFiles = new File[] { f };
			}
		}

		File[] depFiles = Arrays.stream(deps).map(dep -> findInResources(dep)).toArray(File[]::new);
		OPALCallGraphConstructor ocgc = new OPALCallGraphConstructor();

		return ocgc.construct(classFiles, depFiles, CGAlgorithm.CHA);
	}

	private File[] findClassFiles(File projectClassFolder) {
		return FileUtils.listFiles(projectClassFolder, new String[] { "class" }, true).toArray(File[]::new);
	}

	private File findInResources(String path) {
		path = "opal-examples/" + path;
		var f = getTestResource(path);
		if (!f.exists()) {
			throw new RuntimeException(String.format("file does not exist in example folder: %s", f));
		}
		return f;
	}

	private Set<Call> collectCalls(OPALCallGraph cgc) {
		Set<Call> names = new HashSet<>();

		CallGraph cg = cgc.callGraph;
		cg.reachableMethods().foreach(caller -> {

			// find method if existing
			if (!caller.hasSingleDefinedMethod() && !caller.hasMultipleDefinedMethods()) {
				// method referenced, but undefined, e.g., method from unresolved dependency
				return null;
			}

			if (caller.hasMultipleDefinedMethods()) {
				String msg = "Nice, we found a case. Write a test case for this! (see %s)";
				throw new IllegalStateException(String.format(msg, getClass()));
			}

			cg.calleesOf(caller).foreach(pcToDMs -> {
				pcToDMs._2.foreach(callee -> {

					var isObj = "java/lang/Object".equals(callee.declaringClassType().fqn());
					var isDefaultInit = callee.descriptor() == MethodDescriptor.DefaultConstructorDescriptor();
					if (isObj && isDefaultInit) {
						return null;
					}

					names.add(new Call(toStr(caller), toStr(callee)));
					return null;
				});
				return null;
			});

			return null;
		});

		return names;
	}

	private static String toStr(DeclaredMethod dm) {

		if (dm.hasSingleDefinedMethod()) {
			return toStr(dm.definedMethod());
		}
		if (dm.hasMultipleDefinedMethods()) {
			// use name of first hit
			return toStr(dm.definedMethods().find(m -> true).get());
		}

		if (dm instanceof VirtualDeclaredMethod) {
			return toStr((VirtualDeclaredMethod) dm);
		}

		throw new RuntimeException("Encountered unknown subtype of DeclaredMethod: " + dm.getClass());
	}

	private static String toStr(VirtualDeclaredMethod vdm) {
		String className = vdm.declaringClassType().simpleName();
		String methodName = vdm.name();
		return String.format("%s.%s", className, methodName);
	}

	private static String toStr(Method m) {
		StringBuilder sb = new StringBuilder();

		String className = m.declaringClassFile().thisType().simpleName();
		sb.append(className);

		String methodName = m.name();
		sb.append('.').append(methodName);

		return sb.toString();
	}

	private static class Call {

		public final String source;
		public final String target;

		public Call(String source, String target) {
			this.source = source;
			this.target = target;
		}

		@Override
		public int hashCode() {
			return HashCodeBuilder.reflectionHashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return EqualsBuilder.reflectionEquals(this, obj);
		}

		@Override
		public String toString() {
			return String.format("Call(%s -> %s)", source, target);
		}
	}
}