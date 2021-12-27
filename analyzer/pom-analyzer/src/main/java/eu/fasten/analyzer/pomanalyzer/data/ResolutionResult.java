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

import static org.apache.commons.lang3.builder.ToStringStyle.MULTI_LINE_STYLE;

import java.io.File;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import eu.fasten.analyzer.pomanalyzer.utils.MavenRepositoryUtils;

public class ResolutionResult {

	public final File localM2Repository; 

	public String coordinate; // gid:aid:packageType:version
	public String artifactRepository;
	public File localPomFile;
	
	public ResolutionResult(String coordinate, String artifactRepository, File localPkg) {
		this.localM2Repository = getLocalM2Repository();
		this.coordinate = coordinate;
		this.artifactRepository = artifactRepository;
		if (!artifactRepository.endsWith(File.separator)) {
			this.artifactRepository += File.separator;
		}
		// pkg can be .pom, .jar, .war, ... all of them have a corresponding .pom
		this.localPomFile = changeExtension(localPkg, ".pom");
	}

	protected File getLocalM2Repository() {
		return MavenRepositoryUtils.getPathOfLocalRepository();
	}

	public String getPomUrl() {
		// TODO check whether this works for windows
		String f = localPomFile.toString();
		String pathM2 = localM2Repository.getAbsolutePath();
		if (!f.startsWith(pathM2)) {
			var msg = "instead of local .m2 folder, file is contained in '%s'";
			throw new IllegalStateException(String.format(msg, f));
		}
		// also remove path separator (+1)
		return artifactRepository + f.substring(pathM2.length() + 1);
	}

	public File getLocalPackageFile() {
		var packaging = coordinate.split(":")[2];
		return changeExtension(localPomFile, '.' + packaging);
	}

	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, MULTI_LINE_STYLE);
	}

	private static File changeExtension(File f, String extInclDot) {
		String path = f.getAbsolutePath();
		int idxOfExt = path.lastIndexOf('.');
		String newPath = path.substring(0, idxOfExt) + extInclDot;
		return new File(newPath);
	}
}