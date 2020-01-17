package eu.fasten.server.kafka;

import eu.fasten.core.plugins.KafkaConsumer;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class FastenKafkaConsumer extends FastenKafkaConnection {

    private final Logger logger = LoggerFactory.getLogger(FastenKafkaConsumer.class.getName());
    // This produces errors of a plug-in into a Kafka topic.
    private KafkaProducer errorLog;
    private final String errorLogTopic = "error_logs";

    private org.apache.kafka.clients.consumer.KafkaConsumer<String, String> connection;
    private KafkaConsumer<String> kafkaConsumer;
    private CountDownLatch mLatch;

    public FastenKafkaConsumer(Properties p, KafkaConsumer kc) {
        super(p);
        this.kafkaConsumer = kc;
        this.setErrorLogConn(p.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG), kc.getClass().getCanonicalName());

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

        logger.debug("Thread: " + Thread.currentThread().getName() + " | Constructed a Kafka consumer for " + kc.getClass().getCanonicalName());

    }

    /*
    This methods sets up a connection for producing error logs of a plug-in into a Kafka topic.
     */
    private void setErrorLogConn(String serverAddress, String clientID){

        Properties p = new Properties();
        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, serverAddress);
        p.setProperty(ProducerConfig.CLIENT_ID_CONFIG, clientID);
        p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        this.errorLog = new KafkaProducer(p);
    }

    private void sendErrorMsg(String msg){

        ProducerRecord<Object, String> errorRecord = new ProducerRecord<>(this.errorLogTopic, msg);

        this.errorLog.send(errorRecord, (recordMetadata, e) -> {
            if (recordMetadata != null) {
                logger.debug("Sent Error: {} to {}", msg, this.errorLogTopic);
            } else {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void run() {
        logger.debug("Starting consumer: {}", kafkaConsumer.getClass());

        try {
            if(this.connection == null){
                this.connection = new org.apache.kafka.clients.consumer.KafkaConsumer<>(this.connProperties);
                connection.subscribe(kafkaConsumer.consumerTopics());
            }
            do {
                ConsumerRecords<String, String> records = connection.poll(Duration.ofMillis(100));
                List<String> topics = kafkaConsumer.consumerTopics();

                //for(ConsumerRecord<String, String> r : records) System.out.println(r.key() + " " + r.value());

                for (String topic : topics){
                    //for(ConsumerRecord<String, String> r : records.records(topic)) System.out.println("K: " + r.key());

                    for(ConsumerRecord<String, String> r : records.records(topic)){
                        sendErrorMsg( new Date() + " | Offset: " + r.offset() + " | Processing: " + r.key());
                        kafkaConsumer.consume(topic, r);
                        doCommitSync();

                    }
                    //records.records(topic)     forEach(r -> kafkaConsumer.consume(topic, r));
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
            logger.debug("Committed the processed record...");
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
