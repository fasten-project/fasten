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

import static java.lang.String.format;

import java.util.HashSet;
import java.util.function.Consumer;

import org.apache.maven.model.Model;

import eu.fasten.analyzer.pomanalyzer.data.PomAnalysisResult;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Exclusion;

public class PomExtractor {

	public PomAnalysisResult process(Model model) {
		var r = new PomAnalysisResult();

		r.version = model.getVersion();
		r.artifactId = model.getArtifactId();
		r.groupId = model.getGroupId();
		r.packagingType = model.getPackaging();

		r.projectName = model.getName();

		ifNotNull(model.getParent(), p -> {
			var g = $(p.getGroupId(), "?");
			var a = $(p.getArtifactId(), "?");
			var v = $(p.getVersion(), "?");
			r.parentCoordinate = format("%s:%s:pom:%s", g, a, v);
		});

		ifNotNull(model.getDependencies(), deps -> {
			deps.forEach(dep -> {
				var g = $(dep.getGroupId(), "?");
				var a = $(dep.getArtifactId(), "?");
				var v = $(dep.getVersion(), "?");
				var p = $(dep.getType(), "jar");
				var s = $(dep.getScope(), "compile");
				var o = bool(dep.getOptional(), false);
				var c = $(dep.getClassifier(), "");

				var exclusions = new HashSet<Exclusion>();
				ifNotNull(dep.getExclusions(), excls -> {
					excls.forEach(excl -> {
						var eg = $(excl.getGroupId(), "?");
						var ea = $(excl.getArtifactId(), "?");
						exclusions.add(new Exclusion(eg, ea));
					});
				});

				var d = new Dependency(g, a, v, exclusions, s, o, p, c);
				r.dependencies.add(d);
			});
		});

		// TODO continue

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

		return r;
	}

	private static boolean bool(String s, boolean defaultValue) {
		if (s == null) {
			return defaultValue;
		}
		return "true".equals(s.trim());
	}

	private static <T> T $(T obj, T defaultValue) {
		return obj != null ? obj : defaultValue;
	}

	private static <T> void ifNotNull(T obj, Consumer<T> consumer) {
		if (obj != null) {
			consumer.accept(obj);
		}
	}
}