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
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import eu.fasten.analyzer.pomanalyzer.data.PomAnalysisResult;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Exclusion;
import eu.fasten.core.maven.data.VersionConstraint;
import eu.fasten.core.utils.TestUtils;

public class PomExtractorTest {

	// see https://maven.apache.org/pom.html
	
	@Test
	public void minimal() {
		var actual = extract("minimal.pom");
		var expected = getMinimal();
		assertEquals(expected, actual);
	}

	@Test
	public void packaging() {
		var actual = extract("packaging.pom");
		var expected = getMinimal();
		expected.packagingType = "war";
		assertEquals(expected, actual);
	}

	@Test
	public void name() {
		var actual = extract("name.pom");
		var expected = getMinimal();
		expected.projectName = "SOMENAME";
		assertEquals(expected, actual);
	}

	@Test
	public void parent() {
		var actual = extract("parent.pom");
		var expected = getMinimal();
		expected.parentCoordinate = "pgid:paid:pom:1.2.3";
		assertEquals(expected, actual);
	}

	@Test
	public void parentMalformed() {
		var actual = extract("parent-malformed.pom");
		var expected = getMinimal();
		expected.parentCoordinate = "?:?:pom:?";
		assertEquals(expected, actual);
	}

	@Test
	public void dependencies() {
		var actual = extract("dependencies.pom");
		var expected = getMinimal();
		expected.dependencies.add(dep("g1:a1:jar:1", "compile", false, ""));
		expected.dependencies.add(dep("g2:a2:pom:2", "compile", false, ""));
		expected.dependencies.add(dep("g3:a3:jar:3", "test", false, ""));
		expected.dependencies.add(dep("g4:a4:jar:4", "compile", true, ""));
		expected.dependencies.add(dep("g5:a5:jar:5", "compile", false, "c"));
		assertEquals(expected, actual);
	}

	@Test
	public void dependenciesEmpty() {
		var actual = extract("dependencies-empty.pom");
		var expected = getMinimal();
		assertEquals(expected, actual);
	}

	@Test
	public void dependenciesExclusion() {
		var actual = extract("dependencies-exclusion.pom");
		var expected = getMinimal();
		expected.dependencies.add(depE("g1:a1:1"));
		expected.dependencies.add(depE("g2:a2:2", "eg1:ea1"));
		expected.dependencies.add(depE("g3:a3:3", "eg1:ea1", "eg2:ea2"));
		expected.dependencies.add(depE("g4:a4:4", "*:*"));
		assertEquals(expected, actual);
	}

	@Test
	public void dependenciesVersionConstraints() {
		var actual = extract("dependencies-versionconstraints.pom");
		var expected = getMinimal();
		expected.dependencies.add(depC(1, "1.0"));
		expected.dependencies.add(depC(2, "[1.0]"));
		expected.dependencies.add(depC(3, "(,1.0]"));
		expected.dependencies.add(depC(4, "[1.2,1.3]"));
		expected.dependencies.add(depC(5, "[1.0,2.0)"));
		expected.dependencies.add(depC(6, "[1.5,)"));
		expected.dependencies.add(depC(7, "(,1.0]", "[1.2)"));
		expected.dependencies.add(depC(8, "(,1.1)", "(1.1,)"));
		assertEquals(expected, actual);
	}

	@Test
	public void dependencyManagementEmpty() {
		var actual = extract("dependency-management-empty.pom");
		var expected = getMinimal();
		assertEquals(expected, actual);
	}

	@Test
	public void dependencyManagementEmptyDependencies() {
		var actual = extract("dependency-management-empty-deps.pom");
		var expected = getMinimal();
		assertEquals(expected, actual);
	}

	@Test
	public void dependencyManagement() {
		var actual = extract("dependency-management.pom");
		var expected = getMinimal();
		expected.dependencyManagement.add(dep("g1:a1:1"));
		assertEquals(expected, actual);
	}

	@Test
	public void profileEmpty() {
		var actual = extract("profiles-empty.pom");
		var expected = getMinimal();
		assertEquals(expected, actual);
	}

	@Test
	public void profileDefaultActivated() {
		// check that all information is copied over for activated profiles
		var actual = extract("profiles-default-activation.pom");
		var expected = getMinimal();
		expected.dependencies.add(dep("g1:a1:1"));
		expected.dependencyManagement.add(dep("g2:a2:2"));
		assertEquals(expected, actual);
	}

	@Test
	public void profileNoActivation() {
		// make sure that no info is copied from unactivated profiles
		var actual = extract("profiles-no-activation.pom");
		var expected = getMinimal();
		assertEquals(expected, actual);
	}

	@Test
	public void scm() {
		var actual = extract("scm.pom");
		var expected = getMinimal();
		expected.repoUrl = "scmcon";
		expected.commitTag = "scmtag";
		assertEquals(expected, actual);
	}

	@Test
	public void scmEmpty() {
		var actual = extract("scm-empty.pom");
		var expected = getMinimal();
		assertEquals(expected, actual);
	}

	@Test
	public void scmNoConnection() {
		var actual = extract("scm-no-connection.pom");
		var expected = getMinimal();
		expected.repoUrl = "scmdevcon";
		expected.commitTag = "scmtag";
		assertEquals(expected, actual);
	}

	@Test
	public void scmNoDevConnection() {
		var actual = extract("scm-no-dev-connection.pom");
		var expected = getMinimal();
		expected.repoUrl = "scmurl";
		expected.commitTag = "scmtag";
		assertEquals(expected, actual);
	}

	@Test
	public void scmNoTag() {
		var actual = extract("scm-no-tag.pom");
		var expected = getMinimal();
		expected.repoUrl = "scmcon";
		expected.commitTag = "HEAD"; // default value
		assertEquals(expected, actual);
	}

	@Test
	public void scmNoUrl() {
		// without any url, the tag is irrelevant
		var actual = extract("scm-no-url.pom");
		var expected = getMinimal();
		assertEquals(expected, actual);
	}

	@Test
	@Disabled
	public void others() {
		fail();
	}

	private static PomAnalysisResult getMinimal() {
		var expected = new PomAnalysisResult();
		expected.groupId = "test";
		expected.artifactId = "PomAnalyzerTest";
		expected.packagingType = "jar";
		expected.version = "0.0.1-SNAPSHOT";
		return expected;
	}

	private static PomAnalysisResult extract(String pathToPom) {
		var fullPath = Path.of(PomExtractorTest.class.getSimpleName(), pathToPom);
		File pom = TestUtils.getTestResource(fullPath.toString());
		try {
			var reader = new MavenXpp3Reader();
			Model model = reader.read(new FileReader(pom));
			return new PomExtractor().process(model);
		} catch (IOException | XmlPullParserException e) {
			throw new RuntimeException(e);
		}
	}

	private static Dependency dep(String gav) {
		String[] parts = gav.split(":");
		return new Dependency(parts[0], parts[1], parts[2], new HashSet<>(), "compile", false, "jar", "");
	}

	private static Dependency dep(String gapv, String scope, boolean optional, String classifier) {
		String[] parts = gapv.split(":");
		return new Dependency(parts[0], parts[1], parts[3], new HashSet<>(), scope, optional, parts[2], classifier);
	}

	private static Dependency depE(String gav, String... excls) {
		String[] parts = gav.split(":");
		var exclusions = new HashSet<Exclusion>();
		for (var excl : excls) {
			String[] ga = excl.split(":");
			exclusions.add(new Exclusion(ga[0], ga[1]));
		}
		return new Dependency(parts[0], parts[1], parts[2], exclusions, "compile", false, "jar", "");
	}

	private Dependency depC(int i, String... specs) {
		var vcs = new LinkedHashSet<VersionConstraint>();
		for (var spec : specs) {
			vcs.add(new VersionConstraint(spec));
		}
		return new Dependency("g" + i, "a" + i, vcs, new HashSet<>(), "compile", false, "jar", "");
	}
}