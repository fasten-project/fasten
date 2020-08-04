<p align="center">
    <img src="https://user-images.githubusercontent.com/45048351/89289824-8bdde100-d660-11ea-86c9-77a464f58e3d.jpg">
</p>
<br/>
<p align="center">
    <a href="https://github.com/fasten-project/fasten/actions" alt="GitHub Workflow Status">
        <img src="https://img.shields.io/github/workflow/status/fasten-project/fasten/Java%20CI?logo=GitHub%20Actions&logoColor=white&style=for-the-badge" /></a>
    <!-- Here should be a link to Maven repo and version should be pulled from there. -->
    <a href="https://github.com/fasten-project/fasten/" alt="GitHub Workflow Status">
                <img src="https://img.shields.io/maven-central/v/fasten/metadata?label=version&logo=Apache%20Maven&style=for-the-badge" /></a>
</p>
<br/>

The FASTEN Metadata Plugin is used for inserting [Revision Call Graphs and their metadata](https://github.com/fasten-project/fasten/wiki/Revision-Call-Graph-format) into [Metadata Database](https://github.com/fasten-project/fasten/wiki/Metadata-Database-Schema). It can be used both as a standalone tool and as a part of FASTEN server.

## Arguments
- `-h` `--help` Show this help message and exit.
- `-f` `--file` Path to JSON file which contains Revision Call Graph
- `-d` `--database` Database URL for connection
- `-u` `--user` Database user name

## Usage 

#### Inserting Revision Call Graph in the metadata database
```shell script
FASTEN_DBPASS=pass ... -f revision_cgs/callgraph.json -d jdbc:postgresql:postgres -u postgres
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
