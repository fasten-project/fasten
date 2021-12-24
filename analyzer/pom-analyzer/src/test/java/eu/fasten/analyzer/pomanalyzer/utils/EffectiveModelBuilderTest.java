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
package eu.fasten.analyzer.pomanalyzer.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.model.Model;
import org.junit.jupiter.api.Test;

import eu.fasten.analyzer.pomanalyzer.utils.EffectiveModelBuilder;
import eu.fasten.analyzer.pomanalyzer.utils.Resolver;
import eu.fasten.core.utils.TestUtils;

public class EffectiveModelBuilderTest {

	@Test
	public void basic() {
		var model = buildEffectiveModel("basic.pom");

		var actual = model.getDependencies().stream() //
				.map(d -> String.format("%s:%s", d.getArtifactId(), d.getVersion())) //
				.collect(Collectors.toSet());
		var expected = deps("commons-lang3:3.12.0");
		assertEquals(expected, actual);
	}

	@Test
	public void inheritedDepedency() {
		var model = buildEffectiveModel("inherited-dependency.pom");

		var actual = model.getDependencies().stream() //
				.map(d -> String.format("%s:%s", d.getArtifactId(), d.getVersion())) //
				.collect(Collectors.toSet());
		assertTrue(actual.contains("guava:18.0"));
		assertTrue(actual.contains("commons-lang3:3.0"));
		// ... and others, left out for brevity
	}

	@Test
	public void inheritedVersion() {
		var model = buildEffectiveModel("inherited-version.pom");

		var actual = model.getVersion();
		var expected = "9";
		assertEquals(expected, actual);
	}

	@Test
	public void inheritedProperty() {
		var model = buildEffectiveModel("inherited-property.pom");

		var actual = model.getProperties().getOrDefault("project.build.sourceEncoding", null);
		var expected = "UTF-8";
		assertEquals(expected, actual);
	}

	@Test
	public void propertyReplacement() {
		var model = buildEffectiveModel("property-replacement.pom");

		var actual = model.getDependencies().stream() //
				.map(d -> String.format("%s:%s", d.getArtifactId(), d.getVersion())) //
				.collect(Collectors.toSet());
		var expected = deps("commons-lang3:3.12.0");
		assertEquals(expected, actual);
	}

	private static Model buildEffectiveModel(String pathToPom) {
		var fullPath = Path.of(EffectiveModelBuilderTest.class.getSimpleName(), pathToPom);
		File pom = TestUtils.getTestResource(fullPath.toString());
		// resolve once to make sure all dependencies exist in local repo
		new Resolver().resolveDependenciesFromPom(pom);
		var sut = new EffectiveModelBuilder();
		return sut.buildEffectiveModel(pom);
	}

	private static Set<String> deps(String... deps) {
		Set<String> res = new HashSet<String>();
		for (String dep : deps) {
			res.add(dep);
		}
		return res;
	}
}