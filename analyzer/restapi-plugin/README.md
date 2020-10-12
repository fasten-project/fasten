<p align="center">
    <img src="https://user-images.githubusercontent.com/45048351/90059323-a7458d80-dceb-11ea-938b-a7288e784397.jpg">
</p>
<br/>
<p align="center">
    <a href="https://github.com/fasten-project/fasten/actions" alt="GitHub Workflow Status">
        <img src="https://img.shields.io/github/workflow/status/fasten-project/fasten/Java%20CI?logo=GitHub%20Actions&logoColor=white&style=for-the-badge" /></a>
    <!-- Here should be a link to Maven repo and version should be pulled from there. -->
    <a href="https://github.com/fasten-project/fasten/" alt="GitHub Workflow Status">
                <img src="https://img.shields.io/maven-central/v/fasten/restapi?label=version&logo=Apache%20Maven&style=for-the-badge" /></a>
</p>
<br/>

The FASTEN Rest API Plugin is a tool to expose canned queries from the [Metadata Database](https://github.com/fasten-project/fasten/wiki/Metadata-Database-Schema).
<!-- It can be used both as a standalone tool and as a part of FASTEN server. -->

## Design

- [Endpoints details](https://github.com/fasten-project/fasten/wiki/API-endpoints-for-Maven-projects)

## Arguments
- `-h` `--help` Show this help message and exit.
- `-p` `--port` REST server port.
- `-d` `--db` `--database` KnowledgeBase URL.
- `-u` `--user` `--username` KnowledgeBase username.

## Usage

1. Port-forward the remote Postgres server to a local port, e.g., `5433`:    
    ```bash
    export KB_USER=...
    export KB_ADDR=...
    ssh -f ${KB_USER}@${KB_ADDR} -L 5433:${KB_ADDR}:5432 -N
    ```
1. Launch the REST server (example from the root directory and with port `9090`):
    ```bash
    PGPASSWORD=... mvn clean install exec:java \
      -f analyzer/restapi-plugin/pom.xml \
      -Dexec.args='--port 9090 --database jdbc:postgresql://localhost:5433/<DB_NAME> --username <KB_USER>'
    ```

<!-- TODO ### Requirements  -->

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
