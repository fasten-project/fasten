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
import static java.util.stream.IntStream.range;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.impl.maven.MavenResolvedArtifactImpl;

import eu.fasten.analyzer.pomanalyzer.data.ResolutionResult;

public class Resolver {

	// default implementation assumes nothing exists in DB yet
	private Predicate<String> funDbExists = s -> false;

	public void setExistenceCheck(Predicate<String> funDbExists) {
		this.funDbExists = funDbExists;
	}

	public Set<ResolutionResult> resolveDependenciesFromPom(File pom) {
		var coordToResult = new HashMap<String, ResolutionResult>();

		// two iterations: 0) resolving and (potential) deletion 1) get artifactRepos
		range(0, 2).forEach(i -> {
			resolvePom(pom).forEach(res -> {
				// ignore known dependencies
				if (coordToResult.containsKey(res.coordinate) || funDbExists.test(res.coordinate)) {
					return;
				}

				// store artifactRepositories, if found
				if (res.artifactRepository.startsWith("http")) {
					coordToResult.put(res.coordinate, res);
					return;
				}

				// locally existing pkgs must be deleted and re-downloaded (which automatically
				// happens in the second iteration) or the local version is used from which the
				// artifact repository cannot be inferred anymore
				if (i == 0) {
					File f = res.getLocalPackageFile();
					System.out.println(f);
					if (f.exists() && f.isFile()) {
						f.delete();
					}
				} else {
					String msg = "Cannot find artifactRepository for %s.";
					throw new IllegalStateException(format(msg, res.coordinate));
				}
			});
		});

		return new HashSet<>(coordToResult.values());
	}

	private static Set<ResolutionResult> resolvePom(File f) {
		var res = new HashSet<ResolutionResult>();
		MavenResolvedArtifactImpl.artifactRepositories = res;
		Maven.resolver() //
				.loadPomFromFile(f) //
				.importCompileAndRuntimeDependencies() //
				.resolve() //
				.withTransitivity() //
				.asResolvedArtifact();
		MavenResolvedArtifactImpl.artifactRepositories = null;
		return res;
	}
}