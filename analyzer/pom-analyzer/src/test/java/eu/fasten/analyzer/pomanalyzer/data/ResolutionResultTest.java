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
package eu.fasten.analyzer.pomanalyzer.data;

import static eu.fasten.analyzer.pomanalyzer.utils.MavenRepositoryUtils.getPathOfLocalRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;

import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;

import eu.fasten.analyzer.pomanalyzer.utils.MavenRepositoryUtils;

public class ResolutionResultTest {

	@Test
	public void initStoresValuesAndDerivesPom() {
		var gapv = "g:a:jar:1";
		var repo = "http://somewhere/";
		var sut = new ResolutionResult(gapv, repo, inM2("...", "xyz.jar"));

		assertEquals(gapv, sut.coordinate);
		assertEquals(repo, sut.artifactRepository);
		assertEquals(getPathOfLocalRepository(), sut.localM2Repository);
		assertEquals(inM2("...", "xyz.pom"), sut.localPomFile);
		assertEquals(inM2("...", "xyz.jar"), sut.getLocalPackageFile());
	}

	@Test
	public void localM2RepositoryCanBeReplaced() throws MalformedURLException {
		var sut = new ResolutionResult("g:a:jar:1", "http://somewhere/", inTmp("...", "xyz.jar")) {
			@Override
			protected File getLocalM2Repository() {
				return inTmp();
			}
		};
		assertEquals(inTmp(), sut.localM2Repository);
		assertEquals("http://somewhere/.../xyz.pom", sut.getPomUrl());
	}

	@Test
	public void canBeInitializedWithoutLocalFile() throws MalformedURLException {
		var gapv = "g.g2:a:?:1";
		var repo = "http://somewhere/";
		var sut = new ResolutionResult(gapv, repo);

		assertEquals(inM2("g", "g2", "a", "1", "a-1.pom"), sut.localPomFile);
		assertEquals("http://somewhere/g/g2/a/1/a-1.pom", sut.getPomUrl());
	}

	@Test
	public void canGeneratePomUrl() throws MalformedURLException {
		var sut = new ResolutionResult("g:a:jar:1", "http://somewhere/sub/", inM2("...", "xyz.jar"));
		assertEquals("http://somewhere/sub/.../xyz.pom", sut.getPomUrl());
	}

	@Test
	public void canGeneratePomUrlWithoutDoubleSlashes() throws MalformedURLException {

		for (var m2 : getOSDependentM2FoldersWithAndWithoutEndingFileDelimiter()) {
			// use repos +/- ending file delimiter and +/- subfolder
			for (var repo : new String[] { "http://a", "http://b/", "http://c/d", "http://c/e" }) {
				var sut = new ResolutionResult("g1.g2:a:jar:1", repo) {
					@Override
					protected File getLocalM2Repository() {
						return new File(m2);
					}
				};

				String actual = sut.getPomUrl();
				assertTrue(actual.startsWith("http://"));
				actual = actual.substring("http://".length());
				assertFalse(actual.contains("//"));
				assertTrue(actual.endsWith("/g1/g2/a/1/a-1.pom"));
			}
		}
	}

	private String[] getOSDependentM2FoldersWithAndWithoutEndingFileDelimiter() {
		if (SystemUtils.IS_OS_WINDOWS) {
			return new String[] { "C:\\.m2", "C:\\.m2\\" };
		}
		return new String[] { "/.m2", "/.m2/" };
	}

	@Test
	public void failsWhenPomIsNotInM2Folder() {
		var sut = new ResolutionResult("g:a:jar:1", "http://somewhere/sub/", inTmp("...", "xyz.jar"));
		assertThrows(IllegalStateException.class, () -> {
			sut.getPomUrl();
		});
	}

	@Test
	public void equality() {
		var a = new ResolutionResult("g:a:jar:1", "http://somewhere/", inM2("...", "xyz.jar"));
		var b = new ResolutionResult("g:a:jar:1", "http://somewhere/", inM2("...", "xyz.jar"));
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	public void equalityDiffGAV() {
		var repository = "http://somewhere/";
		var pkg = inM2("...", "xyz.jar");
		var a = new ResolutionResult("g:a:jar:1", repository, pkg);
		var b = new ResolutionResult("g:b:jar:1", repository, pkg);
		assertNotEquals(a, b);
		assertNotEquals(a.hashCode(), b.hashCode());
	}

	@Test
	public void equalityDiffRepo() {
		var gapv = "g:a:jar:1";
		var pkg = inM2("...", "xyz.jar");
		var a = new ResolutionResult(gapv, "http://somewhere/", pkg);
		var b = new ResolutionResult(gapv, "http://elsewhere/", pkg);
		assertNotEquals(a, b);
		assertNotEquals(a.hashCode(), b.hashCode());
	}

	@Test
	public void equalityDifFile() {
		var gapv = "g:a:jar:1";
		var repository = "http://somewhere/";
		var a = new ResolutionResult(gapv, repository, inM2("...", "xyz.jar"));
		var b = new ResolutionResult(gapv, repository, inM2("...", "abc.jar"));
		assertNotEquals(a, b);
		assertNotEquals(a.hashCode(), b.hashCode());
	}

	@Test
	public void hasToStringImpl() {
		var actual = new ResolutionResult("g:a:jar:1", "http://somewhere/", inM2("...", "xyz.jar")).toString();
		assertTrue(actual.contains("\n"));
		var l1 = actual.split("\n")[0];
		assertTrue(l1.contains(ResolutionResult.class.getSimpleName()));
		assertTrue(l1.contains("@"));
		assertTrue(actual.contains("localPomFile"));
	}

	private static File inM2(String... pathToFilename) {
		var m2 = MavenRepositoryUtils.getPathOfLocalRepository().getAbsolutePath();
		return Path.of(m2, pathToFilename).toFile();
	}

	private static File inTmp(String... pathToFilename) {
		var tmp = System.getProperty("java.io.tmpdir");
		return Path.of(tmp, pathToFilename).toFile();
	}
}