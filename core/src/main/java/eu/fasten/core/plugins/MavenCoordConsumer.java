package eu.fasten.core.plugins;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;


import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * This plugin consumes Maven coordinates for creating call graphs.
 *
 * Please note that this is highly experimental and does not adhere to conventions of Kafkas interfaces
 * until the plugin's functionalities are discussed and reviewed.
 *
 */


public class MavenCoordConsumer implements FastenPlugin {

    final KafkaConsumer<String, String> MVCConsumer;
    private final Logger logger = LoggerFactory.getLogger(MavenCoordConsumer.class.getName());
    private static final String topic = "maven.packages";
    private final String groupId;

    @Override
    public String name() {
        return "MavenCoordConsumer";
    }

    @Override
    public String description() {
        return "This plugin is responsible for consuming Maven coordinates records. Currently, it passes the records" +
                " to the callgraph generator.";

    }

    MavenCoordConsumer(String serverProperties, String groupId){
        this.groupId = groupId;
        Properties props = consumerProps(serverProperties);
        this.MVCConsumer = new KafkaConsumer<String, String>(props);

        logger.info("Consumer initialized");
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

                    for(ConsumerRecord<String, String> record : records){

                        //TODO: Call graph generator should take Maven coordinates here.

                        logger.info("Key: " + record.key() + " , Value: " + record.value());
                        logger.info("Partition: " + record.partition() + ", Offset: " + record.offset());
                    }

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

    void run() throws InterruptedException {
        logger.debug("Creating consumer thread");

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

        latch.await();
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    public static void main(String[] args) throws InterruptedException {
        // TODO: Create a CLI to avoid hardcoding the serverAddress
        String serverAddress = "127.0.0.1:9092";
        new MavenCoordConsumer(serverAddress, "some_app").run();
    }
}
