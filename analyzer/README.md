<p align="center">
    <img src="https://user-images.githubusercontent.com/45048351/89231067-3ddbc580-d5ed-11ea-9639-2838059dda2c.jpg">
</p>
<br/>
<p align="center">
    <a href="https://github.com/fasten-project/fasten/actions" alt="GitHub Workflow Status">
        <img src="https://img.shields.io/github/workflow/status/fasten-project/fasten/Java%20CI?logo=GitHub%20Actions&logoColor=white&style=for-the-badge" /></a>
    <!-- Here should be a link to Maven repo and version should be pulled from there. -->
    <a href="https://github.com/fasten-project/fasten/" alt="GitHub Workflow Status">
                <img src="https://img.shields.io/maven-central/v/fasten/core?label=version&logo=Apache%20Maven&style=for-the-badge" /></a>
</p>
<br/>

A collection of FASTEN-specific plugins with an ability to be loaded by and run on the [FASTEN server](https://github.com/fasten-project/fasten/tree/master/server). Most of the plugins are also capable of running as standalone applications. Take a look at README of respective plugins to learn more.

## Plugins

#### Graph plugin
The FASTEN Graph Plugin is a tool for inserting [GID Graphs](https://github.com/fasten-project/fasten/wiki/GID-Graph-format) into Graph database ([RocksDB](https://rocksdb.org/)) and retrieving them from it.

#### OPAL plugin
The FASTEN OPAL is a tool for generating call graphs in FASTEN format using [OPAL](https://www.opal-project.de/) call graph generator version '3.0.0'. This tool can also merge the resulted call graphs with their dependencies.

#### Metadata plugin
The FASTEN Metadata Plugin is used for inserting [Revision Call Graphs and their metadata](https://github.com/fasten-project/fasten/wiki/Revision-Call-Graph-format) into [Metadata Database](https://github.com/fasten-project/fasten/wiki/Metadata-Database-Schema).

#### POM analyzer
The FASTEN POM Analyzer plugin is a tool that analyzes Maven coordinate's [POM](https://maven.apache.org/ref/3.6.3/maven-model/maven.html) file and extracts relevant information from it while also inserting this information into the [metadata database](https://github.com/fasten-project/fasten/wiki/Metadata-Database-Schema).

#### Quality analyzer
The FASTEN QualityAnalyzer plugin is used to insert source code quality analysis metadata into [metadata database](https://github.com/fasten-project/fasten/wiki/Metadata-Database-Schema).

#### Repo Cloner
The FASTEN RepoCloner plugin is a tool for cloning repositories into the file system.

#### Rest API
The FASTEN Rest API Plugin is a tool to expose canned queries from the [Metadata Database](https://github.com/fasten-project/fasten/wiki/Metadata-Database-Schema).
<!-- It can be used both as a standalone tool and as a part of FASTEN server. -->

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
