# Datasets

Here, the description of datasets is given:
- `mvn.dataset`: It contains Maven coordinates to generate call graphs using a JAVA call graph generator such as the OPAL plug-in.
- `large.records`: It contains Maven coordinates that generate CGs with pretty big JSON strings (more than 100MB). This dataset helps us benchmark our compression solutions.

## Reading a dataset into a Kafka topic
To load a dataset like `mvn.dataset` and put its content into a topic, use the following command:

```
kafka-console-producer --broker-list localhost:9092 --topic maven.packages < mvn.dataset.txt
```
