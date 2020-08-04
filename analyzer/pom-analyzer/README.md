<p align="center">
    <img src="https://user-images.githubusercontent.com/45048351/89289971-cc3d5f00-d660-11ea-90f8-693d8c96bbbb.jpg">
</p>
<br/>
<p align="center">
    <a href="https://github.com/fasten-project/fasten/actions" alt="GitHub Workflow Status">
        <img src="https://img.shields.io/github/workflow/status/fasten-project/fasten/Java%20CI?logo=GitHub%20Actions&logoColor=white&style=for-the-badge" /></a>
    <!-- Here should be a link to Maven repo and version should be pulled from there. -->
    <a href="https://github.com/fasten-project/fasten/" alt="GitHub Workflow Status">
                <img src="https://img.shields.io/maven-central/v/fasten/pomanalyzer?label=version&logo=Apache%20Maven&style=for-the-badge" /></a>
</p>
<br/>

The FASTEN POM Analyzer plugin is a tool that analyzes Maven coordinate's [POM](https://maven.apache.org/ref/3.6.3/maven-model/maven.html) file and extracts relevant information from it while also inserting this information into the [metadata database](https://github.com/fasten-project/fasten/wiki/Metadata-Database-Schema).

## Arguments
- `-h` `--help` Show this help message and exit.
- `-f` `--file` Path to JSON file which contains Revision Call Graph
- `-d` `--database` Database URL for connection
- `-u` `--user` Database user name
- `-a` `--artifactId` Artifact ID of the Maven coordinate
- `-g` `--groupId` Group ID of the Maven coordinate
- `-v` `--version` Version of the Maven coordinate

## Usage 

#### Inserting POM information from JSON file into the metadata database
```shell script
FASTEN_DBPASS=pass ... -f mvn_coordinates/coordinate.json -d jdbc:postgresql:postgres -u postgres
```

#### Inserting POM information from JSON file into the metadata database
```shell script
FASTEN_DBPASS=pass ... -a junit -g junit -v 4.12 -d jdbc:postgresql:postgres -u postgres
```


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
