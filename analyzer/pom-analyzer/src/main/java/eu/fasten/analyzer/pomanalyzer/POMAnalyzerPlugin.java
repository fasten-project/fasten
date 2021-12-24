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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.maven.model.Model;
import org.jooq.DSLContext;
import org.json.JSONException;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

import eu.fasten.analyzer.pomanalyzer.data.PomAnalysisResult;
import eu.fasten.analyzer.pomanalyzer.data.ResolutionResult;
import eu.fasten.analyzer.pomanalyzer.utils.DatabaseUtils;
import eu.fasten.analyzer.pomanalyzer.utils.EffectiveModelBuilder;
import eu.fasten.analyzer.pomanalyzer.utils.MavenRepositoryUtils;
import eu.fasten.analyzer.pomanalyzer.utils.PomExtractor;
import eu.fasten.analyzer.pomanalyzer.utils.Resolver;
import eu.fasten.core.data.Constants;
import eu.fasten.core.plugins.AbstractKafkaPlugin;
import eu.fasten.core.plugins.DBConnector;

public class POMAnalyzerPlugin extends Plugin {

	public POMAnalyzerPlugin(PluginWrapper wrapper) {
		super(wrapper);
	}

	@Extension
	public static class POMAnalyzer extends AbstractKafkaPlugin implements DBConnector {

		private final MavenRepositoryUtils downloader = new MavenRepositoryUtils();
		private final EffectiveModelBuilder modelBuilder = new EffectiveModelBuilder();
		private final PomExtractor extractor = new PomExtractor();
		private final DatabaseUtils db = new DatabaseUtils();
		private Resolver resolver = new Resolver();

		private List<PomAnalysisResult> results = null;

		@Override
		public void setDBConnection(Map<String, DSLContext> dslContexts) {
			var myContext = dslContexts.get(Constants.mvnForge);
			db.setDslContext(myContext);
			resolver.setExistenceCheck(db::doesPackageExistInDb);
		}

		private void beforeConsume() {
			pluginError = null;
			results = new LinkedList<>();
		}

		@Override
		public void consume(String record) {
			beforeConsume();

			var artifact = bootstrapFirstResolutionResultFromInput(record);
			artifact.localPomFile = downloader.downloadPomToTemp(artifact);

			process(artifact);
		}

		private static ResolutionResult bootstrapFirstResolutionResultFromInput(String record) {
			try {
				var json = new JSONObject(record);

				// TODO remove this if-block if message does not occur in the log
				if (json.has("payload")) {
					String msg = "This seems to be a relict of the past. If the error is raised, fix the PomAnalyzerplugin.consume method.";
					throw new RuntimeException(msg);
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

			// resolve dependencies to
			// 1) have dependencies
			// 2) identify artifact sources
			// 3) make sure all dependencies exist in local .m2 folder
			var deps = resolver.resolveDependenciesFromPom(artifact.localPomFile);

			// merge pom with all its parents and resolve properties
			Model m = modelBuilder.buildEffectiveModel(artifact.localPomFile);

			// extract contents of pom file
			var result = extractor.process(m);

			// remember source repository for artifact
			result.artifactRepository = artifact.artifactRepository;
			
			result.sourcesUrl = downloader.getSourceUrlIfExisting(result);
			result.releaseDate = downloader.getReleaseDate(result);

			// remember concrete resolution result
			result.resolvedCompileAndRuntimeDependencies.addAll(deps.stream() //
					.map(r -> r.coordinate) //
					.collect(Collectors.toSet()));

			results.add(result);
			db.save(result);

			// resolution can be different for dependencies, so process them independently
			deps.forEach(dep -> {
				process(dep);
			});
		}

		@Override
		public List<SingleRecord> produceMultiple() {
			var res = new LinkedList<SingleRecord>();
			for (var data : results) {
				res.add(serialize(data));
			}
			return res;
		}

		private static SingleRecord serialize(PomAnalysisResult d) {

			var res = new SingleRecord();
			res.payload = DatabaseUtils.toJson(d).toString();
			res.outputPath = getRelativeOutputPath(d);

			return res;
		}

		private static String getRelativeOutputPath(PomAnalysisResult d) {
			final var sb = new StringBuilder();
			Arrays.stream(d.groupId.split("\\.")).forEach(p -> {
				sb.append(p).append(File.separator);
			});
			sb.append(d.artifactId).append(File.separator);
			sb.append(d.version).append(File.separator);
			sb.append(d.artifactId).append('-').append(d.version).append(".pomanalyzer.json");
			return sb.toString();
		}
	}
}