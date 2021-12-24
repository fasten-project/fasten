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
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.maven.settings.Settings;
import org.jboss.shrinkwrap.resolver.impl.maven.SettingsManager;

import eu.fasten.analyzer.pomanalyzer.data.PomAnalysisResult;
import eu.fasten.analyzer.pomanalyzer.data.ResolutionResult;
import eu.fasten.core.maven.utils.MavenUtilities;

public class MavenRepositoryUtils {

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

	public String getSourceUrlIfExisting(PomAnalysisResult result) {
		// TODO Auto-generated method stub
		return null;
	}

	public long getReleaseDate(PomAnalysisResult result) {
		// TODO Auto-generated method stub
		return 0;
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

	private long extractReleaseDate(String groupId, String artifactId, String version, String artifactRepo) {
		URLConnection connection;
		try {
			connection = new URL(MavenUtilities.getPomUrl(groupId, artifactId, version, artifactRepo)).openConnection();
		} catch (IOException e) {
			return -1;
		}
		var lastModified = connection.getHeaderField("Last-Modified");
		if (lastModified == null) {
			return -1;
		}
		Date releaseDate;
		try {
			releaseDate = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH).parse(lastModified);
		} catch (ParseException e) {
			return -1;
		}
		return releaseDate.getTime();
	}

	private String generateMavenSourcesLink(String groupId, String artifactId, String version) {
		for (var repo : (Iterable) null) {
			var url = repo + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-"
					+ version + "-sources.jar";
			try {
				int status = sendGetRequest(url);
				if (status == 200) {
					return url;
				} else if (status != 404) {
					break;
				}
			} catch (IOException | InterruptedException e) {
				break;
			}
		}
		return "";
	}

	private int sendGetRequest(String url) throws IOException, InterruptedException {
		var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
		var request = HttpRequest.newBuilder().GET().uri(URI.create(url)).build();
		var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		return response.statusCode();
	}
}