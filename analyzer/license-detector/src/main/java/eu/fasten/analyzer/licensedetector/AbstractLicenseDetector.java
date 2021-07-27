package eu.fasten.analyzer.licensedetector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fasten.analyzer.licensedetector.exceptions.LicenseDetectorException;
import eu.fasten.analyzer.licensedetector.license.DetectedLicense;
import eu.fasten.analyzer.licensedetector.license.DetectedLicenseSource;
import eu.fasten.analyzer.licensedetector.license.DetectedLicenses;
import eu.fasten.analyzer.licensedetector.utils.Scancode;

public abstract class AbstractLicenseDetector implements ILicenseDetector {
	
    private final Logger logger = LoggerFactory.getLogger(AbstractLicenseDetector.class.getName());

	@Override
	public abstract DetectedLicenses detect(String repoPath, String repoUrl) throws LicenseDetectorException;
	
	
	protected JSONArray detectFileLicenses(String repoPath) throws LicenseDetectorException {
		try {
			String resultPath = Scancode.scanProject(repoPath);
			JSONArray files = Scancode.parseScanResult(resultPath);
			return files;
		} catch (IOException | InterruptedException | RuntimeException e) {
			e.printStackTrace();
			throw new LicenseDetectorException(e);
		}
	}

	protected Set<DetectedLicense> detectOutboundLicenses(String repoPath, List<String> licenseFileNames, JSONArray files) {
		for( String licenseFile : licenseFileNames) {
			Set<DetectedLicense> outbound =  getOutboundLicenses(repoPath, licenseFile, files);
			if(outbound != null && !outbound.isEmpty())
				return outbound;
		}
		return null;
	}
	
	// FIXME: DetectedLicenseSource.LOCAL_POM
	protected Set<DetectedLicense> getOutboundLicenses(String repoPath, String licenseFileName, JSONArray files) {
		String outFile = Paths.get(repoPath, licenseFileName).toString();
		var it = files.iterator();
		while(it.hasNext()) {
			JSONObject f = (JSONObject)it.next();
			if(outFile.equalsIgnoreCase(f.getString("path"))) {
				JSONArray licenses = f.getJSONArray("licenses");
				Set<DetectedLicense> result = licenses.toList().stream().map( l -> {
					@SuppressWarnings("unchecked")
					Map<String,Object> o = (Map<String,Object>)l;
					return new DetectedLicense(o.get("spdx_license_key").toString(), DetectedLicenseSource.LOCAL_POM);
				}).collect(Collectors.toSet());
				return result;
			}
		}
		return null;
	}		
	
	
    protected Set<DetectedLicense> detectOutboundLicensesFromGitHub(String repoUrl) {
		try {
			DetectedLicense outboundLicense = getLicenseFromGitHub(repoUrl);
			Set<DetectedLicense> outbound = new HashSet<DetectedLicense>();
			outbound.add(outboundLicense);
			return outbound;
		} catch (IllegalArgumentException | IOException e) {
			e.printStackTrace();
		}
		return null;
    }
	
    /**
     * Retrieves the outbound license of a GitHub project using its API.
     *
     * @param repoUrl the repository URL whose license is of interest.
     * @return the outbound license retrieved from GitHub's API.
     * @throws IllegalArgumentException in case the repository is not hosted on GitHub.
     * @throws IOException              in case there was a problem contacting the GitHub API.
     */
    protected DetectedLicense getLicenseFromGitHub(String repoUrl)
            throws IllegalArgumentException, IOException {

        // Adding "https://" in case it's missing
        if (!Pattern.compile(Pattern.quote("http"), Pattern.CASE_INSENSITIVE).matcher(repoUrl).find()) {
            repoUrl = "https://" + repoUrl;
        }

        // Checking whether the repo URL is a valid URL or not
        URL parsedRepoUrl;
        try {
            parsedRepoUrl = new URL(repoUrl);
        } catch (MalformedURLException e) {
            throw new MalformedURLException("Repo URL " + repoUrl + " is not a valid URL: " + e.getMessage());
        }

        // Checking whether the repo is hosted on GitHub
        if (!Pattern.compile(Pattern.quote("github"), Pattern.CASE_INSENSITIVE).matcher(repoUrl).find()) {
            throw new IllegalArgumentException("Repo URL " + repoUrl + " is not hosted on GitHub.");
        }

        // Parsing the GitHub repo URL
        String path = parsedRepoUrl.getPath();
        String[] splitPath = path.split("/");
        if (splitPath.length < 3) { // should be: ["/", "owner", "repo"]
            throw new MalformedURLException(
                    "Repo URL " + repoUrl + " has no valid path: " + Arrays.toString(splitPath));
        }
        String owner = splitPath[1];
        String repo = splitPath[2].replaceAll(".git", "");
        logger.info("Retrieving outbound license from GitHub. Owner: " + owner + ", repo: " + repo + ".");

        // Result
        DetectedLicense repoLicense;

        // Querying the GitHub API
        try {

            // Format: "https://api.github.com/repos/`owner`/`repo`/license"
            URL url = new URL("https://api.github.com/repos/" + owner + "/" + repo + "/license");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("HTTP query failed. Error code: " + conn.getResponseCode());
            }
            InputStreamReader in = new InputStreamReader(conn.getInputStream());
            BufferedReader br = new BufferedReader(in);
            String jsonOutput = br.lines().collect(Collectors.joining());

            // Retrieving the license SDPX ID
            var jsonOutputPayload = new JSONObject(jsonOutput);
            if (jsonOutputPayload.has("license")) {
                jsonOutputPayload = jsonOutputPayload.getJSONObject("license");
            }
            repoLicense = new DetectedLicense(jsonOutputPayload.getString("spdx_id"), DetectedLicenseSource.GITHUB);

            conn.disconnect();
        } catch (ProtocolException e) {
            throw new ProtocolException(
                    "Couldn't set the GET method while retrieving an outbound license from GitHub: " +
                            e.getMessage());
        } catch (IOException e) {
            throw new IOException(
                    "Couldn't get data from the HTTP response returned by GitHub's API: " + e.getMessage(),
                    e.getCause());
        }

        return repoLicense;
    }
}
