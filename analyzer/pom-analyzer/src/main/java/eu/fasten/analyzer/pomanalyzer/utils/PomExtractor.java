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

import static java.lang.String.format;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.maven.model.Model;
import org.apache.maven.model.ModelBase;
import org.apache.maven.model.Profile;

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

		process(model, r);

		ifNotNull(model.getProfiles(), ps -> {
			ps.forEach(p -> {
				if (isActiveByDefault(p)) {
					process(p, r);
				}
			});
		});

		ifNotNull(model.getScm(), scm -> {

			var repo = $(scm.getConnection(), "");
			if (isNullOrEmpty(repo)) {
				repo = $(scm.getDeveloperConnection(), "");
				if (isNullOrEmpty(repo)) {
					repo = $(scm.getUrl(), "");
				}
			}
			if (!isNullOrEmpty(repo)) {
				r.repoUrl = repo;

				var tag = $(scm.getTag(), "");
				if (!isNullOrEmpty(tag)) {
					r.commitTag = tag;
				}
			}
		});

		return r;
	}

	public PomAnalysisResult process(ModelBase model, PomAnalysisResult r) {

		ifNotNull(model.getDependencies(), deps -> process(deps, r.dependencies));
		ifNotNull(model.getDependencyManagement(), dm -> {
			ifNotNull(dm.getDependencies(), deps -> process(deps, r.dependencyManagement));
		});

		return r;
	}

	private void process(List<org.apache.maven.model.Dependency> depsIn, Set<Dependency> depsOut) {
		depsIn.forEach(dep -> {
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
			depsOut.add(d);
		});
	}

	private static boolean isActiveByDefault(Profile p) {
		var act = p.getActivation();
		if (act != null) {
			return act.isActiveByDefault();
		}
		return false;
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

	private static boolean isNullOrEmpty(String s) {
		return s == null || s.isEmpty();
	}
}