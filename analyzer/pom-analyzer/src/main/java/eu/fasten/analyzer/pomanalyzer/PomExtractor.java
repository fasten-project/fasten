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

import org.json.JSONException;
import org.json.JSONObject;

import eu.fasten.analyzer.pomanalyzer.data.PomAnalysisResult;
import eu.fasten.analyzer.pomanalyzer.data.ResolutionResult;
import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.utils.MavenUtilities;

public class PomExtractor {


	// TODO add sha or md5 hash
	
	public PomAnalysisResult process(ResolutionResult c) {

		var data = new PomAnalysisResult();

		var jsonRecord = new JSONObject("");
		var payload = new JSONObject();
		if (jsonRecord.has("payload")) {
			payload = jsonRecord.getJSONObject("payload");
		} else {
			payload = jsonRecord;
		}
		try {
			data.artifact = payload.getString("artifactId").replaceAll("[\\n\\t ]", "");
			data.group = payload.getString("groupId").replaceAll("[\\n\\t ]", "");
			data.version = payload.getString("version").replaceAll("[\\n\\t ]", "");
			data.date = payload.optLong("date", -1L);
			data.artifactRepository = payload.optString("artifactRepository", MavenUtilities.MAVEN_CENTRAL_REPO);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		var repos = MavenUtilities.getRepos();
		if (!data.artifactRepository.equals(MavenUtilities.MAVEN_CENTRAL_REPO)) {
			repos.addFirst(data.artifactRepository);
		}
		var pomUrl = payload.optString("pomUrl", null);

		final var product = data.group + Constants.mvnCoordinateSeparator + data.artifact
				+ Constants.mvnCoordinateSeparator + data.version;
		var dataExtractor = new DataExtractor(repos);

		if (pomUrl != null) {
			var mavenCoordinate = dataExtractor.getMavenCoordinate(pomUrl);
			if (mavenCoordinate != null && !mavenCoordinate.contains("${")) {
				String[] parts = mavenCoordinate.split(Constants.mvnCoordinateSeparator);
				data.group = parts[0];
				data.artifact = parts[1];
				data.version = parts[2];
			}
		}
		if (data.date == -1) {
			data.date = dataExtractor.extractReleaseDate(data.group, data.artifact, data.version,
					data.artifactRepository);
		}
		data.repoUrl = dataExtractor.extractRepoUrl(data.group, data.artifact, data.version);
		data.dependencyData = dataExtractor.extractDependencyData(data.group, data.artifact, data.version);
		data.commitTag = dataExtractor.extractCommitTag(data.group, data.artifact, data.version);
		data.sourcesUrl = dataExtractor.generateMavenSourcesLink(data.group, data.artifact, data.version);
		data.packagingType = dataExtractor.extractPackagingType(data.group, data.artifact, data.version);
		data.projectName = dataExtractor.extractProjectName(data.group, data.artifact, data.version);
		data.parentCoordinate = dataExtractor.extractParentCoordinate(data.group, data.artifact, data.version);

		return data;

	}
}