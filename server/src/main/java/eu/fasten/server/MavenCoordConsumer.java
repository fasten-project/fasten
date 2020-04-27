package eu.fasten.server;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/**
 * This plugin consumes Maven coordinates for creating call graphs.
 * Please note that this is highly experimental and does not adhere to conventions of Kafkas interfaces
 * until the plugin's functionalities are discussed and reviewed.
 */

@CommandLine.Command(name = "MavenCoordConsumer", description = "Generates call graphs from Maven coordinates.")
public class MavenCoordConsumer implements Runnable {

    @CommandLine.Option(
            names = {"-h", "--host"},
            defaultValue = "localhost",
            description = "The IP address of the Kafka server")
    String IP;

    @CommandLine.Option(
            names = {"-p", "--port"},
            defaultValue = "9092",
            description = "The port of the Kafka server.")
    String port;

    private KafkaConsumer<String, String> MVCConsumer;
    private final Logger logger = LoggerFactory.getLogger(MavenCoordConsumer.class.getName());
    private static final String topic = "maven.packages";
    private final String groupId = "some_app";

    private Properties consumerProps(String serverProperties) {
        String deserializer = StringDeserializer.class.getName();
        Properties properties = new Properties();

        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, serverProperties);
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, this.groupId);
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, deserializer);
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return properties;
    }

    private class ConsumerRunnable implements Runnable {
        private CountDownLatch mLatch;

        ConsumerRunnable(CountDownLatch latch) {
            mLatch = latch;
            MVCConsumer.subscribe(Collections.singletonList(topic));
        }

        @Override
        public void run() {
            try {

                do {
                    ConsumerRecords<String, String> records =
                            MVCConsumer.poll(Duration.ofMillis(100));

                    for (ConsumerRecord<String, String> r : records) {
                        System.out.println(r.key() + " " + r.value());

                        JSONObject mvn_record = new JSONObject(r.value());
                        System.out.println("GroupID: " + mvn_record.getString("groupId"));
                        System.out.println("ArtifactID: " + mvn_record.getString("artifactId"));
                        System.out.println("version: " + mvn_record.getString("version"));
                        System.out.println("datetime: " + mvn_record.getString("date"));

                    }

                } while (true);
            } catch (WakeupException e) {
                logger.info("Received shutdown signal!");
            } finally {
                MVCConsumer.close();
                mLatch.countDown();
            }
        }

        void shutdown() {
            MVCConsumer.wakeup();
        }
    }

    @Override
    public void run() {
        Properties props = consumerProps(IP + ":" + port);
        this.MVCConsumer = new KafkaConsumer<>(props);

        logger.info("Consumer initialized");

        CountDownLatch latch = new CountDownLatch(1);

        ConsumerRunnable consumerRunnable = new ConsumerRunnable(latch);
        Thread thread = new Thread(consumerRunnable);
        thread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.debug("Caught shutdown hook");
            consumerRunnable.shutdown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            logger.debug("MVC Consumer has exited");
        }));

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MavenCoordConsumer()).execute(args);
        System.exit(exitCode);
    }
}
