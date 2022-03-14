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

The Debian license feeder consumes messages with the topic `fasten.DebianLicenseDetectorExtension.out` containing license information at the file level and the outbound license of a given package.

<!-- to update from here -->

This plugin is currently functioning in the Java use case Fasten pipeline and will be slightly adapted for Python and Debian.

It sets the connection with the db through the function [setDBConnection()](https://github.com/fasten-project/fasten/blob/endocode/compliancePlugin/analyzer/license-feeder/src/main/java/eu/fasten/analyzer/licensefeeder/LicenseFeederPlugin.java#L43-L46) and consumes a record.

In the specific Java Maven use case, first, it extracts the Maven coordinates from the record. After instantiating a new MetadataDao object, it uses the [insertOutboundLicenses()](https://github.com/fasten-project/fasten/blob/endocode/compliancePlugin/analyzer/license-feeder/src/main/java/eu/fasten/analyzer/licensefeeder/LicenseFeederPlugin.java#L120-L132) and the [insertFileLicenses()](https://github.com/fasten-project/fasten/blob/endocode/compliancePlugin/analyzer/license-feeder/src/main/java/eu/fasten/analyzer/licensefeeder/LicenseFeederPlugin.java#L134-L156) functions to populate the DB. Both functions take as input the maven `coordinates`, the input `record`, and the `MetadataDao` object created in the [Consume()](https://github.com/fasten-project/fasten/blob/endocode/compliancePlugin/analyzer/license-feeder/src/main/java/eu/fasten/analyzer/licensefeeder/LicenseFeederPlugin.java#L49-L74) method.

The [insertOutboundLicenses()](https://github.com/fasten-project/fasten/blob/endocode/compliancePlugin/analyzer/license-feeder/src/main/java/eu/fasten/analyzer/licensefeeder/LicenseFeederPlugin.java#L120-L132) function creates a JSONObject from the input record and searches for the JSONArray `outbound`. Once retrieved the array, invokes the [insertPackageOutboundLicenses()](https://github.com/fasten-project/fasten/blob/endocode/compliancePlugin/analyzer/license-feeder/src/main/java/eu/fasten/analyzer/licensefeeder/LicenseFeederPlugin.java#L127-L130) method passing the coordinates and the licenses contained in the `outbound` array. The method [insertPackageOutboundLicenses()](https://github.com/fasten-project/fasten/blob/e9d1bb51c3f54f35a70dc4f7336b902b4b47b46d/core/src/main/java/eu/fasten/core/data/metadatadb/MetadataDao.java#L227-L260), which belongs to the MetadataDao class, takes as input the `coordinates`, and the `outboundLicense(s)`, and updates the Metadata field of the `package_versions` table through the [updateMetadata](https://github.com/fasten-project/fasten/blob/e9d1bb51c3f54f35a70dc4f7336b902b4b47b46d/core/src/main/java/eu/fasten/core/data/metadatadb/MetadataDao.java#L248-L254) object.

Concretely the insertPackageOutboundLicenses is invoked three times.
The [first time](https://github.com/fasten-project/fasten/blob/e9d1bb51c3f54f35a70dc4f7336b902b4b47b46d/core/src/main/java/eu/fasten/core/data/metadatadb/MetadataDao.java#L184-L198) it takes the input mentioned above and returns the `groupId`, the `artifactId`, and the package `version` and the `outboundLicenses` previously received, which will augment the metadata field.

The [second time](https://github.com/fasten-project/fasten/blob/e9d1bb51c3f54f35a70dc4f7336b902b4b47b46d/core/src/main/java/eu/fasten/core/data/metadatadb/MetadataDao.java#L200-L217) it takes as input the items previously returned and returns first the pretty-printed Maven coordinates by invoking the method [getMavenCoordinateName()](https://github.com/fasten-project/fasten/blob/e9d1bb51c3f54f35a70dc4f7336b902b4b47b46d/core/src/main/java/eu/fasten/core/data/metadatadb/MetadataDao.java#L214), which belongs to the class [MavenUtilities](https://github.com/fasten-project/fasten/blob/e9d1bb51c3f54f35a70dc4f7336b902b4b47b46d/core/src/main/java/eu/fasten/core/maven/utils/MavenUtilities.java#L204-L228).  The other two elements returned are: `packageVersion` and `outboundLicenses`.

The [third time](https://github.com/fasten-project/fasten/blob/e9d1bb51c3f54f35a70dc4f7336b902b4b47b46d/core/src/main/java/eu/fasten/core/data/metadatadb/MetadataDao.java#L219-L260) invoked, it creates the `updateMetadata` object that will update the metadata field through the `SET` SQL statement.

The [insertFileLicenses()](https://github.com/fasten-project/fasten/blob/endocode/compliancePlugin/analyzer/license-feeder/src/main/java/eu/fasten/analyzer/licensefeeder/LicenseFeederPlugin.java#L134-L156) function, similarly to the previous one, creates a JSONObject from the input record and searches for the JSONArray `files` in the payload. These files are the ones detected with license information by Scancode.

The files found are stored in the `fileLicenses` JSONArray. The function loops over this array and after checking that an array element has both, `path` and `licenses` keys, invokes the [insertFileLicenses()](https://github.com/fasten-project/fasten/blob/endocode/compliancePlugin/analyzer/license-feeder/src/main/java/eu/fasten/analyzer/licensefeeder/LicenseFeederPlugin.java#L148-L152) method.

The insertFileLicenses() method, as well as the insertPackageOutboundLicenses(), is declared inside of the MetadataDao class, and takes as input the `coordinates`, the `filePath`, and the `fileLicenses`, and updates the Metadata field of the `files` table through the [updateMetadata()](https://github.com/fasten-project/fasten/blob/e9d1bb51c3f54f35a70dc4f7336b902b4b47b46d/core/src/main/java/eu/fasten/core/data/metadatadb/MetadataDao.java#L324-L333) object declared inside of this method.

Identically to the previously described method, it iterates three times to retrieve the `groupId`, the `artifactId`, and the `version` from the coordinates, to pretty-print the `groupId` and the `artifactId`, and finally to update the Metadata field of the file table through the `SET` SQL statement called inside of the `updateMetadata` object.

