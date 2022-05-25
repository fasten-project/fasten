
<!--
<p align="center">
    <img src="https://user-images.githubusercontent.com/45048351/91091340-1c578200-e65f-11ea-9c5d-597fbbe4ba41.jpg">
</p>
<br/>
-->
<p align="center">
    <a href="https://github.com/fasten-project/fasten/actions" alt="GitHub Workflow Status">
        <img src="https://img.shields.io/github/workflow/status/fasten-project/fasten/Java%20CI?logo=GitHub%20Actions&logoColor=white&style=for-the-badge" /></a>
    <!-- Here should be a link to Maven repo and version should be pulled from there. -->
    <a href="https://github.com/fasten-project/fasten/" alt="GitHub Workflow Status">
                <img src="https://img.shields.io/maven-central/v/fasten/graph?label=version&logo=Apache%20Maven&style=for-the-badge" /></a>
</p>
<br/>

# Debian license detector

The Debian license detector plugin  starts after consuming the Kafka topic `fasten.metadataDBCExtension.out` (as shown in the figure below). Then, it detects the outbound license by querying the Debian APIs.

The method [retrieveCopyrightFile](https://github.com/fasten-project/fasten/blob/develop/analyzer/debian-license-detector/src/main/java/eu/fasten/analyzer/debianlicensedetector/DebianLicenseDetectorPlugin.java#L332-L396) takes in input the `packageName` and the `packageVersion`, parses the JSON obtained by the Debian APIs, and looks in the package root directory for files name containing the three keywords: `copyright`, `license`, and `readme`. Then, it uses the method [ElaborateCopyrightFileJSON](https://github.com/fasten-project/fasten/blob/develop/analyzer/debian-license-detector/src/main/java/eu/fasten/analyzer/debianlicensedetector/DebianLicenseDetectorPlugin.java#L397-L433) which creates a JSONObject containing license information and the path of the matched file.
The `retrieveCopyrightFile` method is called inside of the [DebianOutboundLicenses](https://github.com/fasten-project/fasten/blob/develop/analyzer/debian-license-detector/src/main/java/eu/fasten/analyzer/debianlicensedetector/DebianLicenseDetectorPlugin.java#L241-L254) method, which will add the JSONObject provided by the `ElaborateCopyrightFileJSON` method, in case one of the three keywords has been found.

The license detector retrieves licenses at the file level, looking recursively inside the package directories. This is performed by parsing the JSON received from the APIs and storing the license information for files together with their path.
This process mainly involves the methods: [AnalyzeDirectory](https://github.com/fasten-project/fasten/blob/develop/analyzer/debian-license-detector/src/main/java/eu/fasten/analyzer/debianlicensedetector/DebianLicenseDetectorPlugin.java#L565-L608), [LoopThroughFiles](https://github.com/fasten-project/fasten/blob/develop/analyzer/debian-license-detector/src/main/java/eu/fasten/analyzer/debianlicensedetector/DebianLicenseDetectorPlugin.java#L659-L694), and the [GetDirectoryOrFileJSON](https://github.com/fasten-project/fasten/blob/develop/analyzer/debian-license-detector/src/main/java/eu/fasten/analyzer/debianlicensedetector/DebianLicenseDetectorPlugin.java#L545-L562)
`AnalyzeDirectory` is a recursive method that terminates only when the deepest directory in a package is reached.
`LoopThroughFiles`, employing other three methods ([RetrieveChecksumWithPath](https://github.com/fasten-project/fasten/blob/develop/analyzer/debian-license-detector/src/main/java/eu/fasten/analyzer/debianlicensedetector/DebianLicenseDetectorPlugin.java#L455-L471), [RetrieveLicenseAndPathJSON](https://github.com/fasten-project/fasten/blob/develop/analyzer/debian-license-detector/src/main/java/eu/fasten/analyzer/debianlicensedetector/DebianLicenseDetectorPlugin.java#L474-L490),[RetrieveLicenseAndPath](https://github.com/fasten-project/fasten/blob/develop/analyzer/debian-license-detector/src/main/java/eu/fasten/analyzer/debianlicensedetector/DebianLicenseDetectorPlugin.java#L493-L530)) discovers license information for each file in the current directory and, using the [WriteToJSON](https://github.com/fasten-project/fasten/blob/develop/analyzer/debian-license-detector/src/main/java/eu/fasten/analyzer/debianlicensedetector/DebianLicenseDetectorPlugin.java#L533-L542) method, writes file `path` and `license` to a JSON file.

At the end of the `AnalyzeDirectory` recursion, the detector adds the license discovered to the [detectedLicenses](https://github.com/fasten-project/fasten/blob/develop/analyzer/debian-license-detector/src/main/java/eu/fasten/analyzer/debianlicensedetector/DebianLicenseDetectorPlugin.java#L174) object by running the [parseScanResult](https://github.com/fasten-project/fasten/blob/develop/analyzer/debian-license-detector/src/main/java/eu/fasten/analyzer/debianlicensedetector/DebianLicenseDetectorPlugin.java#L705-L722) method.

The [produce](https://github.com/fasten-project/fasten/blob/develop/analyzer/debian-license-detector/src/main/java/eu/fasten/analyzer/debianlicensedetector/DebianLicenseDetectorPlugin.java#L220-L230) method will then include all the license findings to the Kafka message converting to string the `detectedLicenses` object.


![PipelineIntegrations_Debian drawio](https://user-images.githubusercontent.com/10910590/167583124-81122f40-9c96-4067-9c95-92569121e23c.png)

