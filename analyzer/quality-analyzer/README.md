<p align="center">
    <img src="https://user-images.githubusercontent.com/45048351/89231067-3ddbc580-d5ed-11ea-9639-2838059dda2c.jpg">
</p>

<br/>
<p align="center">
    <a href="https://github.com/fasten-project/fasten/actions" alt="GitHub Workflow Status">
        <img src="https://img.shields.io/github/workflow/status/fasten-project/fasten/Java%20CI?logo=GitHub%20Actions&logoColor=white&style=for-the-badge" /></a>
    <!-- Here should be a link to Maven repo and version should be pulled from there. -->
    <a href="https://github.com/fasten-project/fasten/" alt="GitHub Workflow Status">
                <img src="https://img.shields.io/maven-central/v/fasten/vulnerability?label=version&logo=Apache%20Maven&style=for-the-badge" /></a>
</p>
<br/>


QualityAnalyzer is FASTEN plugin to store/update code quality metadata from Kafka topic. There is a single Kafka message per single callable produced 
by [Rapid Plugin](https://github.com/fasten-project/quality-analyzer/tree/master/rapidplugin) using [Lizard](https://github.com/terryyin/lizard). 
Once a metadata message is consumed, the metadata is stored to respective database depending on the forge (mvn, Debian, PyPI).

It can be used both as a standalone tool and as a part of FASTEN server.

## Arguments

- `-h` `--help` Show this help message and exit.
- `-d` `--database` Comma separated key-value pairs of Database URLs for connection
- `-f` `--file` Path to JSON file that contains JSON with Lizard tool generated quality metrics

## Usage 

A password must be provided through the environmental variable `$FASTEN_DBPASS`. 
Currently, all three databases use the same password (this will change).

To start as part of FASTEN server, 

```
FASTEN_DBPASS=pass ... -p /path-to/fasten/docker/plugins/ -k localhost:9092 -pl "QualityAnalyzer" -kt "QualityAnalyzer=fasten.RapidPlugin.out" -d "mvn=jdbc:postgresql://postgres@localhost/dbname,debian=jdbc:postgresql://postgres@localhost/pythondbname"
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




