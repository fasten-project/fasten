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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.io.FileUtils.writeStringToFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.javastack.httpd.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.fasten.analyzer.pomanalyzer.data.ResolutionResult;

public class MavenRepositoryUtilsTest {

	private static final String ARTIFACT_REPO = "http://localhost:1234";
	private static final String SOME_COORD = "gid:aid:jar:0.1.2";
	private static final String SOME_CONTENT = "<some content>";

	@TempDir
	private File dirTemp;
	private HttpServer httpd;
	private MavenRepositoryUtils sut;

	@BeforeEach
	public void setup() throws IOException {
		httpd = new HttpServer(1234, dirTemp.getAbsolutePath());
		httpd.start();
		sut = new MavenRepositoryUtils();
	}

	@AfterEach
	public void teardown() {
		httpd.stop();
	}

	@Test
	public void downloadPoms() {
		webContent("some/coord.pom", SOME_CONTENT);

		var f = new File(".../.m2/repository/some/coord.pom");
		var a = new ResolutionResult(SOME_COORD, ARTIFACT_REPO, f);

		String actual = downloadAndRead(a);
		assertEquals(SOME_CONTENT, actual);
	}

	@Test
	public void downloadPomsForJars() {
		webContent("some/coord.pom", SOME_CONTENT);

		var f = new File(".../.m2/repository/some/coord.jar");
		var a = new ResolutionResult(SOME_COORD, ARTIFACT_REPO, f);

		String actual = downloadAndRead(a);
		assertEquals(SOME_CONTENT, actual);
	}

	@Test
	public void nonExistingPom() {
		var e = assertThrows(RuntimeException.class, () -> {
			var f = new File(".../.m2/repository/some/coord.pom");
			var a = new ResolutionResult(SOME_COORD, ARTIFACT_REPO, f);
			sut.downloadPomToTemp(a);
		});
		assertTrue(e.getCause() instanceof FileNotFoundException);
	}

	@Test
	public void localM2FolderExists() {
		File f = MavenRepositoryUtils.getPathOfLocalRepository();
		assertTrue(f.isAbsolute());
		assertTrue(f.exists());
		assertTrue(f.isDirectory());
	}

	private String downloadAndRead(ResolutionResult a) {
		try {
			File out = sut.downloadPomToTemp(a);
			assertTrue(out.exists());
			assertTrue(out.isFile());
			return readFileToString(out, UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void webContent(String path, String content) {
		try {
			File f = new File(dirTemp, path);
			writeStringToFile(f, content, UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}