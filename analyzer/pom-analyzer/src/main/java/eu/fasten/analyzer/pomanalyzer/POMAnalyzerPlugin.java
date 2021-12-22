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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.json.JSONException;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import eu.fasten.analyzer.pomanalyzer.data.PomAnalysisResult;
import eu.fasten.analyzer.pomanalyzer.data.ResolutionResult;
import eu.fasten.core.data.Constants;
import eu.fasten.core.plugins.AbstractKafkaPlugin;
import eu.fasten.core.plugins.DBConnector;

public class POMAnalyzerPlugin extends Plugin {

	public POMAnalyzerPlugin(PluginWrapper wrapper) {
		super(wrapper);
	}

	@Extension
	public static class POMAnalyzer extends AbstractKafkaPlugin implements DBConnector {

		private final Downloader downloader = new Downloader();
		private final PomExtractor extractor = new PomExtractor();
		private final DBStorage store = new DBStorage();
		private Resolver resolver = new Resolver();

		private List<PomAnalysisResult> results = null;

		@Override
		public void setDBConnection(Map<String, DSLContext> dslContexts) {
			var myContext = dslContexts.get(Constants.mvnForge);
			store.setDslContext(myContext);

			// TODO still default, replace with actual check
			resolver.setExistenceCheck(s -> false);
		}

		private void beforeConsume() {
			pluginError = null;
			results = new LinkedList<>();
		}

		@Override
		public void consume(String record) {
			beforeConsume();

			var artifact = parseInput(record);
			artifact.localPomFile = downloader.downloadPomToTemp(artifact);

			process(artifact);
			var deps = resolver.resolveDependenciesFromPom(artifact.localPomFile);
			deps.forEach(dep -> {
				process(dep);
			});
		}

		private static ResolutionResult parseInput(String record) {
			try {
				var json = new JSONObject(record);
				if (json.has("payload")) {
					throw new RuntimeException(
							"This seems to be a relict of the past. If the error is raised, fix the PomAnalyzerplugin.consume method.");
				}

				var groupId = json.getString("groupId").replaceAll("[\\n\\t ]", "");
				var artifactId = json.getString("artifactId").replaceAll("[\\n\\t ]", "");
				var version = json.getString("version").replaceAll("[\\n\\t ]", "");
				var coord = asMavenCoordinate(groupId, artifactId, version);

				var artifactRepository = json.getString("artifactRepository").replaceAll("[\\n\\t ]", "");
				return new ResolutionResult(coord, artifactRepository, null);

			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
		}

		private static String asMavenCoordinate(String groupId, String artifactId, String version) {
			// packing type is unknown
			return String.format("%s:%s:?:%s", groupId, artifactId, version);
		}

		private void process(ResolutionResult artifact) {
			var result = extractor.process(artifact.localPomFile, artifact.artifactRepository);
			results.add(result);
		}

		@Override
		public List<SingleRecord> produceMultiple() {
			var res = new LinkedList<SingleRecord>();
			for (var data : results) {
				res.add(serialize(data));
			}
			store.saveAll(results);
			return res;
		}

		private static SingleRecord serialize(PomAnalysisResult d) {
			// TODO replace with GSON call
			var json = new JSONObject();
			json.put("artifactId", d.artifact);
			json.put("groupId", d.group);
			json.put("version", d.version);
			json.put("packagingType", d.packagingType);

			json.put("date", d.date);
			json.put("repoUrl", (d.repoUrl != null) ? d.repoUrl : "");
			json.put("commitTag", (d.commitTag != null) ? d.commitTag : "");
			json.put("sourcesUrl", d.sourcesUrl);
			json.put("projectName", (d.projectName != null) ? d.projectName : "");
			json.put("parentCoordinate", (d.parentCoordinate != null) ? d.parentCoordinate : "");
			json.put("dependencyData", d.dependencyData.toJSON());
			json.put("forge", Constants.mvnForge);
			json.put("artifactRepository", d.artifactRepository);

			var res = new SingleRecord();
			res.payload = json.toString();
			res.outputPath = getOutputPath(d);

			return res;
		}

		private static String getOutputPath(PomAnalysisResult d) {
			return File.separator + d.group.charAt(0) + File.separator + d.group + File.separator + d.artifact
					+ File.separator + d.version + ".json";
		}
	}
}