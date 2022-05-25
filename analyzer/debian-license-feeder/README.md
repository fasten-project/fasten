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

# Debian license feeder


The license feeder consumes messages with the topic `fasten.DebianLicenseDetector.out` containing license information at the file level and the outbound license of a given package.

This feeder functions slightly different than the others (Java and Python).
The plugin uses the `product` name and `version` to augment the metadata field, while the Debian license detector uses `source` as a `packageName` and `version` as a `packageVersion` to perform the queries against the Debian APIs.

There are cases where more products are included in a single source (e.g. libdga, libcfitsio). Since the Debian APIs are not aware of the `product`s (that come after compiling a source), but only of the source, the license detector retrieves license information performing queries only using `source` and `version`.

![PipelineIntegrations_Debian drawio](https://user-images.githubusercontent.com/10910590/167827873-8f9f7ae2-9f1b-443b-ac2b-8c2defefb9d2.png)
