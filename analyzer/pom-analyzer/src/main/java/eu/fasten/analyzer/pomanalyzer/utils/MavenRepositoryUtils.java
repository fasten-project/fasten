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

import static eu.fasten.core.utils.Asserts.assertNotNull;
import static java.io.File.createTempFile;
import static org.apache.commons.io.FileUtils.copyURLToFile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import org.apache.maven.settings.Settings;
import org.jboss.shrinkwrap.resolver.impl.maven.SettingsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fasten.analyzer.pomanalyzer.data.PomAnalysisResult;
import eu.fasten.analyzer.pomanalyzer.data.ResolutionResult;
import eu.fasten.core.utils.Asserts;

public class MavenRepositoryUtils {

	private static final Logger LOG = LoggerFactory.getLogger(MavenRepositoryUtils.class);
	private static final String[] ALLOWED_CLASSIFIERS = new String[] { null, "sources" };

	public File downloadPomToTemp(ResolutionResult artifact) {
		assertNotNull(artifact);
		try {
			var source = new URL(artifact.getPomUrl());
			File target = createTempFile("pom-analyzer.", ".pom");
			copyURLToFile(source, target);
			return target;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * returns url of sources jar if it exists, null otherwise
	 */
	public String getSourceUrlIfExisting(PomAnalysisResult r) {

		var url = getUrl(r, "sources");

		int status = sendGetRequest(url);
		if (status == 200) {
			return url;
		} else {
			return null;
		}
	}

	public long getReleaseDate(PomAnalysisResult r) {
		try {
			var url = new URL(getUrl(r, null));
			var con = url.openConnection();
			var lastModified = con.getHeaderField("Last-Modified");
			if (lastModified == null) {
				LOG.error("cannot infer release date, 'Last-Modified' header missing in response for '{}'", url);
				return -1;
			}
			var releaseDate = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH).parse(lastModified);
			return releaseDate.getTime();

		} catch (IOException | ParseException e) {
			throw new RuntimeException(e);
		}
	}

	private static String getUrl(PomAnalysisResult r, String classifier) {
		if (isNullEmptyOrUnset(r.artifactRepository) || isNullEmptyOrUnset(r.groupId)
				|| isNullEmptyOrUnset(r.artifactId) || isNullEmptyOrUnset(r.packagingType)
				|| isNullEmptyOrUnset(r.version)) {
			throw new IllegalArgumentException("cannot build sources URL with missing package information");
		}
		Asserts.assertContains(ALLOWED_CLASSIFIERS, classifier);
		var classifierStr = classifier != null ? "-" + classifier : "";
		var url = r.artifactRepository + "/" + r.groupId.replace('.', '/') + "/" + r.artifactId + "/" + r.version + "/"
				+ r.artifactId + "-" + r.version + classifierStr + "." + r.packagingType;
		return url;
	}

	private static int sendGetRequest(String url) {
		try {
			var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
			var request = HttpRequest.newBuilder().GET().uri(URI.create(url)).build();
			// TODO use "discarding"?
			var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			return response.statusCode();
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private static boolean isNullEmptyOrUnset(String s) {
		return s == null || s.isEmpty() || "?".equals(s);
	}

	public static File getPathOfLocalRepository() {
		// By default, this is set to ~/.m2/repository/, but that can be re-configured
		// or even provided as a parameter. As such, we are reusing an existing library
		// to find the right folder.
		Settings settings = new SettingsManager() {
			@Override
			public Settings getSettings() {
				return super.getSettings();
			}
		}.getSettings();
		String localRepository = settings.getLocalRepository();
		return new File(localRepository);
	}
}