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

import java.io.File;

public class ResolutionResult {

	// TODO differentiate between package type and pom
	public String coordinate;
	public String artifactRepository;
	public File filePkg;
	public File filePom;

	public ResolutionResult(String coordinate, String source, File file) {
		this.coordinate = coordinate;
		this.artifactRepository = source;
		this.filePkg = file;
		this.filePom = pomify(file);
	}

	private static File pomify(File pkg) {
		// TODO implement me
		return pkg;
	}

	// TODO How to make this work under Windows?
	public String getPomUrl() {
		String f = filePkg.toString();
		String marker = "/.m2/repository/";
		int idx = f.indexOf(marker);
		int idxOfExt = f.lastIndexOf('.');
		return artifactRepository + f.substring(idx + marker.length() - 1, idxOfExt) + ".pom";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((coordinate == null) ? 0 : coordinate.hashCode());
		result = prime * result + ((filePkg == null) ? 0 : filePkg.hashCode());
		result = prime * result + ((artifactRepository == null) ? 0 : artifactRepository.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ResolutionResult other = (ResolutionResult) obj;
		if (coordinate == null) {
			if (other.coordinate != null)
				return false;
		} else if (!coordinate.equals(other.coordinate))
			return false;
		if (filePkg == null) {
			if (other.filePkg != null)
				return false;
		} else if (!filePkg.equals(other.filePkg))
			return false;
		if (artifactRepository == null) {
			if (other.artifactRepository != null)
				return false;
		} else if (!artifactRepository.equals(other.artifactRepository))
			return false;
		return true;
	}
}