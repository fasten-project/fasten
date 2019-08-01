package eu.fasten.pipeliner;



import eu.fasten.analyzer.CallGraphGenerator;
import eu.fasten.analyzer.data.fastenJSON.CanonicalJSON;
import eu.fasten.analyzer.generator.WalaUFIAdapter;
import eu.fasten.analyzer.serverContact.Artifact;
import eu.fasten.analyzer.serverContact.KafkaConsumerMonster;
import eu.fasten.analyzer.serverContact.KafkaProducerMonster;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;


public class Pipeliner {

    private static Logger logger = LoggerFactory.getLogger(Pipeliner.class);

    public static <Method> void main(String[] args) {
        int projectNumber = 0;
        String nonResolvables = "";
        final Consumer<String, String> consumer = KafkaConsumerMonster.createConsumer();
        WalaUFIAdapter wrapped_cg = null;
        try {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Long.MAX_VALUE);
                for (ConsumerRecord<String, String> record : records) {
                    JSONObject jsonObject = new JSONObject(record.value());
                    Artifact artifact = new Artifact(record.offset(), jsonObject.get("groupId").toString(), jsonObject.get("artifactId").toString(), jsonObject.get("version").toString(), jsonObject.get("date").toString());
                    String coordinate = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":"+ artifact.getVersion();
                    File tempFile = new File("canonicalGraphs/"+projectNumber+"-"+coordinate);
                    if(!tempFile.exists()) {
                        logger.info("Call Graph Generation for :{}", coordinate);
                        projectNumber++;
                        wrapped_cg = CallGraphGenerator.generateCallGraph(coordinate);
                        if (wrapped_cg == null) {
                            nonResolvables += + projectNumber + "-" + coordinate + "\n";
                            continue;
                        }
                        KafkaProducerMonster.runProducer(projectNumber, CanonicalJSON.createJson(wrapped_cg, artifact.getDate()));
                        logger.info("producing done!");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error Occured", e);
        } finally {
            consumer.close();
        }
    }

}
