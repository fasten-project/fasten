package eu.fasten.analyzer.debianlicensedetector;

import com.google.common.collect.Sets;
import eu.fasten.core.data.metadatadb.license.DetectedLicense;
import eu.fasten.core.data.metadatadb.license.DetectedLicenseSource;
import eu.fasten.core.data.metadatadb.license.DetectedLicenses;
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


public class DebianLicenseDetectorPlugin extends Plugin {

    public DebianLicenseDetectorPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class DebianLicenseDetector implements KafkaPlugin {
        private static String packageVersion = "latest";
        private static String packageName ;
        private static int HttpGetCount = 0;
        private static int FilesCount=0;
        private static int FilesWithLicensesCount=0;
        private static int LoopAlarm = 0;
        private static int NumberOfFilesWithDoubleEntries = 0;
        private static File file;
        private static String FileDoubleEntered = null;
        private static String CurrentPathAndFilename = null;

        private final Logger logger = LoggerFactory.getLogger(DebianLicenseDetector.class.getName());

        protected Exception pluginError = null;

        /**
         * The topic this plugin consumes.
         */
        protected String consumerTopic = "fasten.MetadataDBCExtension.out";

        /**
         * TODO
         */
        protected DetectedLicenses detectedLicenses = new DetectedLicenses();

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(Collections.singletonList(consumerTopic));
        }

        @Override
        public void setTopic(String topicName) {
            this.consumerTopic = topicName;
        }

        /**
         * Resets the internal state of this plugin.
         */
        protected void reset() {
            pluginError = null;
            detectedLicenses = new DetectedLicenses();
        }

        @Override
        public void consume(String record) {
            try { // Fasten error-handling guidelines
                reset();
                JSONObject json = new JSONObject(record);
                logger.info("Debian license detector started.");

                // Retrieving the package name
                String packageName = extractPackageName(json);
                logger.info("The package to analyze is:"+packageName+".");
                // Retrieving the package version
                String packageVersion = extractPackageVersion(json);
                logger.info("The package version is:"+packageVersion+".");

                // these logs should be saved in some shared directory, and a policy of log deletion should be implemented
                String directoryName = "logs";
                File directory = new File(directoryName);
                if (!directory.exists()) {
                    directory.mkdir();
                }
                FileHandler fh;
                String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
                // This block configures the logger with handler and formatter
                fh = new FileHandler("logs/D4.2" + timeStamp + ".log");
                logger.addHandler(fh);
                SimpleFormatter formatter = new SimpleFormatter();
                fh.setFormatter(formatter);

                long startTime = System.currentTimeMillis();
                MeasureElapsedTime();
                String path = packageName + "/" + packageVersion;
                var jsonOutputPayload = new JSONObject();
                jsonOutputPayload = GetDirectoryOrFileJSON(path);
                System.out.println(jsonOutputPayload);
                if (jsonOutputPayload == null) {
                    logger.info("Analyzed: " + packageName + " version : " + packageVersion);
                    logger.info("The package is not present on the Debian repository.");
                } else {
                    packageVersion = jsonOutputPayload.getString("version");
                    file = new File("logs/" + packageName + "-" + packageVersion + "_LicensesAtFileLevel_ld.json");
                    Files.deleteIfExists(file.toPath());
                    file.createNewFile();
                    logger.info(file + " created.");
                    logger.info("Analyzing: " + packageName + " version : " + packageVersion);
                    AnalyzeDirectory(jsonOutputPayload, packageName, packageVersion);
                }

                long endTime = System.currentTimeMillis();
                long duration = (endTime - startTime);  //Total execution time in milliseconds


                logger.info("Analysis completed successfully\n " +
                        "During this analysis " + HttpGetCount + " HTTP requests have been performed.\n" +
                        "During this analysis " + FilesCount + " files have been found.\n" +
                        "During this analysis " + FilesWithLicensesCount + " files with licenses have been found.\n" +
                        "The analysis took:" + ConvertMsToMins(duration) + ".\n"
                );
                if (NumberOfFilesWithDoubleEntries > 0) {
                    logger.info("The number of files with detected double entries are: " + NumberOfFilesWithDoubleEntries + ".\n" +
                            "The five files detected with a double entry are :" + FileDoubleEntered + ".\n"
                    );
                }
                packageVersion = "latest";
                HttpGetCount = 0;
                FilesCount = 0;
                FilesWithLicensesCount = 0;
                NumberOfFilesWithDoubleEntries = 0;
                file = null;
                FileDoubleEntered = null;
            }
        }

        // this method first get the license from one of the copyrights files (copyright, license or readme), if
        // these files are not present try to retrieve the outbound license from github, using the repourl.
        /**
         * Retrieves the outbound license(s) of the input project.
         *
         * @param packageName the name of the package to be scanned.
         * @param packageVersion the version of the package to be scanned.
         * @return the set of detected outbound licenses.
         */
        protected Set<DetectedLicense> getDebianOutboundLicenses(String packageName, String packageVersion, String repoUrl) {

            try {
                // Retrieving the outbound license(s) from one of the copyright files (copyright, license or readme)
                // TODO passing also the path as SOURCE
                JSONObject FileAndPath = retrieveCopyrightFile(packageName,packageVersion)
                if (FileAndPath.getString("license")!= null){
                    String license = FileAndPath.getString("license");
                    return license;
                }


            } catch (FileNotFoundException | RuntimeException | XmlPullParserException e) {

                // In case retrieving the outbound license from the copights  files was not possible
                logger.warn(e.getMessage(), e.getCause()); // why wasn't it possible
                logger.info("Retrieving outbound license from GitHub...");
                if ((detectedLicenses.getOutbound() == null || detectedLicenses.getOutbound().isEmpty())
                        && repoUrl != null) {

                    // Retrieving licenses from the GitHub API
                    try {
                        DetectedLicense licenseFromGitHub = getDebianLicenseFromGitHub(repoUrl);
                        if (licenseFromGitHub != null) {
                            return Sets.newHashSet(licenseFromGitHub);
                        } else {
                            logger.warn("Couldn't retrieve the outbound license from GitHub.");
                        }
                    } catch (IllegalArgumentException | IOException ex) { // not a valid GitHub repo URL
                        logger.warn(e.getMessage(), e.getCause());
                    } catch (@SuppressWarnings({"TryWithIdenticalCatches", "RedundantSuppression"})
                            RuntimeException ex) {
                        logger.warn(e.getMessage(), e.getCause()); // could not contact GitHub API
                    }
                }
            }

            return Collections.emptySet();
        }

        // TODO
        /**
         * Retrieves the licenses declared in the files: `copyright`, `license`, `readme` .
         *
         * @param packageName the package name to be analyzed.
         * @param packageVersion the package version to be analyzed.
         * @return the detected licenses.
         * @throws XmlPullParserException in case the `pom.xml` file couldn't be parsed as an XML file.
         */
        protected Set<DetectedLicense> getLicensesFromCopyrightFile(String packageName, String packageVersion) throws XmlPullParserException {

            // Result
            List<License> licenses;

            // Maven `pom.xml` file parser
            MavenXpp3Reader reader = new MavenXpp3Reader();
            try (FileReader fileReader = new FileReader(pomFile)) {

                // Parsing and retrieving the `licenses` XML tag
                Model model = reader.read(fileReader);
                licenses = model.getLicenses();

                // If the pom file contains at least a license tag
                if (!licenses.isEmpty()) {

                    // Logging
                    logger.trace("Found " + licenses.size() + " outbound license" + (licenses.size() == 1 ? "" : "s") +
                            " in " + pomFile.getAbsolutePath() + ":");
                    for (int i = 0; i < licenses.size(); i++) {
                        logger.trace("License number " + i + ": " + licenses.get(i).getName());
                    }

                    // Returning the set of discovered licenses
                    Set<DetectedLicense> result = new HashSet<>(Collections.emptySet());
                    licenses.forEach(license -> result.add(new DetectedLicense(
                            license.getName(),
                            DetectedLicenseSource.LOCAL_POM)));

                    return result;
                }
            } catch (IOException e) {
                throw new RuntimeException("Pom file " + pomFile.getAbsolutePath() +
                        " exists but couldn't instantiate a FileReader object..", e.getCause());
            } catch (XmlPullParserException e) {
                throw new XmlPullParserException("Pom file " + pomFile.getAbsolutePath() +
                        " exists but couldn't be parsed as a Maven pom XML file: " + e.getMessage());
            }

            // No licenses were detected
            return Collections.emptySet();
        }

        /**
         * Retrieves the outbound license of a GitHub project using its API.
         *
         * @param repoUrl the repository URL whose license is of interest.
         * @return the outbound license retrieved from GitHub's API.
         * @throws IllegalArgumentException in case the repository is not hosted on GitHub.
         * @throws IOException              in case there was a problem contacting the GitHub API.
         */
        protected DetectedDebianLicense getDebianLicenseFromGitHub(String repoUrl)
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
            DebianDetectedLicense repoLicense;

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
         * ############################ TODO ########################
         *                  Here the input topic changed.
         *                  Integrate Magiel suggested code.
         * Retrieves the package version of the input record.
         *
         * @param record the input record containing the package version information.
         * @return the package version
         * @throws IllegalArgumentException in case the function couldn't find the package version.
         */
        protected String extractPackageVersion(JSONObject json) throws IllegalArgumentException {
            for (var key : json.keySet()) {
                if (key.equals("version")) {
                    return json.getString("version");
                } else {
                    var other = json.get(key);
                    if (other instanceof JSONObject) {
                        var nestedResult = findPayload((JSONObject) other);
                        if(nestedResult != null) return nestedResult;
                    }
                }
            }
            return null;
        }

        /**
         * #################### TODO #####################
         *          ....same as above...
         *
         * Retrieves the package name of the input record.
         *
         * @param record the input record containing package information.
         * @return the package name.
         */
        protected String extractPackageName(JSONObject json) {
            for (var key : json.keySet()) {
                if (key.equals("product")) {
                    return json.getString("product");
                } else {
                    var other = json.get(key);
                    if (other instanceof JSONObject) {
                        var nestedResult = findPayload((JSONObject) other);
                        if(nestedResult != null) return nestedResult;
                    }
                }
            }
            return null;
        }

        /**
         * * #################### TODO #####################
         *          ....same as above...
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

        // this method was calling the previous version of RetrieveLicenseAndPath, TODO check if with RetrieveLicenseAndPathJSON all it is functioning
        /**
         * Retrieves the copyright file given a package name and the package version path.
         *
         * @param packageName the package name to be analyzed.
         * @param packageVersion the package version to be analyzed.
         */
        protected JSONObject retrieveCopyrightFile(String packageName, String packageVersion) {
            URL url = new URL("https://sources.debian.org/api/src/" + packageName + "/" + packageVersion + "/");
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
            if (jsonOutputPayload.has("content")) {
                JSONArray array2 = jsonOutputPayload.getJSONArray("content");
                //Getting json objects inside array
                for (int i = 0; i < array2.length(); i++) {
                    JSONObject obj4 = array2.getJSONObject(i);
                    //Getting name and type of json objects inside array2
                    String name = obj4.getString("name");
                    String type = obj4.getString("type");
                    String copyright = "copyright";
                    String licenseStr = "license";
                    String readme = "readme";
                    String license = null;
                    System.out.println("The file name is : " + obj4.getString("name") + " Type of obj4 at index " + i + " is : " + obj4.getString("type"));
                    //Converting both the strings to lower case for case insensitive checking
                    if (name.toLowerCase().contains(copyright)) {
                        String checksum = RetrieveChecksum(name, packageName, packageVersion);
                        if (checksum != null) {
                            // the following String should be modified in a JSONObject, and then parsing the license key
                            JSONObject LicenseAndPath = RetrieveLicenseAndPathJSON(checksum, packageName, packageVersion);
                            if (LicenseAndPath.getString("license")!= null){
                                license = LicenseAndPath.getString("license");
                            }
                            if (license != null) {
                                System.out.println("The license retrieved is: "+license);
                                return LicenseAndPath;
                            }
                        }
                    }
                    if (name.toLowerCase().contains(licenseStr)) {
                        String checksum = RetrieveChecksum(name, packageName, packageVersion);
                        if (checksum != null) {
                            JSONObject LicenseAndPath = RetrieveLicenseAndPathJSON(checksum, packageName, packageVersion);
                            if (LicenseAndPath.getString("license")!= null){
                                license = LicenseAndPath.getString("license");
                            }
                            if (license != null) {
                                System.out.println("The license retrieved is: "+license);
                                return LicenseAndPath;
                            }
                        }
                    }
                    if (name.toLowerCase().contains(readme)) {
                        String checksum = RetrieveChecksum(name, packageName, packageVersion);
                        if (checksum != null) {
                            JSONObject LicenseAndPath = RetrieveLicenseAndPathJSON(checksum, packageName, packageVersion);
                            if (LicenseAndPath.getString("license") != null) {
                                license = LicenseAndPath.getString("license");
                            }
                            if (license != null) {
                                System.out.println("The license retrieved is: " + license);
                                return LicenseAndPath;
                            }
                        }
                    }
                }
            } else {
                System.out.println(" No contents key in this JSON");
                return licenseAndFilePath;
            }
        }

        // retrieve checksum for a given file
        protected String RetrieveChecksum(String fileName, String packageName, String packageVersion) throws IOException {
            URL url = new URL("https://sources.debian.org/api/src/" + packageName + "/" + packageVersion + "/" + "/" + fileName + "/");
            String checksum = null;
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("HTTP query failed. Error code: " + conn.getResponseCode());
            }
            InputStreamReader in = new InputStreamReader(conn.getInputStream());
            BufferedReader br = new BufferedReader(in);
            String jsonOutput = br.lines().collect(Collectors.joining());

            var jsonOutputPayload = new JSONObject(jsonOutput);
            if (jsonOutputPayload.has("checksum")) {
                checksum = jsonOutputPayload.getString("checksum");
            }
            return checksum;
        }

        // This is going to substitute the previous function called: RetrieveChecksum
        protected String RetrieveChecksumWithPath(String path) throws IOException, TimeoutException, InterruptedException {
            String checksum = null;
            var jsonOutputPayload = new JSONObject();
            try {
                jsonOutputPayload = GetDirectoryOrFileJSON(path);
                if (jsonOutputPayload != null){
                    if (jsonOutputPayload.has("checksum")) {
                        checksum = jsonOutputPayload.getString("checksum");
                    }
                }
            } catch (IOException e) {
                throw new IOException(
                        "Couldn't get data from the HTTP response returned by Debian's API using the checksum: " + e.getMessage(),
                        e.getCause());
            }
            return checksum;
        }

        // this method retrieves a JSON given a checksum and a packageName. This is used by the RetrieveLicenseAndPath method.
        protected JSONObject RetrieveLicenseAndPathJSON(String checksum, String packageName, String packageVersion) throws IOException, TimeoutException, RuntimeException {
            URL url = new URL("https://sources.debian.org/copyright/api/sha256/?checksum=" + checksum + "&package=" + packageName);
            System.out.println(url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);  //set timeout to 5 seconds
            if (conn.getResponseCode() != 200) {
                return null;
            }
            HttpGetCount += 1;
            InputStreamReader in = new InputStreamReader(conn.getInputStream());
            BufferedReader br = new BufferedReader(in);
            String jsonOutput = br.lines().collect(Collectors.joining());
            var jsonOutputPayload = new JSONObject(jsonOutput);
            return jsonOutputPayload;
        }

        // return a JSONObject with license and path. This method is invoked right after RetrieveLicenseAndPathJSON().
        protected JSONObject RetrieveLicenseAndPath(String checksum, String packageName, String packageVersion) throws IOException {
            URL url = new URL("https://sources.debian.org/copyright/api/sha256/?checksum=" + checksum + "&package=" + packageName);
            String license = null;
            String filePath = null;
            //initialize an empty JSONObject
            JSONObject licenseAndFilePath = new JSONObject();

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("HTTP query failed. Error code: " + conn.getResponseCode());
            }
            InputStreamReader in = new InputStreamReader(conn.getInputStream());
            BufferedReader br = new BufferedReader(in);
            String jsonOutput = br.lines().collect(Collectors.joining());
            var jsonOutputPayload = new JSONObject(jsonOutput);
            if (jsonOutputPayload.has("result")) {
                JSONObject obj1= jsonOutputPayload.getJSONObject("result");
                if (obj1.has("copyright")) {
                    JSONArray array1 = obj1.getJSONArray("copyright");
                    for (int i = 0; i < array1.length(); i++) {
                        JSONObject obj2 = array1.getJSONObject(i);
                        String version = obj2.getString("version");
                        if (version.equals(packageVersion)){
                            license = obj2.getString("license");
                            filePath = obj2.getString("path");
                            licenseAndFilePath.put("license", license);
                            licenseAndFilePath.put("path", filePath);
                        }
                    }
                }
            }
            return licenseAndFilePath;
        }

        // this method write to a JSONFile .
        protected void WriteToJSON(JSONArray JSONArrayWithFileInfo) throws IOException {
            try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(String.valueOf(file)), StandardOpenOption.APPEND)) {
                bw.append(String.valueOf(JSONArrayWithFileInfo));
                bw.append("\n");
                bw.flush();
            } catch (Exception ex) {
                System.err.println("Couldn't write Licenses \n"
                        + ex.getMessage());
            }
        }

        // this method retrieves the json file given a path
        protected JSONObject GetDirectoryOrFileJSON(String path) throws InterruptedException, RuntimeException, IOException, TimeoutException, SocketTimeoutException {
            JSONObject JSONDirectoryOrFile = new JSONObject();
            URL url = new URL("https://sources.debian.org/api/src/" + path + "/");
            System.out.println(url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);  //set timeout to 5 seconds
            if (conn.getResponseCode() == 200) {
                HttpGetCount += 1;
                InputStreamReader in = new InputStreamReader(conn.getInputStream());
                BufferedReader br = new BufferedReader(in);
                String jsonOutput = br.lines().collect(Collectors.joining());
                // searching for the copyright files in the JSON response
                JSONDirectoryOrFile = new JSONObject(jsonOutput);
                return JSONDirectoryOrFile;
            }
            return null;
        }

        // this method recursively call itself, to parse the json given
        protected void AnalyzeDirectory(JSONObject JSONDirectoryPayload, String packageName, String packageVersion) throws IOException, InterruptedException, TimeoutException {
            if (NumberOfFilesWithDoubleEntries < 25) {
                JSONArray JSONFilesLicenses = new JSONArray();
                if (JSONDirectoryPayload.has("content")) {
                    JSONArray array = JSONDirectoryPayload.getJSONArray("content");
                    JSONArray JSONFiles = new JSONArray();
                    // this loop analyzes files in the current directory.
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.getJSONObject(i);
                        String name = obj.getString("name");
                        String type = obj.getString("type");
                        String FilePath = JSONDirectoryPayload.getString("path");
                        if (type.equals("file")) {
                            FilePath = FilePath + "/" + name;
                            obj.put("path", FilePath);
                            obj.put("packageName", packageName);
                            obj.put("packageVersion", packageVersion);
                            JSONFiles.put(obj);
                        }
                    }

                    if (JSONFiles.length() > 0) {
                        System.out.println("JSONFiles :"+JSONFiles);
                        LoopThroughFiles(JSONFiles);
                    }
                    // this loop analyzes directories in the current directory; when a dir is found recursively the
                    // AnalyzeDirectory function is triggered.
                    for (int j = 0; j < array.length(); j++) {
                        JSONObject obj2 = array.getJSONObject(j);
                        String nameDir = obj2.getString("name");
                        String typeDir = obj2.getString("type");
                        String DirectoryPath = JSONDirectoryPayload.getString("path");
                        if (typeDir.equals("directory")) {
                            DirectoryPath = DirectoryPath + "/" + nameDir;
                            System.out.println("DirectoryPath is: "+DirectoryPath);
                            JSONObject JSONDirectory = GetDirectoryOrFileJSON(DirectoryPath);
                            if (JSONDirectory != null){
                                AnalyzeDirectory(JSONDirectory, packageName, packageVersion);
                            }
                        }
                    }
                }
            }
            else{
                System.out.println("Too many files with a double entry");
            }
        }

        // this method is used to measure the time elapsed for scanning a given package
        protected void MeasureElapsedTime() {
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // this method convert from milliseconds to minutes and seconds.
        protected String ConvertMsToMins(long milliseconds) {

            // formula for conversion for
            // milliseconds to minutes.
            long minutes = (milliseconds / 1000) / 60;

            // formula for conversion for
            // milliseconds to seconds
            long seconds = (milliseconds / 1000) % 60;

            // Print the output
            String output = minutes + " minutes and "
                    + seconds + " seconds.";
            return output;
        }

        // this method looks for dubplicate files, trying to prevent loops.
        protected boolean SearchPathInJsonFile(String jsonFile, String path){
            try{
                BufferedReader br = new BufferedReader(new FileReader(jsonFile));
                String next = null;
                LoopAlarm = 0;
                while ((next = br.readLine()) != null) {
                    JSONArray jsonArr = new JSONArray(next);
                    for (int i = 0; i < jsonArr.length(); i++) {
                        //jsonArr[i].getString("path");
                        //System.out.println(jsonArr.getString(i));
                        //String arr= jsonArr(i);
                        JSONObject explrObject = jsonArr.getJSONObject(i);
                        String path2 = explrObject.getString("path");
                        //double-check this loop
                        if (path2.equals(path)) {
                            LoopAlarm++;
                        }
                    }
                }
                if (LoopAlarm >= 2){
                    System.out.println("LoopAlarm = "+LoopAlarm);
                    System.out.println(path);
                    return false;
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }

        // Loop through files and insert path and license into a JSONArray
        protected JSONArray LoopThroughFiles(JSONArray JSONFiles) throws IOException, TimeoutException, InterruptedException {
            JSONArray LicenseAndPathFiles = new JSONArray();
            //JSONObject LicenseAndPathJSON = null;// = new JSONObject();
            String checksum = null;
            System.out.println(JSONFiles);
            for (int j = 0; j < JSONFiles.length(); j++) {
                JSONObject obj = JSONFiles.getJSONObject(j);
                if (obj.has("path")) {
                    CurrentPathAndFilename = obj.getString("path");
                    CurrentPathAndFilename = CurrentPathAndFilename.replace(packageName+"/","");
                    CurrentPathAndFilename = CurrentPathAndFilename.replace(packageVersion+"/","");
                    System.out.println(obj.getString("path"));
                    checksum = RetrieveChecksumWithPath(obj.getString("path"));
                }
                if (checksum != null) {
                    JSONObject LicenseAndPathJSON = RetrieveLicenseAndPathJSON(checksum, obj.getString("packageName"), obj.getString("packageVersion"));
                    if (LicenseAndPathJSON != null) {
                        JSONObject LicenseAndPath = RetrieveLicenseAndPath(LicenseAndPathJSON);
                        if (LicenseAndPath.length()>0) {
                            LicenseAndPathFiles.put(LicenseAndPath);
                        }
                    }
                }
            }
            if (LicenseAndPathFiles.length() > 0) {
                WriteToJSON(LicenseAndPathFiles);
                for (int i = 0; i < LicenseAndPathFiles.length(); i++) {
                    JSONObject explrObject = LicenseAndPathFiles.getJSONObject(i);
                    String path = explrObject.getString("path");
                    String jsonFile = file.getName();
                    System.out.println("jsonFile :"+jsonFile);
                    boolean DoubleEntries = SearchPathInJsonFile("logs/"+jsonFile, path);
                    if (!DoubleEntries){
                        NumberOfFilesWithDoubleEntries++;
                        FileDoubleEntered = path;
                        //pathIndex++;
                    }
                }
            }
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
            return "Debian License Detector Plugin";
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
