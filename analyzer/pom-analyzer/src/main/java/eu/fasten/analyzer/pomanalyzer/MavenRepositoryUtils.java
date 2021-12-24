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
package eu.fasten.analyzer.pomanalyzer;

import static eu.fasten.core.utils.Asserts.assertNotNull;
import static java.io.File.createTempFile;
import static org.apache.commons.io.FileUtils.copyURLToFile;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import eu.fasten.analyzer.pomanalyzer.data.ResolutionResult;

public class MavenRepositoryUtils {

	public File downloadPomToTemp(ResolutionResult artifact) {
		assertNotNull(artifact);
		try {
			var source = new URL(artifact.getPomUrl());
			File target = createTempFile("pom-analyzer.", ".pom");
			copyURLToFile(source, target);
			return target;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}