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

import eu.fasten.core.maven.data.DependencyData;

public class PomAnalyzerData {
	public String artifact = null;
	public String group = null;
	public String version = null;
	public String packagingType = null;

	public String projectName = null;
	// TODO: rename this to proper name like "releaseDate"
	public long date = -1L;
	public String parentCoordinate = null;

	public DependencyData dependencyData = null;

	public String repoUrl = null;
	public String commitTag = null;
	public String sourcesUrl = null;
	public String artifactRepository = null;
}