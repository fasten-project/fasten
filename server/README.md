<p align="center">
    <img src="https://user-images.githubusercontent.com/45048351/89221996-e9304e80-d5dc-11ea-8e09-431293157bae.jpg">
</p>
<br/>
<p align="center">
    <a href="https://github.com/fasten-project/fasten/actions" alt="GitHub Workflow Status">
        <img src="https://img.shields.io/github/workflow/status/fasten-project/fasten/Java%20CI?logo=GitHub%20Actions&logoColor=white&style=for-the-badge" /></a>
    <!-- Here should be a link to Maven repo and version should be pulled from there. -->
    <a href="https://github.com/fasten-project/fasten/" alt="GitHub Workflow Status">
                <img src="https://img.shields.io/maven-central/v/fasten/server?label=version&logo=Apache%20Maven&style=for-the-badge" /></a>
</p>
<br/>

The FASTEN-server is a component necessary for running [FASTEN-specific plugins](https://github.com/fasten-project/fasten/tree/master/analyzer), consuming and producing to a Kafka topic, and accessing a database.

## Arguments
- `-b` `--base_dir` Path to base directory to which data will be written;
- `-d` `--database` Kay-value pairs of Database URLs for connection. Valid keys are `mvn`, `pypi`, `debian` for Java, Python and C, respectively. E.g.`mvn=jdbc:postgresql://postgres@localhost/dbname`;
- `-gd` `--graphdb_dir` Path to directory with RocksDB database;
- `-h` `--help` Show this help message and exit;
- `-k` `--kafka_server` Kafka server to connect to. Use multiple times for clusters;
    - `-ks` `--skip_offsets` Adds one to offset of all the partitions of the consumers;
    - `-kt` `--topic` Kay-value pairs of Plugin and topic to consume from. Example: `OPAL=fasten.OPAL.out`;
- `-la` `--list_all` List all values and extensions;
- `-m` `--mode` Deployment or Development mode
- `-p` `--plugin_dir` Directory to load plugins from.
    - `-pl` `--plugin_list` List of plugins to run. Can be used multiple times.
    - `-po` `--plugin_output` Path to directory where plugin output messages will be stored
    - `-pol` `--plugin_output_link` HTTP link to the root directory where output messages will be stored
- `cg` `--consumer_group` Name of the consumer group. Defaults to (canonical) name of the plugin.
- `ot` `--output_topic` Name of the output topic. Defaults to (simple) name of the plugin.
- `ct` `--consume_timeout` Adds a timeout on the time a plugin can spend on its consumed records. Disabled by default.
- `cte` `--consume_timeout_exit` Shutdowns the JVM if a consume timeout is reached. 
- `ls` `--local_storage` Enables local storage which stores the record that is currently processed. This ensure that records that were processed before won't be processed again (e.g. when the pod crashes). Local storage relies on the `--local_storage_dir` flag, to store message hashes. Furthermore, the environment variable `POD_INSTANCE_ID` needs to be available with a static and unique id per pod.  
- `-V` `--version` Print version information and exit.

## Usage 

#### Show all available plugins
```shell script
-p path/to/plugins/dir -la
```

#### Run a specific FASTEN-plugin that consumes and produce to a Kafka topic
```shell script
-p path/to/plugins/dir -pl OPAL -k localhost:9092 -kt some.kafka.topic 
```

#### Run a specific FASTEN-plugin that requires a database connection
```shell script
-p path/to/plugins/dir -pl POMAnalyzer -d mvn=jdbc:postgresql://postgres@localhost/dbname
```

#### Run a specific FASTEN-plugin that writes output to files
```shell script
-p path/to/plugins/dir -pl OPAL -po some/output/dir -pol http://some.out/link
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
