# OPAL Plugin
#### This tool generates call graphs in FASTEN format using [OPAL](https://www.opal-project.de/) call graph generator version '1.0.0'. This tool can also merge the resulted call graphs.

The arguments that this tool takes are as follows:
- `-m, --mode` gets the input mode of the tool. "FILE" makes the inputs path to files and "COORD" makes inputs maven coordinates. Paths or coordinates will be specified by argument `-a` and `-d`.
- `-a, --artifact` gets the artifact you want to work with. The input of CG generation or merging call graphs is placed after this argument.
- `-d, --dependencies` gets one or more dependencies for the merge algorithm, the separator between dependencies is `,`.
- `-g, --generate` specifies the act of generating call graphs in FASTEN RevisionCallGraph format.
- `-l, --algorithm` gets the algorithm that does the merge, currently it supports RA and CHA.
- `-o, --output` gets the output path for the results.
- `-s, --stitch` specifies the act of stitching the artifact's call graph to dependency call graphs.
- `-t, --timestamp` gets a timestamp that will be written in the RevisionCallGraph as the release timestamp of the artifact.

# Usage: 
```
java -jar javacg-opal-0.0.1-SNAPSHOT-with-dependencies.jar ([[-a=ARTIFACT] [-t=TS] [-m=MODE] [[[-g]] | [[-s] [-d=DEPS[,
                  DEPS...]] [-d=DEPS[,DEPS...]]... [-l=ALG]]]]) [-o=OUT]
```

# Examples
## Generate
Generating call graph for `org.slf4j:slf4j-api:1.7.29`:
```
-g -a "org.slf4j:slf4j-api:1.7.29" -m COORD
```

Result will be written in the given path:
```
-g -a "org.slf4j:slf4j-api:1.7.29" -m COORD -o <ResultPath>
```

The input will be a path to a jar file or `.class` files instead of maven coordinate:
```
-g -a <InputPath> -m FILE -o <ResultPath>
```
## Merge
This command generates a call graph for artifact and dependencies that we pass and stitches the artifact call graph to the passed dependencies using the specified algorithm:
```
-s -a <ArtifactPath> -d <DepPath1>,<DepPath2>,... -m FILE -o <ResultPath> -l CHA
```

Stitches the maven coordinates call graphs and writes the artifact resolved call graph in the output path:
```
-s -a abbot:costello:1.4.0 -d abbot:abbot:1.4.0 -m COORD -o <ResultPath> -l CHA
```
#### Note
Please note that this tool works with Java 8 compiled bytecode.