# WALA Plugin
#### This tool generates call graphs in FASTEN format using [WALA](http://wala.sourceforge.net/wiki/index.php/Main_Page) call graph generator version '1.0.0'.

## Arguments
Required arguments:
- `-jre` Specifies an absolute path to JRE 8 that is necessary for running WALA Plugin.

Generating a call graph from a Maven coordinate:
- `-g` `--group` Specifies **groupID** of the package for which the callgraph will be generated. Used together with `-a` & `-v`.
- `-a` `--artifact` Specifies **artifactID** of the package for which the callgraph will be generated. Used together with `-g` & `-v`.
- `-v` `--version` Specifies **version** of the package for which the callgraph will be generated. Used together with `-a` & `-g`.
- `-c` `--coord` Maven coordinate which is an alternative for specifying parts of the coordinate separately with `-g` & `-a` & `-v`.
- `-t` `--timestamp` Specify a product timestamp for the generated call graph. If omitted a placeholder timestamp will be used.

Generating a call graph from a set of Maven coordinates:
- `-s` `--set` A path containing a list of Maven coordinates in JSON format.

Generating a call graph from a `.jar` file:
- `-f` `--file` A path tho the `.jar` file for which a call graph should be generated.
- `-p` `--product` Specify a product name for the generated call graph. If omitted a placeholder name will be used.
- `-pv` `--productversion` Specify a product version for the generated call graph. If omitted a placeholder version will be used.
- `-d` `--dependencies` Specify dependencies for the generated call graph. If omitted no dependencies are added.

Writing the output:
- `-o` `--output` Specifies a directory into which a generated call graph will be written. Filename will be `<productName>-v<version>.json`
- `--stdout` If present a generated call graph will be written to standard output.

## Examples
#### From Maven Coordinate
Generating call graph for `org.slf4j:slf4j-api:1.7.29`:
```
-jre <JREPath> -g org.slf4j -a slf4j-api -v 1.7.29 --stdout
```
```
-jre <JREPath> -c org.slf4j:slf4j-api:1.7.29 --stdout
```

Result will be written in the given path:
```
-jre <JREPath> -c org.slf4j:slf4j-api:1.7.29 -o <ResultPath>
```
#### From a set of Maven coordinates
Following is the format of the coordinates' list:
```
{"groupId":"groupId","artifactId":"artifactId","version":"1.0.0","date":123}
{"groupId":"otherGroupId","artifactId":"otherArtifactId","version":"2.0.0","date":123}
```
```
-jre <JREPath> -s <InputPath> -o <ResultPath>
```

#### From `.jar` file
The input will be a path to a `.jar` file instead of maven coordinate:
```
-jre <JREPath> -f <InputPath> -p product_name -pv 1.0.0 -t 123 --stdout
```
