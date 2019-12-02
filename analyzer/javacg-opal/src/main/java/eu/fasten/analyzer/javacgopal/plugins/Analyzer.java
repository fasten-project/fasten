package eu.fasten.analyzer.javacgopal.plugins;

import eu.fasten.core.plugins.FastenPlugin;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import picocli.CommandLine;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Currently, this is the Analyzer plugins. Its functionality is described:
 * 1- It gets Maven coordinates from the producer, which is the Python crawler.
 * 2- It generates a call graph for the given Maven coordinates. Note that the Kafka consumer and
 *  its properties are declared in this class.
 */
public class Analyzer implements FastenPlugin {

    private String serverAddress;
    private KafkaConsumer<String, String> MVCConsumer;
    private static final String topic = "maven.packages";
    private final String groupId = "some_app";
    private final Logger logger = LoggerFactory.getLogger(Analyzer.class.getName());

    public Analyzer(String serverAddress) {
        this.serverAddress = serverAddress;
    }

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

        ConsumerRunnable(CountDownLatch latch){
            mLatch = latch;
            MVCConsumer.subscribe(Collections.singletonList(topic));
        }

        @Override
        public void run(){
            try{

                do{
                    ConsumerRecords<String, String> records = MVCConsumer.poll(Duration.ofMillis(100));

//                    for(ConsumerRecord<String, String> r : records){
//                        System.out.println(r.key() + " " + r.value());
//
//                        JSONObject mvn_record = new JSONObject(r.value());
//                        System.out.println("GroupID: " + mvn_record.getString("groupId"));
//                        System.out.println("ArtifactID: " + mvn_record.getString("artifactId"));
//                        System.out.println("version: " + mvn_record.getString("version"));
//                        System.out.println("datetime: " + mvn_record.getString("date"));
//
//                    }

                }while (true);
            } catch (WakeupException e){
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
    public String name() {
        return "DummyAnalyzer";
    }

    @Override
    public String description() {
        return "DummyAnalyzer";
    }

    @Override
    public void start() {
        Properties props = consumerProps(this.serverAddress);
        this.MVCConsumer = new KafkaConsumer<String, String>(props);

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

    @Override
    public void stop() {

    }

}

