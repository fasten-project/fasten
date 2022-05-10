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

# Python license detector

The Python license detector is triggered by the `fasten.metadataDBPythonExtension.out` Kafka consumer topic.
Then, it detects the outbound license by querying the PyPI.org APIs. To cope with this aim, the license detector performs an HTTP GET at the address: https://pypi.org/pypi/packageName/packageVersion/json.

This query is executed using the method [getJSONFromPypi](https://github.com/fasten-project/fasten/blob/develop/analyzer/python-license-detector/src/main/java/eu/fasten/analyzer/pythonlicensedetector/PythonLicenseDetectorPlugin.java#L272-L290).

The license detector will recursively look up at the API response to potentially extract a GitHub URL, with the method [findGitHubStringIterate](https://github.com/fasten-project/fasten/blob/develop/analyzer/python-license-detector/src/main/java/eu/fasten/analyzer/pythonlicensedetector/PythonLicenseDetectorPlugin.java#L308-L323). If PyPI.org APIs are not providing license information (using the method [getLicenseFromPypi](https://github.com/fasten-project/fasten/blob/develop/analyzer/python-license-detector/src/main/java/eu/fasten/analyzer/pythonlicensedetector/PythonLicenseDetectorPlugin.java#L293-L305)), the GitHub URL will be then parsed with the method [getLicenseFromGitHub](https://github.com/fasten-project/fasten/blob/develop/analyzer/python-license-detector/src/main/java/eu/fasten/analyzer/pythonlicensedetector/PythonLicenseDetectorPlugin.java#L333-L401), which will perform a query against the GitHub APIs, looking for license information.


`PackageName` and `packageVersion` are retrieved using the methods [extractPackageName](https://github.com/fasten-project/fasten/blob/develop/analyzer/python-license-detector/src/main/java/eu/fasten/analyzer/pythonlicensedetector/PythonLicenseDetectorPlugin.java#L174-L188) and [extractPackageVersion](https://github.com/fasten-project/fasten/blob/develop/analyzer/python-license-detector/src/main/java/eu/fasten/analyzer/pythonlicensedetector/PythonLicenseDetectorPlugin.java#L150-L164), looking at the input Kafka message. The first method looks iteratively for the `product` value, while the second looks for `version` within two nidified `input` values. These two methods could have also been implemented using recursion, which would enable the detector to find these values if the number or the order of the previous plugins changes in the future.

Then, the plugin starts the detection of other license information by running Scancode, performing similarly to the [Java license detector](https://github.com/fasten-project/fasten/tree/develop/analyzer/license-detector). The main difference is where the package `repos` are located. The detector has to look recursively for the `sourcePath` address in the consumed Kafka message, using the method [findSourcePath](https://github.com/fasten-project/fasten/blob/develop/analyzer/python-license-detector/src/main/java/eu/fasten/analyzer/pythonlicensedetector/PythonLicenseDetectorPlugin.java#L410-L424). Then Scancode will run upon the indicated path, executed by the method [scanProject](https://github.com/fasten-project/fasten/blob/develop/analyzer/python-license-detector/src/main/java/eu/fasten/analyzer/pythonlicensedetector/PythonLicenseDetectorPlugin.java#L455-L514), producing a JSON that will be then parsed by the [parseScanResult](https://github.com/fasten-project/fasten/blob/develop/analyzer/python-license-detector/src/main/java/eu/fasten/analyzer/pythonlicensedetector/PythonLicenseDetectorPlugin.java#L524-L553) method, which will populate a JSON object with the license findings at the file level.  Finally the [produce](https://github.com/fasten-project/fasten/blob/develop/analyzer/python-license-detector/src/main/java/eu/fasten/analyzer/pythonlicensedetector/PythonLicenseDetectorPlugin.java#L556-L564) method, looking at the `detectedLicenses` object, generates the Kafka message that will be then consumed by the feeder.



## Join the community

The FASTEN software package management efficiency relies on an open community contributing to open technologies. Related research projects, R&D engineers, early users and open source contributors are welcome to join the [FASTEN community](https://www.fasten-project.eu/view/Main/Community), to try the tools, to participate in physical and remote worshops and to share our efforts using the project [community page](https://www.fasten-project.eu/view/Main/Community) and the social media buttons below.
<p>
    <a href="http://www.twitter.com/FastenProject" alt="Fasten Twitter">
        <img src="https://img.shields.io/badge/%20-Twitter-%231DA1F2?logo=Twitter&style=for-the-badge&logoColor=white" /></a>
    <a href="http://www.slideshare.net/FastenProject" alt="GitHub Workflow Status">
                <img src="https://img.shields.io/badge/%20-SlideShare-%230077B5?logo=slideshare&style=for-the-badge&logoColor=white" /></a>
    <a href="http://www.linkedin.com/groups?gid=12172959" alt="Gitter">
            <img src="https://img.shields.io/badge/%20-LinkedIn-%232867B2?logo=linkedin&style=for-the-badge&logoColor=white" /></a>
</p>
