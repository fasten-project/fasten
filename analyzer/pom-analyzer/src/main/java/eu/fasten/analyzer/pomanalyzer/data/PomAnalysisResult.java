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

import java.util.Set;

import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.data.Dependency;

public class PomAnalysisResult {

	public String forge = Constants.mvnForge;

	public String artifactId = null;
	public String groupId = null;
	public String packagingType = null;
	public String version = null;
	
	// g:a:packaging:version
	public String parentCoordinate = null;

	public long releaseDate = -1L;
	public String projectName = null;

	public Set<Dependency> dependencies;
	public Set<Dependency> dependencyManagement;
	
	// set(g:a:packaging:version)
	public Set<String> resolvedCompileAndRuntimeDependencies;

	public String repoUrl = null;
	public String commitTag = null;
	public String sourcesUrl = null;
	public String artifactRepository = null;

	// TODO tests
	
	// TODO hashCode + equals
}