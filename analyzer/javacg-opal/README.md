# OPAL Plugin
#### This tool generates call graphs in FASTEN format using [OPAL](https://www.opal-project.de/) call graph generator version '1.0.0'. This tool can also merge the resulted call graphs.

The arguments that this tool takes are as follows:
- By passing argument -a you specify the artifact you want to work with.
- Using -d you can specify the dependencies for the merge algorithm.
- Using -g one can generate call graphs in FASTEN RevisionCallGraph format.
- Argument -l is to specify the algorithm that does the merge, currently it supports RA and CHA.
- By specifying -m you can change the input mode of the tool by using "FILE" the inputs will be path to files and by "COORD" inputs will be maven coordinates.
- Argument -o is to specify the output path of the result.
- By using -s one can stitch the artifact call graph to dependency call graphs.
- Argument -t is a timestamp that will be written in the RevisionCallGraph as the release timestamp of the artifact.
- By passing -w you specify that the results should be written to the file.

# Examples
## Generate
Generating call graph for `org.slf4j:slf4j-api:1.7.29`:
```
-g -a "org.slf4j:slf4j-api:1.7.29" -m COORD
```

Result will be written in the given path:
```
-g -a "org.slf4j:slf4j-api:1.7.29" -m COORD -o <ResultPath> -w
```

The input will be a path to a jar file or `.class` files instead of maven coordinate:
```
-g -a <InputPath> -m FILE -o <ResultPath> -w
```
## Merge
This command generates a call graph for artifact and dependencies that we pass and stitches the artifact call graph to the passed dependencies using the specified algorithm.
```
-s -a <ArtifactPath> -d <DepPath1>,<DepPath2>,... -m FILE -o <ResultPath> -w -l CHA
```

Stitches the maven coordinates call graphs and writes the artifact resolved call graph in the output path:
```
-s -a abbot:costello:1.4.0 -d abbot:abbot:1.4.0 -m COORD -o <ResultPath> -w -l CHA
```
