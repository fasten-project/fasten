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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import eu.fasten.analyzer.pomanalyzer.data.PomAnalysisResult;

public class PomExtractor {

	public PomAnalysisResult process(File localPomFile, String artifactRepository) {

		var model = parsePom(localPomFile);
		model.getVersion();
		model.getArtifactId();
		model.getPackaging();
		// TODO continue

		var data = new PomAnalysisResult();

		data.artifactRepository = artifactRepository;

		// see
		// https://riptutorial.com/maven/example/30104/reading-a-pom-xml-at-runtime-using-maven-model-plugin

//		data.artifact = payload.getString("artifactId").replaceAll("[\\n\\t ]", "");
//		data.group = payload.getString("groupId").replaceAll("[\\n\\t ]", "");
//		data.packagingType = dataExtractor.extractPackagingType(data.group, data.artifact, data.version);
//		data.version = payload.getString("version").replaceAll("[\\n\\t ]", "");
//
//		var pomUrl = "TODO";
//		var mavenCoordinate = dataExtractor.getMavenCoordinate(pomUrl);
//		if (mavenCoordinate != null && !mavenCoordinate.contains("${")) {
//			String[] parts = mavenCoordinate.split(Constants.mvnCoordinateSeparator);
//			data.group = parts[0];
//			data.artifact = parts[1];
//			data.version = parts[2];
//		}
//		data.date = dataExtractor.extractReleaseDate(data.group, data.artifact, data.version, data.artifactRepository);
//		data.repoUrl = dataExtractor.extractRepoUrl(data.group, data.artifact, data.version);
//		data.dependencyData = dataExtractor.extractDependencyData(data.group, data.artifact, data.version);
//		data.commitTag = dataExtractor.extractCommitTag(data.group, data.artifact, data.version);
//		data.sourcesUrl = dataExtractor.generateMavenSourcesLink(data.group, data.artifact, data.version);
//		data.projectName = dataExtractor.extractProjectName(data.group, data.artifact, data.version);
//		data.parentCoordinate = dataExtractor.extractParentCoordinate(data.group, data.artifact, data.version);

		return data;

	}

	private Model parsePom(File localPomFile) {
		try {
			MavenXpp3Reader reader = new MavenXpp3Reader();
			Model model = reader.read(new FileReader(localPomFile));
			return model;
		} catch (IOException | XmlPullParserException e) {
			throw new RuntimeException(e);
		}
	}
}