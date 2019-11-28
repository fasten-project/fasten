package eu.fasten.core.plugins;

import org.apache.kafka.clients.producer.KafkaProducer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class MavenCoordProducer implements FastenPlugin {

    /**
     * Utility function and class
     */

    /**
     * Encapsulates the properties of a Maven project.
     */
    static class MavenProject{
        public String groupId;
        public String artifactId;
        public String version;
        public String timestamp;

        public MavenProject(String groupId, String artifactId, String version, String timestamp) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.timestamp = timestamp;
        }
    }

    /**
     * getting Maven coordinates from JSON string
     */
    static public MavenProject getMVCFromJSON(String jsonString){
        JSONObject mvc = new JSONObject(jsonString);
        return new MavenProject(mvc.getString("groupId"), mvc.getString("artifactId"), mvc.getString("version"), mvc.getString("date"));
    }

    //****************************************************************************************************************

    final KafkaProducer<String, String> MVCProducer;
    private static Logger logger = LoggerFactory.getLogger(MavenCoordProducer.class);
    private static final String topic = "maven.packages";

    @Override
    public String name() {
        return "MavenCoordProducer";
    }


    @Override
    public String description() {
        return "This plugin reads Maven coordinates from the crawler and puts the coordinates in a topic";
    }

    MavenCoordProducer(String serverProperties){

        Properties props = producerProperties(serverProperties);
        this.MVCProducer = new KafkaProducer<String, String>(props);

        logger.info("Producer initialized");
    }

    private Properties producerProperties(String serverProperties)
    {
        String serializer = StringSerializer.class.getName();
        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, serverProperties);
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, serializer);
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, serializer);

        return props;
    }

    private void run() throws IOException, InterruptedException, ExecutionException {

        // TODO: The path of the Python crawler is hard-coded. A CLI is needed to pass this.
        // Starts the Maven crawler (Python script)
        ProcessBuilder ps = new ProcessBuilder("python", "-W ignore", "/Users/amir/projects/mvn-crawler/maven_crawler.py");
        ps.redirectErrorStream(true);
        Process crawler = ps.start();

        BufferedReader crawlerOutput = new BufferedReader(new InputStreamReader(crawler.getInputStream()));
        String line;
        while ((line = crawlerOutput.readLine()) != null){
            System.out.println(line);
            MavenProject mvc = getMVCFromJSON(line);
            System.out.println(mvc.groupId + " " + mvc.artifactId + " " + mvc.version + " " + mvc.timestamp);
            put("groupId", mvc.groupId);
            put("artifactId", mvc.artifactId);
            put("version", mvc.version);
            put("date", mvc.timestamp);
        }

        crawler.waitFor();
    }

    private void put(String key, String value) throws ExecutionException, InterruptedException {
        logger.debug("Put value: " + value + ", for key:" + key);

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        MVCProducer.send(record, ((recordMetadata, e) -> {
            if(e != null){
                logger.error("Error while producing", e);
                return;
            }

            logger.debug("Received new meta. \n" +
                    "Topic: " + recordMetadata.topic() + "\n" +
                    "Partition: " + recordMetadata.partition() + "\n" +
                    "Offset: " + recordMetadata.offset() + "\n" +
                    "Timestamp: " + recordMetadata.timestamp());
        } )).get();
    }

    @Override
    public void start() {
        logger.debug("MavenCoordProducer plugin started....");
    }

    @Override
    public void stop() {
        System.out.println("Stopped...");
    }

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        String jsonData = "{\"groupID\": \"activecluster\", \"artifcatID\": \"activecluster\", \"version\": \"4.0.2\"}";
        JSONObject mvc = new JSONObject(jsonData);
        System.out.println(mvc.getString("groupID"));

        String serverAddress = "127.0.0.1:9092";

        MavenCoordProducer mvcProducer = new MavenCoordProducer(serverAddress);
        mvcProducer.run();


    }
}
