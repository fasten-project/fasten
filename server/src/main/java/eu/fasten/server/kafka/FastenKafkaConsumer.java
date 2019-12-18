package eu.fasten.server.kafka;

import eu.fasten.core.plugins.KafkaConsumer;
import eu.fasten.server.MavenCoordConsumer;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class FastenKafkaConsumer extends FastenKafkaConnection {

    private final Logger logger = LoggerFactory.getLogger(FastenKafkaConsumer.class.getName());
    private org.apache.kafka.clients.consumer.KafkaConsumer connection;
    private KafkaConsumer kafkaConsumer;
    private CountDownLatch mLatch;

    public FastenKafkaConsumer(Properties p, KafkaConsumer kc) {
        super(p);
        this.kafkaConsumer = kc;
        this.mLatch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.debug("Caught shutdown hook");
            try {
                mLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            logger.debug("{} has exited", kafkaConsumer.getClass().getCanonicalName());
        }));
    }

    @Override
    public void run() {
        logger.debug("Starting consumer: {}", kafkaConsumer.getClass());

        try {
            if(this.connection == null){
                this.connection = new org.apache.kafka.clients.consumer.KafkaConsumer(this.connProperties);
                connection.subscribe(kafkaConsumer.consumerTopics());
            }
            do {
                ConsumerRecords<String, String> records = connection.poll(Duration.ofMillis(100));
                List<String> topics = kafkaConsumer.consumerTopics();

                for (String topic : topics){
                    records.records(topic).forEach(r -> kafkaConsumer.consume(topic, r));
                    doCommitSync();
                }

            } while (true);
        } catch (WakeupException e) {
            logger.info("Received shutdown signal!");
        } finally {
            connection.close();
            mLatch.countDown();
        }
    }

    public void shutdown() {
        connection.wakeup();
    }

    private void doCommitSync() {
        try {
            connection.commitSync();
        } catch (WakeupException e) {
            // we're shutting down, but finish the commit first and then
            // rethrow the exception so that the main loop can exit
            doCommitSync();
            throw e;
        } catch (CommitFailedException e) {
            // the commit failed with an unrecoverable error. if there is any
            // internal state which depended on the commit, you can clean it
            // up here. otherwise it's reasonable to ignore the error and go on
            logger.debug("Commit failed", e);
        }
    }

}
