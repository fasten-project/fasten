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

# Part of the pipeline antecedent to this plugin

```
+------------+             +-----------+                +------------------+   +----------------+   +--------------+
|POM analyzer+------------>|Repo cloner+--------------->|Flink's JobManager+-->|License detector+-->|License feeder|
+-----------++             +-----------+                +------------------+   +----------------+   +--------------+
            |                                            ^
            |    +----+   +--------------------------+   |
            +--->|OPAL+-->|Metadata DB extension Java+---+
                 +----+   +--------------------------+
```

# Plugin description


The license feeder consumes messages with the topic `fasten.PythonLicenseDetector.out` containing license information at the file level and the outbound license of a given package.

This plugin has been adapted from the [Java license feeder](https://github.com/fasten-project/fasten/tree/develop/analyzer/license-feeder). The main difference is that instead of using `coordinates` (which for Maven are `groupId`, `articactId`, and package `version`), it uses `product` as a `packageName` and version as a `packageVersion`.

![Copy of Python pipeline Diagram (1)](https://user-images.githubusercontent.com/10910590/167827560-5357c5ce-34a3-45bb-b9f3-eb12081ea1a0.png)

