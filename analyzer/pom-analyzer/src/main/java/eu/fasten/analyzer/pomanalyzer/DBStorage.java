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

import static eu.fasten.core.maven.utils.MavenUtilities.MAVEN_CENTRAL_REPO;
import static eu.fasten.core.utils.Asserts.assertNotNull;

import java.sql.Timestamp;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.json.JSONArray;
import org.json.JSONObject;

import eu.fasten.analyzer.pomanalyzer.data.PomAnalysisResult;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.MetadataDao;

public class DBStorage {

	private DSLContext context;
	private MetadataDao metadataDao;

	private boolean processedRecord;

	public void setDslContext(DSLContext context) {
		this.context = context;
		this.metadataDao = new MetadataDao(context);
	}

	public void saveAll(List<PomAnalysisResult> results) {
		for (var sr : results) {
			save(sr);
		}
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

	public void insertIntoDB(PomAnalysisResult r) {
		String product = r.group + Constants.mvnCoordinateSeparator + r.artifact;
		final var packageId = metadataDao.insertPackage(product, Constants.mvnForge, r.projectName, r.repoUrl, null);

		var pvMeta = new JSONObject();
		pvMeta.put("dependencyManagement",
				(r.dependencyData.dependencyManagement != null) ? r.dependencyData.dependencyManagement.toJSON()
						: null);
		pvMeta.put("dependencies",
				(r.dependencyData.dependencies != null) ? new JSONArray(r.dependencyData.dependencies) : null);
		pvMeta.put("commitTag", (r.commitTag != null) ? r.commitTag : "");
		pvMeta.put("sourcesUrl", (r.sourcesUrl != null) ? r.sourcesUrl : "");
		pvMeta.put("packagingType", (r.packagingType != null) ? r.packagingType : "");
		pvMeta.put("parentCoordinate", (r.parentCoordinate != null) ? r.parentCoordinate : "");

		assertNotNull(r.artifactRepository);

		var isMavenCentral = MAVEN_CENTRAL_REPO.equals(r.artifactRepository);
		long artifactRepoId = isMavenCentral ? -1L : metadataDao.insertArtifactRepository(r.artifactRepository);

		// TODO: Why is the opalGenerator required here??
		final var packageVersionId = metadataDao.insertPackageVersion(packageId, Constants.opalGenerator, r.version,
				artifactRepoId, null, getProperTimestamp(r.date), pvMeta);

		for (var dep : r.dependencyData.dependencies) {
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
}