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

import static eu.fasten.core.maven.utils.MavenUtilities.MAVEN_CENTRAL_REPO;
import static eu.fasten.core.utils.Asserts.assertNotNull;

import java.sql.Timestamp;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.json.JSONArray;
import org.json.JSONObject;

import eu.fasten.analyzer.pomanalyzer.data.PomAnalysisResult;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.MetadataDao;

public class DatabaseUtils {

	private DSLContext context;
	private MetadataDao metadataDao;

	private boolean processedRecord;

	public void setDslContext(DSLContext context) {
		this.context = context;
		this.metadataDao = new MetadataDao(context);
	}

	public void save(PomAnalysisResult result) {
		processedRecord = false;
		int numTries = 0;
		while (!processedRecord && numTries < Constants.transactionRestartLimit) {
			numTries++;
			context.transaction(transaction -> {
				metadataDao.setContext(DSL.using(transaction));
				try {
					insertIntoDB(result);
					processedRecord = true;
				} catch (DataAccessException e) {
					// Can be happen. Normally fixable through retrying.
				}
			});
		}
	}
	
	public boolean doesPackageExistInDb(String gapv) {
		// gid:aid:packaging:version
		return false;
	}

	public void insertIntoDB(PomAnalysisResult r) {
		String product = r.groupId + Constants.mvnCoordinateSeparator + r.artifactId;
		final var packageId = metadataDao.insertPackage(product, Constants.mvnForge, r.projectName, r.repoUrl, null);

		var pvMeta = toJson(r);
//		pvMeta.put("dependencyManagement",
//				(r.dependencyManagement != null) ? new JSONArray(r.dependencyManagement) : null);
//		pvMeta.put("dependencies",
//				(r.dependencies != null) ? new JSONArray(r.dependencies) : null);
//		pvMeta.put("commitTag", (r.commitTag != null) ? r.commitTag : "");
//		pvMeta.put("sourcesUrl", (r.sourcesUrl != null) ? r.sourcesUrl : "");
//		pvMeta.put("packagingType", (r.packagingType != null) ? r.packagingType : "");
//		pvMeta.put("parentCoordinate", (r.parentCoordinate != null) ? r.parentCoordinate : "");

		assertNotNull(r.artifactRepository);

		var isMavenCentral = MAVEN_CENTRAL_REPO.equals(r.artifactRepository);
		long artifactRepoId = isMavenCentral ? -1L : metadataDao.insertArtifactRepository(r.artifactRepository);

		// TODO: Why is the opalGenerator required here??
		final var packageVersionId = metadataDao.insertPackageVersion(packageId, Constants.opalGenerator, r.version,
				artifactRepoId, null, getProperTimestamp(r.releaseDate), pvMeta);

		for (var dep : r.dependencies) {
			var depProduct = dep.groupId + Constants.mvnCoordinateSeparator + dep.artifactId;
			final var depId = metadataDao.insertPackage(depProduct, Constants.mvnForge);
			metadataDao.insertDependency(packageVersionId, depId, dep.getVersionConstraints(), null, null, null,
					dep.toJSON());
		}
	}

	private static Timestamp getProperTimestamp(long timestamp) {
		if (timestamp == -1) {
			return null;
		} else {
			if (timestamp / (1000L * 60 * 60 * 24 * 365) < 1L) {
				return new Timestamp(timestamp * 1000);
			} else {
				return new Timestamp(timestamp);
			}
		}
	}

	public static JSONObject toJson(PomAnalysisResult d) {
		var json = new JSONObject();

		assertNotNull(d.forge);
		assertNotNull(d.artifactRepository);
		assertNotNull(d.groupId);
		assertNotNull(d.artifactId);
		assertNotNull(d.version);
		assertNotNull(d.packagingType);
		assertNotNull(d.dependencies);
		assertNotNull(d.dependencyManagement);
		assertNotNull(d.sourcesUrl);
		// TODO add remaining asserts

		json.put("forge", d.forge);
		json.put("artifactRepository", d.artifactRepository);

		json.put("groupId", d.groupId);
		json.put("artifactId", d.artifactId);
		json.put("version", d.version);
		json.put("packagingType", d.packagingType);

		json.put("parentCoordinate", (d.parentCoordinate != null) ? d.parentCoordinate : "");

		json.put("dependencies", new JSONArray(d.dependencies).toString());
		json.put("dependencyManagement", new JSONArray(d.dependencyManagement).toString());

		json.put("resolvedCompileAndRuntimeDependencies",
				new JSONArray(d.resolvedCompileAndRuntimeDependencies).toString());

		json.put("date", d.releaseDate);
		json.put("repoUrl", (d.repoUrl != null) ? d.repoUrl : "");
		json.put("commitTag", (d.commitTag != null) ? d.commitTag : "");
		json.put("sourcesUrl", d.sourcesUrl);
		json.put("projectName", (d.projectName != null) ? d.projectName : "");

		return json;
	}
}