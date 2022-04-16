package eu.fasten.analyzer.debianlicensedetector;

import com.google.common.collect.Sets;
import eu.fasten.core.data.metadatadb.license.DetectedLicense;
import eu.fasten.core.data.metadatadb.license.DetectedLicenseSource;
import eu.fasten.core.data.metadatadb.license.DetectedLicenses;
import eu.fasten.core.plugins.KafkaPlugin;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import org.json.JSONException;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.FileHandler;
import java.util.stream.Collectors;


public class DebianLicenseDetectorPlugin extends Plugin {

    public DebianLicenseDetectorPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class DebianLicenseDetectorExtension implements KafkaPlugin {
        private static String packageVersion = "latest";
        private static String packageName ;
        private static int HttpGetCount = 0;
        private static int FilesCount=0;
        private static int FilesWithLicensesCount=0;
        private static int LoopAlarm = 0;
        private static int NumberOfFilesWithDoubleEntries = 0;
        private static File file;
        private static File fileJson;
        private static String FileDoubleEntered = null;
        private static String CurrentPathAndFilename = null;
        private static JSONObject object = new JSONObject();

        private final Logger logger = LoggerFactory.getLogger(DebianLicenseDetectorExtension.class.getName());

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
        public void setTopics(List<String> consumeTopics) {

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
                packageName = extractPackageName(json);
                logger.info("The package to analyze is:"+packageName+".");
                // Retrieving the package version
                packageVersion = extractPackageVersion(json);
                logger.info("The package version is:"+packageVersion+".");

                //Adding packageName and packageVersion to the out message (object).
                JSONObject packageInfo = new JSONObject();
                packageInfo.put("packageName", packageName);
                packageInfo.put("packageVersion", packageVersion);
                // forcing the packageName and packageVersion information into the files JSONArray
                object.accumulate("files", packageInfo);


                // Outbound license detection: adding outbound licenses to the JSON object.
                detectedLicenses.setOutbound(DebianOutboundLicenses(packageName,packageVersion));
                if (detectedLicenses.getOutbound() == null || detectedLicenses.getOutbound().isEmpty()) {
                    logger.warn("No outbound licenses were detected.");
                } else {
                    logger.info(
                            detectedLicenses.getOutbound().size() + " outbound license" +
                                    (detectedLicenses.getOutbound().size() == 1 ? "" : "s") + " detected: " +
                                    detectedLicenses.getOutbound()
                    );
                }

                String directoryName = "logs";
                File directory = new File(directoryName);
                if (!directory.exists()) {
                    directory.mkdir();
                }
                FileHandler fh;
                //String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());

                long startTime = System.currentTimeMillis();
                MeasureElapsedTime();
                String path = packageName + "/" + packageVersion;
                var jsonOutputPayload = new JSONObject();
                jsonOutputPayload = GetDirectoryOrFileJSON(path);
                //System.out.println(jsonOutputPayload);
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

                // Creating a json pretty printed in the jsons directory
                String directoryNameJson = "jsons";
                File directoryJson = new File(directoryNameJson);
                if (! directoryJson.exists()){
                    directoryJson.mkdir();
                }

                fileJson = new File("jsons/" + packageName + "-" + packageVersion + "_LicensesAtFileLevel_pp.json");
                BufferedWriter writer = new BufferedWriter(new FileWriter(fileJson));
                // here is missing the control upon jsons already created
                writer.write(object.toString(4));
                writer.close();


                JSONArray fileLicenses = parseScanResult(String.valueOf(fileJson));
                if (fileLicenses != null && !fileLicenses.isEmpty()) {
                    detectedLicenses.addFiles(fileLicenses);
                } else {
                    logger.warn("Scanner hasn't detected any licenses in " + String.valueOf(fileJson) + ".");
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

                fileJson.delete();
                file.delete();
                //System.out.println("Json files deleted.");

                packageVersion = "latest";
                HttpGetCount = 0;
                FilesCount = 0;
                FilesWithLicensesCount = 0;
                NumberOfFilesWithDoubleEntries = 0;
                file = null;
                FileDoubleEntered = null;
                } catch (SocketTimeoutException ex) {
                ex.printStackTrace();
            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            } catch (TimeoutException ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public Optional<String> produce() {
            if (detectedLicenses == null ||
                    (detectedLicenses.getOutbound().isEmpty() && detectedLicenses.getFiles().isEmpty())
            ) {
                logger.info("Detected licenses is empty.");
                return Optional.empty();
            } else {
                //System.out.println("Producing the payload with the produce method.");
                return Optional.of(new JSONObject(detectedLicenses).toString());
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
        protected Set<DetectedLicense> DebianOutboundLicenses(String packageName, String packageVersion) throws IOException, TimeoutException {
            // Retrieving the outbound license(s) from one of the copyright files (copyright, license or readme)
            JSONObject FileAndPath = retrieveCopyrightFile(packageName,packageVersion);
            //System.out.println("Inside DebianOutboundLicenses function.");
            //System.out.println(FileAndPath);
            if (FileAndPath.getString("license")!= null){
                DetectedLicense licenseFromDebianAPI;
                licenseFromDebianAPI = new DetectedLicense(FileAndPath.getString("license"), DetectedLicenseSource.DEBIAN_PACKAGES);
                return Sets.newHashSet(licenseFromDebianAPI);
                //return FileAndPath;
            }

            return Collections.emptySet();
        }

        /**
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
         * Retrieves the copyright file given a package name and the package version path.
         *
         * @param packageName the package name to be analyzed.
         * @param packageVersion the package version to be analyzed.
         */
        protected JSONObject retrieveCopyrightFile(String packageName, String packageVersion) throws IOException, TimeoutException {
            JSONObject result = new JSONObject();
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
                    //String type = obj4.getString("type");
                    String copyright = "copyright";
                    String licenseStr = "license";
                    String readme = "readme";
                    String license = null;
                    //System.out.println("The file name is : " + obj4.getString("name") + " Type of obj4 at index " + i + " is : " + obj4.getString("type"));
                    //Converting both the strings to lower case for case insensitive checking
                    if (name.toLowerCase().contains(copyright)) {
                        String checksum = RetrieveChecksum(name, packageName, packageVersion);
                        if (checksum != null) {
                            // the following String should be modified in a JSONObject, and then parsing the license key
                            LicenseAndPath = RetrieveLicenseAndPathJSON(checksum, packageName, packageVersion);
                            result = ElaborateCopyrightFileJSON(LicenseAndPath);
                            return result;
                        }
                    }
                    if (name.toLowerCase().contains(licenseStr)) {
                        String checksum = RetrieveChecksum(name, packageName, packageVersion);
                        if (checksum != null) {
                            LicenseAndPath = RetrieveLicenseAndPathJSON(checksum, packageName, packageVersion);
                            result = ElaborateCopyrightFileJSON(LicenseAndPath);
                            return result;
                        }
                    }
                    if (name.toLowerCase().contains(readme)) {
                        String checksum = RetrieveChecksum(name, packageName, packageVersion);
                        if (checksum != null) {
                            LicenseAndPath = RetrieveLicenseAndPathJSON(checksum, packageName, packageVersion);
                            result = ElaborateCopyrightFileJSON(LicenseAndPath);
                            return result;
                        }
                    }
                }
            } else {
                //System.out.println(" No contents key in this JSON");
                return LicenseAndPath;
            }
            JSONObject obj3 = new JSONObject();
            obj3.put("license", "");
            obj3.put("path", "");
            return obj3;
            //return LicenseAndPath;
        }
        protected JSONObject ElaborateCopyrightFileJSON(JSONObject LicenseAndPath) {
            //JSONObject LicenseAndPath = new JSONObject();
            String license = null;
            if (LicenseAndPath.has("result")){
                JSONObject obj1 = LicenseAndPath.getJSONObject("result");
                if (obj1.has("copyright")){
                    //System.out.println(obj1);
                    JSONArray array1 = obj1.getJSONArray("copyright");
                    for (int j = 0; j < array1.length(); j++) {
                        JSONObject obj2 = array1.getJSONObject(j);
                        //System.out.println(obj2);
                        String version = obj2.getString("version");
                        if (version.equals(packageVersion)) {
                            if (!obj2.isNull("license")) {
                                license = obj2.getString("license");
                                String path = obj2.getString("path");
                                //System.out.println("Inside retrieveCopyright function.");
                                //System.out.println(LicenseAndPath);
                                //System.out.println(license);
                                JSONObject obj3 = new JSONObject();
                                obj3.put("license", license);
                                obj3.put("path", path);
                                return obj3;
                            }
                            else{
                                String path = obj2.getString("path");
                                JSONObject obj3 = new JSONObject();
                                obj3.put("license", "");
                                obj3.put("path", path);
                                return obj3;
                            }
                        }
                    }
                }
            }
            return null;
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
        protected JSONObject RetrieveLicenseAndPath(JSONObject jsonOutputPayload) {
            String license = null;
            String filePath = null;
            //initialize an empty JSONObject
            JSONObject licenseAndFilePath = new JSONObject();
            if (jsonOutputPayload.has("result")) {
                JSONObject obj1 = jsonOutputPayload.getJSONObject("result");
                if (obj1.has("copyright")) {
                    JSONArray array1 = obj1.getJSONArray("copyright");
                    for (int i = 0; i < array1.length(); i++) {
                        JSONObject obj2 = array1.getJSONObject(i);
                        String version = obj2.getString("version");
                        String path = obj2.getString("path");
                        if (version.equals(packageVersion)) {
                            //System.out.println(CurrentPathAndFilename);
                            if (CurrentPathAndFilename != null){
                                if (CurrentPathAndFilename.equals(path)) {
                                    if (!obj2.isNull("license")) {
                                        license = obj2.getString("license");
                                        licenseAndFilePath.put("license", license);
                                        FilesWithLicensesCount += 1;
                                    }
                                    else{
                                        license = null;
                                        licenseAndFilePath.put("license", license);
                                    }
                                    filePath = obj2.getString("path");
                                    FilesCount+=1;
                                    licenseAndFilePath.put("path", filePath);
                                    //System.out.println(CurrentPathAndFilename+" and "+path+" matched!");
                                }
                            }
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
        protected static JSONObject GetDirectoryOrFileJSON(String path) throws InterruptedException, RuntimeException, IOException, TimeoutException, SocketTimeoutException {
            JSONObject JSONDirectoryOrFile = new JSONObject();
            URL url = new URL("https://sources.debian.org/api/src/" + path + "/");
            //System.out.println(url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);  //set timeout to 5 seconds
            if (conn.getResponseCode() == 200) {
                HttpGetCount += 1;
                InputStreamReader in = new InputStreamReader(conn.getInputStream());
                BufferedReader br = new BufferedReader(in);
                String jsonOutput = br.lines().collect(Collectors.joining());
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
                        //System.out.println("JSONFiles :"+JSONFiles);
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
                            //System.out.println("DirectoryPath is: "+DirectoryPath);
                            JSONObject JSONDirectory = GetDirectoryOrFileJSON(DirectoryPath);
                            if (JSONDirectory != null){
                                AnalyzeDirectory(JSONDirectory, packageName, packageVersion);
                            }
                        }
                    }
                }
            }
        }

        // this method is used to measure the time elapsed for scanning a given package
        protected static void MeasureElapsedTime() {
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // this method convert from milliseconds to minutes and seconds.
        protected static String ConvertMsToMins(long milliseconds) {

            long minutes = (milliseconds / 1000) / 60;

            long seconds = (milliseconds / 1000) % 60;

            String output = minutes + " minutes and "
                    + seconds + " seconds.";
            return output;
        }

        // this method looks for duplicate files, trying to prevent loops.
        protected boolean SearchPathInJsonFile(String jsonFile, String path){
            try{
                BufferedReader br = new BufferedReader(new FileReader(jsonFile));
                String next = null;
                LoopAlarm = 0;
                while ((next = br.readLine()) != null) {
                    JSONArray jsonArr = new JSONArray(next);
                    for (int i = 0; i < jsonArr.length(); i++) {
                        JSONObject explrObject = jsonArr.getJSONObject(i);
                        String path2 = explrObject.getString("path");
                        if (path2.equals(path)) {
                            LoopAlarm++;
                        }
                    }
                }
                if (LoopAlarm >= 2){
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
        protected void LoopThroughFiles(JSONArray JSONFiles) throws IOException, TimeoutException, InterruptedException {
            JSONArray LicenseAndPathFiles = new JSONArray();
            String checksum = null;
            for (int j = 0; j < JSONFiles.length(); j++) {
                JSONObject obj = JSONFiles.getJSONObject(j);
                if (obj.has("path")) {
                    CurrentPathAndFilename = obj.getString("path");
                    CurrentPathAndFilename = CurrentPathAndFilename.replace(packageName+"/","");
                    CurrentPathAndFilename = CurrentPathAndFilename.replace(packageVersion+"/","");
                    checksum = RetrieveChecksumWithPath(obj.getString("path"));
                }
                if (checksum != null) {
                    JSONObject LicenseAndPathJSON = RetrieveLicenseAndPathJSON(checksum, obj.getString("packageName"), obj.getString("packageVersion"));
                    if (LicenseAndPathJSON != null) {
                        JSONObject LicenseAndPath = RetrieveLicenseAndPath(LicenseAndPathJSON);
                        if (LicenseAndPath.length()>0) {
                            LicenseAndPathFiles.put(LicenseAndPath);
                            object.accumulate("files", LicenseAndPath);
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
                    boolean DoubleEntries = SearchPathInJsonFile("logs/"+jsonFile, path);
                    if (!DoubleEntries){
                        NumberOfFilesWithDoubleEntries++;
                        FileDoubleEntered = path;
                    }
                }
            }
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
                JSONObject root = new JSONObject(Files.readString(Paths.get(scanResultPath)));
                if (root.isEmpty()) {
                    throw new JSONException("Couldn't retrieve the root object of the JSON scan result file " +
                            "at " + scanResultPath + ".");
                }

                if (root.has("files") && !root.isNull("files")) {
                    return root.getJSONArray("files");
                }
            } catch (IOException e) {
                throw new IOException("Couldn't read the JSON scan result file at " + scanResultPath +
                        ": " + e.getMessage(), e.getCause());
            }
            return null;
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
