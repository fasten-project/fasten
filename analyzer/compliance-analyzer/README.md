<p align="center">
    <img src="https://user-images.githubusercontent.com/45048351/91091340-1c578200-e65f-11ea-9c5d-597fbbe4ba41.jpg">
</p>
<br/>
<p align="center">
    <a href="https://github.com/fasten-project/fasten/actions" alt="GitHub Workflow Status">
        <img src="https://img.shields.io/github/workflow/status/fasten-project/fasten/Java%20CI?logo=GitHub%20Actions&logoColor=white&style=for-the-badge" /></a>
    <!-- Here should be a link to Maven repo and version should be pulled from there. -->
    <a href="https://github.com/fasten-project/fasten/" alt="GitHub Workflow Status">
                <img src="https://img.shields.io/maven-central/v/fasten/graph?label=version&logo=Apache%20Maven&style=for-the-badge" /></a>
</p>
<br/>

The FASTEN QMSTR plugin is a tool to integrate the [QMSTR](https://github.com/qmstr/qmstr) license and compliance analysis process into FASTEN.\
This integration is part of the WP4 and it's being developed by [Endocode AG](https://endocode.com).

## Arguments
- `-r` `--repo` `--repository`                Path to JSON file containing repository information
- `-c` `--credentials` `--cluster-credentials`  Path to the Kubernetes cluster credentials file

## Usage 

#### <!-- TODO ultimate goal -->
1. Start the plugin:
    ```bash
    # Example: from the FASTEN root folder
    mvn clean install exec:java \
      -f analyzer/compliance-analyzer/pom.xml \
      -Dexec.args="--repository analyzer/compliance-analyzer/dummyKafkaTopic.json --cluster-credentials path/to/cluster/credentials.json"
    ```
   This demo simulates a Kafka topic consumption by reading the [`dummyKafkaTopic.json` file](dummyKafkaTopic.json).\
   Upon topic consumption, the `compliance-analyzer` launches Quartermaster that will build the specified repository. 

1. Wait for the building process to be over:
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
        <img src="img/dgraph_login.png" alt="DGraph login page" width="75%"/>
    </p>

1. Navigate to the "Console" page

1. You should now be able to query the database
    ```graphql
    {
        # *.class files
        PackageNode(func: has(packageNodeType)) {
            uid 
            name
            version
            buildConfig
            timestamp
            packageNodeType
            targets { # a.k.a. "target FileNodes"
                uid
                path
                name
                timestamp
                fileNodeType
            }
        }

        # FileNodes of compiled outer and inner [anonymous] classes
        FileNode(func: has(fileNodeType)) {
            uid
            fileNodeType
            path
            name
            timestamp
            fileData { # points to "target FileNodes"
                uid
                fileDataNodeType
                hash
            }
            derivedFrom { # *.java files
                uid
                fileNodeType
                path
                name
                timestamp
                fileData { # outer FileData nodes
                    uid
                    fileDataNodeType
                    hash
                }
            }
        }
    }
    ```

1. The generated Build Graph should look something like this:
    <p align="center">
        <img src="img/build_graph.png" alt="Generated Build Graph example"/>
    </p>

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
