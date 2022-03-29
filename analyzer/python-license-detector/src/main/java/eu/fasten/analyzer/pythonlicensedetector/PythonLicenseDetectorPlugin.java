package eu.fasten.analyzer.pythonlicensedetector;

import com.google.common.collect.Sets;
import eu.fasten.core.data.metadatadb.license.DetectedLicense;
import eu.fasten.core.data.metadatadb.license.DetectedLicenseSource;
import eu.fasten.core.data.metadatadb.license.DetectedLicenses;
import eu.fasten.core.plugins.AbstractKafkaPlugin;
import eu.fasten.core.plugins.KafkaPlugin;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;

import javax.annotation.Nullable;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.concurrent.TimeoutException;


public class PythonLicenseDetectorPlugin extends Plugin {

    public PythonLicenseDetectorPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class PythonLicenseDetectorExtension  extends AbstractKafkaPlugin {
        private final Logger logger = LoggerFactory.getLogger(PythonLicenseDetectorExtension.class.getName());

        private static String packageVersion;
        private static String packageName ;
        private static JSONObject object = new JSONObject();
        private static String str1;

        protected Exception pluginError = null;

        /*
        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(Collections.singletonList(consumerTopic));
        }

        @Override
        public void setTopics(List<String> consumeTopics) {

        }*/

        /**
         * The topic this plugin consumes.
         */
        protected String consumerTopic = "fasten.MetadataDBPythonExtension.out";

        /**
         * TODO
         */
        protected DetectedLicenses detectedLicenses = new DetectedLicenses();

        /**
         * Resets the internal state of this plugin.
         */
        protected void reset() {
            pluginError = null;
            detectedLicenses = new DetectedLicenses();
        }

        @Override
        public void consume(String kafkaRecord, ProcessingLane l) {
            try { // Fasten error-handling guidelines
                reset();
                JSONObject json = new JSONObject(kafkaRecord);
                logger.info("Python license detector started.");

                // Retrieving the package name
                packageName = extractPackageName(json);
                logger.info("The package to analyze is:"+packageName+".");
                // Retrieving the package version
                packageVersion = extractPackageVersion(json);
                logger.info("The package version is:"+packageVersion+".");

                //String GitHubURL =
                JSONObject PypiJSON = new JSONObject();;

                PypiJSON = getJSONFromPypi(packageName, packageVersion);

                findGitHubStringIterate(PypiJSON);

                System.out.println ("GitHubURL:");
                System.out.println (str1);

                String GitHubURL = str1;



                //String PypiLicense = getLicenseFromPypi(PypiJSON);
                //System.out.println ("PypiLicense");
                //System.out.println (PypiLicense);

                // Outbound license detection
                detectedLicenses.setOutbound(getOutboundLicenses(packageName, packageVersion, GitHubURL));
                if (detectedLicenses.getOutbound() == null || detectedLicenses.getOutbound().isEmpty()) {
                    logger.warn("No outbound licenses were detected.");
                } else {
                    logger.info(
                            detectedLicenses.getOutbound().size() + " outbound license" +
                                    (detectedLicenses.getOutbound().size() == 1 ? "" : "s") + " detected: " +
                                    detectedLicenses.getOutbound()
                    );
                }

                String repoPath = findSourcePath(json);
                //repoPath = repoPath.replace("revision-callgraphs/","");
                System.out.println(repoPath);
                // Detecting inbound licenses by scanning the project
                String scanResultPath = scanProject(repoPath);
                // Parsing the result
                JSONArray fileLicenses = parseScanResult(scanResultPath);
                System.out.println("Content of fileLicenses JSONArray");
                System.out.println(fileLicenses);
                if (fileLicenses != null && !fileLicenses.isEmpty()) {
                    detectedLicenses.addFiles(fileLicenses);
                } else {
                    logger.warn("Scanner hasn't detected any licenses in " + scanResultPath + ".");
                }




                //Adding packageName and packageVersion to the out message (object).
                /*JSONObject packageInfo = new JSONObject();
                packageInfo.put("packageName", packageName);
                packageInfo.put("packageVersion", packageVersion);
                // forcing the packageName and packageVersion information into the files JSONArray
                object.accumulate("files", packageInfo);*/

                // Detecting inbound licenses by scanning the project
                //String scanResultPath = scanProject(repoPath);

                // Parsing the result
                /*JSONArray fileLicenses = parseScanResult(scanResultPath);
                if (fileLicenses != null && !fileLicenses.isEmpty()) {
                    detectedLicenses.addFiles(fileLicenses);
                } else {
                    logger.warn("Scanner hasn't detected any licenses in " + scanResultPath + ".");
                }*/

            } catch (Exception e) { // Fasten error-handling guidelines
                logger.error(e.getMessage(), e.getCause());
                setPluginError(e);
            }
        }

        /**
         *
         * Retrieves the package version of the input record.
         *
         * @param json the input record containing the package version information.
         * @return the package version
         * @throws IllegalArgumentException in case the function couldn't find the package version.
         */
        protected static String extractPackageVersion(JSONObject json) throws IllegalArgumentException {
            if (json.has("input")) {
                JSONObject json2 = json.getJSONObject("input");
                if (json2.has("input")) {
                    JSONObject json3 = json2.getJSONObject("input");
                    if (json3.has("version")) {
                        return json3.getString("version");
                    } else {
                        String packageVersionNotFound = "Package version not found";
                        return packageVersionNotFound;
                    }
                }
            }
            return null;
        }

        /**
         *
         * Retrieves the package name of the input record.
         *
         * @param json the input record containing package information.
         * @return the package name.
         */

        protected static String extractPackageName(JSONObject json) {
            if (json.has("input")) {
                JSONObject json2 = json.getJSONObject("input");
                if (json2.has("input")) {
                    JSONObject json3 = json2.getJSONObject("input");
                    if (json3.has("source")) {
                        return json3.getString("source");
                    } else {
                        String packageNameNotFound = "Package name not found";
                        return packageNameNotFound;
                    }
                }
            }
            return null;
        }

        /**
         * Retrieves the outbound license(s) of the input project.
         *
         * @param packageName the package name.
         * @param packageVersion  the package version.
         * @param GitHubURL  the input repository URL. Might be `null`.
         * @return the set of detected outbound licenses.
         */
        protected Set<DetectedLicense> getOutboundLicenses(String packageName, String packageVersion, @Nullable String GitHubURL) throws IOException, TimeoutException {
            //currently excluded the GitHub outbound license detection
            try {
                DetectedLicense licenseFromPypi = getLicenseFromPypi(packageName,packageVersion);
                if (licenseFromPypi != null) {
                    return Sets.newHashSet(licenseFromPypi);
                } else {
                    logger.warn("Couldn't retrieve the outbound license from Pypi.");
                    if (GitHubURL != null){
                        logger.warn("Trying with the GitHub APIs ...");
                        DetectedLicense GitHubLicense = getLicenseFromGitHub(str1);
                        System.out.println(GitHubLicense);

                    }

                }
            } catch (IllegalArgumentException | IOException ex) { // not a valid GitHub repo URL
                    logger.warn("It is not a valid GitHub repo URL");
            } catch (@SuppressWarnings({"TryWithIdenticalCatches", "RedundantSuppression"})
                    RuntimeException ex) {
                logger.warn("Couldn't connect with GitHub APIs"); // could not contact GitHub API
            }

            return Collections.emptySet();
        }

        /**
         * Retrieves the outbound license of a Pypi package using its API.
         *
         * @return the outbound license retrieved from PyPI's API.
         * @throws TimeoutException in case there was a connection timeout.
         * @throws IOException in case there was a problem contacting the PyPI's API.
         */

        protected DetectedLicense getLicenseFromPypi(String packageName, String packageVersion) throws IOException, TimeoutException {
            // Result
            DetectedLicense repoLicense = null;
            try {
                JSONObject result = new JSONObject();
                //response = requests.get("https://pypi.org/pypi/"+packageName+"/"+packageVersion+"/json")
                URL url = new URL("https://pypi.org/pypi/" + packageName + "/" + packageVersion + "/json");
                JSONObject LicenseAndPath = new JSONObject();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                if (conn.getResponseCode() != 200) {
                    throw new RuntimeException("HTTP query failed. Error code: " + conn.getResponseCode());
                }
                InputStreamReader in = new InputStreamReader(conn.getInputStream());
                BufferedReader br = new BufferedReader(in);
                String jsonOutput = br.lines().collect(Collectors.joining());
                // searching for the copyright files in the JSON response
                var jsonOutputPayload = new JSONObject(jsonOutput);
                if (jsonOutputPayload.has("info")) {
                    JSONObject json2 = jsonOutputPayload.getJSONObject("info");
                    if (json2.has("license")) {
                        //return json2.getString("license");
                        json2 = json2.getJSONObject("license");
                    }
                    repoLicense = new DetectedLicense(json2.getString("spdx_id"), DetectedLicenseSource.PYPI);
                }
                conn.disconnect();
                return repoLicense;
            } catch (ProtocolException e) {
                throw new ProtocolException(
                        "Couldn't set the GET method while retrieving an outbound license from GitHub: " +
                                e.getMessage());
            } catch (IOException e) {
                throw new IOException(
                        "Couldn't get data from the HTTP response returned by GitHub's API: " + e.getMessage(),
                        e.getCause());
            }
            //return repoLicense;
        }


        private static JSONObject getJSONFromPypi(String packageName, String packageVersion) throws
                IOException, TimeoutException {
            JSONObject result = new JSONObject();
            //response = requests.get("https://pypi.org/pypi/"+packageName+"/"+packageVersion+"/json")
            URL url = new URL("https://pypi.org/pypi/" + packageName + "/" + packageVersion + "/json");
            System.out.println(url);
            JSONObject LicenseAndPath = new JSONObject();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("HTTP query failed. Error code: " + conn.getResponseCode());
            }
            InputStreamReader in = new InputStreamReader(conn.getInputStream());
            BufferedReader br = new BufferedReader(in);
            String jsonOutput = br.lines().collect(Collectors.joining());
            // searching for the copyright files in the JSON response
            var jsonOutputPayload = new JSONObject(jsonOutput);
            return jsonOutputPayload;
        }

        /* Retrieve PyPI license from PyPI json */
        private static String getLicenseFromPypi(JSONObject jsonOutputPayload ){
            if (jsonOutputPayload.has("info")) {
                JSONObject json2 = jsonOutputPayload.getJSONObject("info");

                if (json2.has("license")) {
                    return json2.getString("license");
                } else {
                    String licenseNotFound = "License not declared";
                    return licenseNotFound;
                }
            }
            return null;
        }

        /* Find recursively the GitHub URL inside of the payload/input */
        private static String findGitHubStringIterate(JSONObject jsonObj) {
            for (String keyStr : jsonObj.keySet()) {
                Object keyvalue = jsonObj.get(keyStr);
                str1 = String.valueOf(keyvalue);
                if (jsonObj.get(keyStr) instanceof JSONObject) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject = (JSONObject) jsonObj.get(keyStr);
                    findGitHubStringIterate(jsonObject);
                    //break;
                }
                if (str1.contains("github.com")) {
                    System.out.println("The Keyword :github.com is found in given string");
                    System.out.println("key: " + keyStr + " value: " + keyvalue);
                    return str1;
                }
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

        /**
         * Retrieves the cloned repository path on the shared volume from the input record.
         *
         * @param json the input record containing repository information.
         * @return the repository path on the shared volume
         * @throws IllegalArgumentException in case the function couldn't find the repository path in the input record.
         */
        static String findSourcePath(JSONObject json) {
            for (var key : json.keySet()) {
                if (key.equals("sourcePath")) {
                    String candidatePayload = json.getString("sourcePath");
                    //var candidatePayload = json.getJSONObject(key);
                    //if (candidatePayload.has("forge")) {
                    System.out.println("sourcePath");
                    System.out.println(candidatePayload);
                    System.out.println(json.getString("sourcePath"));
                    return candidatePayload;
                } else {
                    var other = json.get(key);
                    if (other instanceof JSONObject) {
                        var otherPayload = findSourcePath((JSONObject) other);
                        if(otherPayload != null) return otherPayload;
                    }
                }
            }
            return null;
        }


        /**
         * Retrieves the repository URL from the input record.
         *
         * @param record the input record containing repository information.
         * @return the input repository URL.
         */
        @Nullable
        protected String extractRepoURL(String record) {
            var payload = new JSONObject(record);
            if (payload.has("fasten.RepoCloner.out")) {
                payload = payload.getJSONObject("fasten.RepoCloner.out");
            }
            if (payload.has("payload")) {
                payload = payload.getJSONObject("payload");
            }
            return payload.getString("repoUrl");
        }


        /**
         * Scans a repository looking for license text in files with scancode.
         *
         * @param repoPath the repository path whose pom.xml file must be retrieved.
         * @return the path of the file containing the result.
         * @throws IOException          in case scancode couldn't start.
         * @throws InterruptedException in case this function couldn't wait for scancode to complete.
         * @throws RuntimeException     in case scancode returns with an error code != 0.
         */
        protected String scanProject(String repoPath) throws IOException, InterruptedException, RuntimeException {

            // Where is the result stored
            String resultPath = repoPath + "/scancode.json";

            // `scancode` command to be executed
            List<String> cmd = Arrays.asList(
                    "/bin/bash",
                    "-c",
                    "scancode " +
                            // Scan for licenses
                            "--license " +
                            // Report full, absolute paths
                            "--full-root " +
                            // Scan using n parallel processes
                            "--processes " + "2 " +
                            // Write scan output as a compact JSON file
                            "--json " + resultPath + " " +
                            // SPDX RDF file
                            // "--spdx-rdf " + repoPath + "/scancode.spdx.rdf" + " " +
                            // SPDX tag/value file
                            // "--spdx-tv " + repoPath + "/scancode.spdx.tv " + " " +
                    /*  Only return files or directories with findings for the requested scans.
                        Files and directories without findings are omitted
                        (file information is not treated as findings). */
                            "--only-findings " +
                            // TODO Scancode timeout?
                            // "--timeout " + "600.0 " +
                            // Repository directory
                            repoPath
            );

            // Start scanning
            logger.info("Scanning project in " + repoPath + "...");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO();
            Process p = null;
            int exitCode = Integer.MIN_VALUE;
            try {
                p = pb.start(); // start scanning the project
                exitCode = p.waitFor();// synchronous call
            } catch (IOException e) {
                if (p != null) {
                    p.destroy();
                }
                throw new IOException("Couldn't start the scancode analyzer: " + e.getMessage(), e.getCause());
            } catch (InterruptedException e) {
                if (p != null) {
                    p.destroy();
                }
                throw new InterruptedException("Couldn't wait for scancode to complete: " + e.getMessage());
            }
            if (exitCode != 0) {
                throw new RuntimeException("Scancode returned with exit code " + exitCode + ".");
            }

            logger.info("...project in " + repoPath + " scanned successfully.");

            return resultPath;
        }

        /**
         * Parses the scan result file and returns file licenses.
         *
         * @param scanResultPath the path of the file containing the scan results.
         * @return the list of licenses that have been detected by scanning files.
         * @throws IOException   in case the JSON scan result couldn't be read.
         * @throws JSONException in case the root object of the JSON scan result couldn't have been retrieved.
         */
        protected JSONArray parseScanResult(String scanResultPath) throws IOException, JSONException {

            try {
                // Retrieving the root element of the scan result file
                JSONObject root = new JSONObject(Files.readString(Paths.get(scanResultPath)));
                if (root.isEmpty()) {
                    throw new JSONException("Couldn't retrieve the root object of the JSON scan result file " +
                            "at " + scanResultPath + ".");
                }

                // Returning file licenses
                if (root.has("files") && !root.isNull("files")) {
                    return root.getJSONArray("files");
                }
            } catch (IOException e) {
                throw new IOException("Couldn't read the JSON scan result file at " + scanResultPath +
                        ": " + e.getMessage(), e.getCause());
            }

            // In case nothing could have been found
            return null;
        }

        @Override
        public Optional<String> produce() {
            if (detectedLicenses == null ||
                    (detectedLicenses.getOutbound().isEmpty() && detectedLicenses.getFiles().isEmpty())
            ) {
                return Optional.empty();
            } else {
                return Optional.of(new JSONObject(detectedLicenses).toString());
            }
        }

        @Override
        public String getOutputPath() {
            return null; // FIXME
        }

        @Override
        public String name() {
            return "License Detector Plugin";
        }

        @Override
        public String description() {
            return "Detects licenses at the file level";
        }

        @Override
        public String version() {
            return "0.1.0";
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Exception getPluginError() {
            return this.pluginError;
        }

        public void setPluginError(Exception throwable) {
            this.pluginError = throwable;
        }

        @Override
        public void freeResource() {
        }

        @Override
        public long getMaxConsumeTimeout() {
            return 30 * 60 * 1000; // 30 minutes
        }
    }
}