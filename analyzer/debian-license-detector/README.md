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

The License detector is triggered by the [`fasten.SyncJava.out`] Kafka consumer topic, produced by `Flink's JobManager`.

The function [`extractRepoPath`](https://github.com/fasten-project/fasten/blob/endocode/compliancePlugin/analyzer/license-detector/src/main/java/eu/fasten/analyzer/licensedetector/LicenseDetectorPlugin.java#L294-L314) extracts the repository path from the `Repo cloner` Kafka message `fasten.RepoCloner.out`.

The function [`extractRepoUrl`](https://github.com/fasten-project/fasten/blob/endocode/compliancePlugin/analyzer/license-detector/src/main/java/eu/fasten/analyzer/licensedetector/LicenseDetectorPlugin.java#L316-L332) extracts the repository URL, always from the `Repo cloner` Kafka message `fasten.RepoCloner.out`.

## Retrieving the outbound license 

The [`getOutboundLicenses()`](https://github.com/fasten-project/fasten/blob/endocode/compliancePlugin/analyzer/license-detector/src/main/java/eu/fasten/analyzer/licensedetector/LicenseDetectorPlugin.java#L120-L163) function, which is in charge of retrieving the license declared for the given project, the so-called `outbound` license, needs a repository path and URL.

The plugin will first search for the `pom.xml`, passing the repository path to the [`retrievePomFile()`](https://github.com/fasten-project/fasten/blob/endocode/compliancePlugin/analyzer/license-detector/src/main/java/eu/fasten/analyzer/licensedetector/LicenseDetectorPlugin.java#L334-L370) function and then running the [`getLicensesFromPomFile()`](https://github.com/fasten-project/fasten/blob/endocode/compliancePlugin/analyzer/license-detector/src/main/java/eu/fasten/analyzer/licensedetector/LicenseDetectorPlugin.java#L165-L214) function upon the `pom.xml` file, if retrieved, to retrieve the `outbound` license. 

The first function will look at all the `pom.xml` files (considering that more than one `pom.xml` could be there) inside the repository folder, choosing the one closest to the root path.

The second function will parse the `pom.xml`; if license information is present will return the detected license.

If license information in the `pom.xml` is missing, the license detector will query the `GitHub APIs` through the [`getLicenseFromGitHub()`](https://github.com/fasten-project/fasten/blob/6e92f3c814865e5e53b226d8a76ef28867218f32/analyzer/license-detector/src/main/java/eu/fasten/analyzer/licensedetector/LicenseDetectorPlugin.java#L215-L292) function. If the repository URL is hosted on GitHub and the API `JSON` response contains the license key, this will be set as the outbound license for the analyzed package.

If any `outbound` license is retrieved, the [`chttps://github.com/fasten-project/fasten/blob/6e92f3c814865e5e53b226d8a76ef28867218f32/analyzer/license-detector/src/main/java/eu/fasten/analyzer/licensedetector/LicenseDetectorPlugin.java#L76-L118ume()`](url) method will add this information at the [`fasten.LicenseDetector.out`](https://github.com/fasten-project/fasten/wiki/Kafka-Topics#fastenlicensedetector) Kafka message.


## Detecting license information at the file level

After retrieving the `outbound` license, the license detector will scan using `Scancode` to detect license information at the file level. The function [`scanProject()`](https://github.com/fasten-project/fasten/blob/6e92f3c814865e5e53b226d8a76ef28867218f32/analyzer/license-detector/src/main/java/eu/fasten/analyzer/licensedetector/LicenseDetectorPlugin.java#L371-L440) will take in input the repository path and will provide as output  a `scancode.json` file (which will be added to the path), which will be then parsed by the [`parseScanResult()`](https://github.com/fasten-project/fasten/blob/6e92f3c814865e5e53b226d8a76ef28867218f32/analyzer/license-detector/src/main/java/eu/fasten/analyzer/licensedetector/LicenseDetectorPlugin.java#L441-L470) function. This function will store the set of licenses detected at the file level in a `JSONArray`, called [`fileLicenses`](https://github.com/fasten-project/fasten/blob/6e92f3c814865e5e53b226d8a76ef28867218f32/analyzer/license-detector/src/main/java/eu/fasten/analyzer/licensedetector/LicenseDetectorPlugin.java#L106-L112). 

This `JSONArray`, if not empty, will be processed by the [`detectedLicenses.addFiles()`](https://github.com/fasten-project/fasten/blob/6e92f3c814865e5e53b226d8a76ef28867218f32/core/src/main/java/eu/fasten/core/data/metadatadb/license/DetectedLicenses.java#L31-L33
) function, which will include it in the [`fasten.LicenseDetector.out`](https://github.com/fasten-project/fasten/wiki/Kafka-Topics#fastenlicensedetector) Kafka message, which the `license feeder` will later process.
























<!-- TODO Provide description -->

<!-- TODO Shall we provide an example that can be manually triggered?

## Arguments

## Usage

1. (Optional) When using a custom Kubernetes namespace, make sure to create it first:
    ```bash
    kubectl create namespace myownnamespace
    ``` 

1. (Optional) Install [Kafka](https://github.com/bitnami/charts/tree/master/bitnami/kafka) in your cluster:
    ```bash
    helm repo add bitnami https://charts.bitnami.com/bitnami
    helm install \
        --set service.type=LoadBalancer \
        --set externalAccess.enabled=true \
        fasten-kafka-instance \
        bitnami/kafka
    ```
    -
        This plugin sends the license report back to Kafka using
        its default
        Helm chart
        address.
        \
        If you need to use your own Kafka instance,
        please set its address
        [here](https://github.com/fasten-project/fasten/blob/d42f3ec828d0e6c0663e7db566b0b18df2b0d5a7/analyzer/compliance-analyzer/src/main/resources/k8s/qmstr/job.yaml#L39).
    -
        Make sure to add:
        ```
        --namespace myownnamespace
        ```
        when using a custom Kubernetes namespace.

1. Start the plugin specifying the path to the cluster credentials file as an environment variable:
    
   1. From the FASTEN server:
        1.  When using the Kafka instance installed in the cluster in step 1,
            first retrieve its public IP with:
            ```bash
            export KAFKA_ADDRESS=$(kubectl get svc fasten-kafka-instance -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
            ```
        1. Start the plugin using the FASTEN server JAR (from the project root):
            ```bash
            java \
                -jar docker/server/server-0.0.1-SNAPSHOT-with-dependencies.jar \
                --kafka_server ${KAFKA_ADDRESS}:9092 \
                --plugin_dir $(pwd)/docker/plugins \
                --plugin_list LicenseDetector \
                --topic LicenseDetector=fasten.RepoCloner.out
            ```
       
   1. As a standalone Maven plugin:
       ```bash
       # Example: from the FASTEN root folder
       mvn \
         -DclusterCredentials=path/to/cluster/credentials.json \
         clean install exec:java \
         -f analyzer/license-detector/pom.xml \
         -Dexec.args="--repository analyzer/license-detector/dummyKafkaTopic.json"
       ```
       -
           This demo simulates a Kafka message consumption by reading the [`dummyKafkaTopic.json` file](dummyKafkaTopic.json).
       -
           Upon consuming the message, the plugin starts Quartermaster, that will build and analyze the specified repository.
       -
           Make sure to include the Kubernetes namespace when using a custom one:
           ```
           -Dexec.args="--repository analyzer/license-detector/dummyKafkaTopic.json --namespace myownnamespace"
           ```


The plugin then generates a [`fasten.qmstr.*` Kafka message](https://github.com/fasten-project/fasten/wiki/Kafka-Topics#fastenqmstr).

## Extra

<details>
<summary>Metadata visualization</summary>

Here are the necessary steps needed to visualize the detected metadata stored in our graph database.

<p align="center">
    <img src="https://raw.githubusercontent.com/fullsushidev/qmstr/master/doc/static/img/qmstr-plugin.gif">
</p>
  
1. Wait for the build and analysis phases to be over:
    ```bash
    kubectl logs --follow $(kubectl get pods --selector job-name=qmstr -o=name) qmstr-client
    ```

1. Forward two local ports to the following two ports on the DGraph Pod:
    ```bash
    kubectl port-forward $(kubectl get pods --selector job-name=qmstr -o=name) 8000:8000
    ```
    ```bash
    kubectl port-forward $(kubectl get pods --selector job-name=qmstr -o=name) 8080:8080
    ```

1. Open [localhost:8000/?latest](http://localhost:8000/?latest) in your browser.

1. Click on "Continue":
    <p align="center">
        <img src="https://user-images.githubusercontent.com/45048351/92643192-0649f280-f2ea-11ea-842d-ee9612f54f7c.png" alt="DGraph login page" width="75%"/>
    </p>

1. Navigate to the "Console" page.

1. You should now be able to query the database:  
    ```graphql
    {
        PackageNodes(func: has(packageNodeType)) @recurse(loop: true, depth: 3) {
            uid
            name
            version
            packageNodeType
            targets
            additionalInfo
            buildConfig
            diagnosticInfo
            timestamp
        }
    
        FileNodes(func: has(fileNodeType)) @recurse(loop: true, depth: 3) {
            uid
            fileNodeType
            path
            name
            fileData
            timestamp
            derivedFrom
            dependencies
        }
    
        FileDataNodes(func: has(fileDataNodeType)) @recurse(loop: true, depth: 3) {
            uid
            fileDataNodeType
            hash
            additionalInfo
            diagnosticInfo
        }
    
        InfoNodes(func: has(infoNodeType)) @recurse(loop: true, depth: 3) {
            uid
            infoNodeType
            type
            confidenceScore
            analyzer
            dataNodes
            timestamp
        }
    
        Analyzers(func: has(analyzerNodeType)) @recurse(loop: true, depth: 3) {
            uid
            name
            analyzerNodeType
            trustLevel
            pathSub
            old
            new
        }
    
        DataNodes(func: has(dataNodeType)) @recurse(loop: true, depth: 3) {
            uid
            dataNodeType
            type
            data
            timestamp
        }
    }
    ```

1. The generated graph should look something like this:
    <p align="center">
        <img src="https://raw.githubusercontent.com/wiki/fasten-project/fasten/img/qmstr/build_graph.png" alt="Generated Build Graph example"/>
    </p>
    The left part of the graph consists in the usual build graph, having in this case a single (Java) package node in green as the central node.
    License and compliance information is on the right, having the analyzer node in pink right in the middle.  
</details>

-->

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
