package eu.fasten.server.kafka;

import eu.fasten.core.plugins.KafkaConsumer;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class FastenKafkaConsumer extends FastenKafkaConnection {

    private final Logger logger = LoggerFactory.getLogger(FastenKafkaConsumer.class.getName());

    private final String errorLogTopic = "error_logs";
    private final String cgsStatusTopic = "CGS_status";

    // This produces errors of a plug-in into a Kafka topic.
    private KafkaProducer errorLog;
    private KafkaProducer cgsStatus;
    private org.apache.kafka.clients.consumer.KafkaConsumer<String, String> cgsStatusConsumer;

    // Constants for CGS_status topic
    private final String OK_STATUS = "OK";
    private final String FAIL_STATUS = "FAIL";

    private org.apache.kafka.clients.consumer.KafkaConsumer<String, String> connection;
    private int skipOffsets;
    private KafkaConsumer<String> kafkaConsumer;
    private CountDownLatch mLatch;

    public FastenKafkaConsumer(Properties p, KafkaConsumer kc, int skipOffsets) {
        super(p);
        this.kafkaConsumer = kc;
        this.skipOffsets = skipOffsets;

        this.errorLog = new KafkaProducer<>(this.setKafkaProducer(p.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG),
                kc.getClass().getSimpleName() + "_errors"));
        this.cgsStatus = new KafkaProducer<>(this.setKafkaProducer(p.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG),
                kc.getClass().getSimpleName() + "_CGS_status"));
        this.setCGSStatusConn(p.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG), kc.getClass().getCanonicalName());

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
    private Properties setKafkaProducer(String serverAddress, String clientID){

        Properties p = new Properties();
        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, serverAddress);
        p.setProperty(ProducerConfig.CLIENT_ID_CONFIG, clientID);
        p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        return p;
    }

    private void setCGSStatusConn(String serverAddress, String clientID){

        Properties p = new Properties();
        p.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, serverAddress);
        p.setProperty(ConsumerConfig.GROUP_ID_CONFIG, clientID);
        p.setProperty(ConsumerConfig.CLIENT_ID_CONFIG, clientID + "_CGS_status");
        p.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        p.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1");

        this.cgsStatusConsumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(p);
        this.cgsStatusConsumer.subscribe(Collections.singletonList(this.cgsStatusTopic));

    }

    private void sendRecord(KafkaProducer producer, String topic, String msg){

        ProducerRecord<Object, String> errorRecord = new ProducerRecord<>(topic, msg);

        producer.send(errorRecord, (recordMetadata, e) -> {
            if (recordMetadata != null) {
                logger.debug("Sent: {} to {}", msg, this.errorLogTopic);
            } else {
                e.printStackTrace();
            }
        });
    }

    /**
     * This is a utility method to get current committed offset of all partitions for a consumer
     */
    private void getOffsetForPartitions(org.apache.kafka.clients.consumer.KafkaConsumer<String, String> consumer, List<String> topics){

        for(String t: topics){
            for(PartitionInfo p: consumer.partitionsFor(t)){
                sendRecord(this.errorLog, this.errorLogTopic, "T: " + t + " P: " + p.partition() + " OfC: " +
                        consumer.committed(new TopicPartition(t, p.partition())).offset());
            }
        }
    }

    private String generateRecordStatus(String pluginName, ConsumerRecord<String, String> record, String status){

//        return new JSONObject().put("plugin", pluginName).put("topic", record.topic())
//                .put("partition", String.valueOf(record.partition())).put("offset", String.valueOf(record.offset())).put("record",
//                        record.value()).put("status", status).toString();

        return new JSONObject(record.value()).put("plugin", pluginName).put("status", status).toString();

    }

    /**
     * This is a dummy poll method for calling lazy methods such as seek.
     */
    private ConsumerRecords<String, String> dummyPoll(org.apache.kafka.clients.consumer.KafkaConsumer<String, String> consumer){
        ConsumerRecords<String, String> statusRecords;
        int i = 0;
        do{
            statusRecords = consumer.poll(Duration.ofMillis(100));
            i++;
        } while (i <= 5 && statusRecords.count() == 0);

        return statusRecords;
    }

    /**
     * It checks whether the last record was processed successfully or not.
     * if the processing of the last record failed, the method changes current offset to skip the failed record.
     */
    private void checkFailStatus(){

        ArrayList<TopicPartition> topicPartitions = new ArrayList<>();
        for(PartitionInfo p: this.cgsStatusConsumer.partitionsFor(this.cgsStatusTopic)){
            topicPartitions.add(new TopicPartition(this.cgsStatusTopic, p.partition()));
        }

        ConsumerRecords<String, String> statusRecords = dummyPoll(this.cgsStatusConsumer);
//        ConsumerRecords<String, String> statusRecords;
//        int i = 0;
//        do{
//            statusRecords = this.cgsStatusConsumer.poll(Duration.ofMillis(100));
//            i++;
//        } while (i <= 5 && statusRecords.count() == 0);
        // After five times try, either we continue to check or assume that CGS_status topic is empty and return.
        if(statusRecords.count() == 0) return;

        this.cgsStatusConsumer.seekToEnd(topicPartitions);

        //System.out.println("Offset before seeking to end: " + this.cgsStatusConsumer.position(topicPartitions.get(0)));

        // Sets offset to correct position to read the last record
        topicPartitions.forEach(tp -> this.cgsStatusConsumer.seek(tp,  this.cgsStatusConsumer.position(tp) - 1));
        //this.cgsStatusConsumer.seek(topicPartitions.get(0),  this.cgsStatusConsumer.position(topicPartitions.get(0)) - 1);

        //System.out.println("Offset set to: " + this.cgsStatusConsumer.position(topicPartitions.get(0)));

        statusRecords = this.cgsStatusConsumer.poll(Duration.ofMillis(100));

        if(statusRecords.count() != 0){
            String record =  statusRecords.iterator().next().value();
            String[] splitRecord = record.split("\\|");
            JSONObject lastStatusRecord = new JSONObject(splitRecord[1]);

            //System.out.println("Checking " + splitRecord[1]);
            logger.info("Checking the status of record: " + splitRecord[1]);

            this.cgsStatusConsumer.close();

            if(!lastStatusRecord.get("status").toString().equals(this.OK_STATUS)){
                logger.info("Increasing offset for skipping a record");
                final long failedPartition = Long.parseLong(lastStatusRecord.get("partition").toString());
                final long failedOffset = Long.parseLong(lastStatusRecord.get("offset").toString());

                ConsumerRecords<String, String> failedRecord;
                do{
                    // A dummy call to poll to make seek method work
                    failedRecord = this.connection.poll(Duration.ofMillis(1000));
                    connection.commitSync();
                    System.out.println("Fetched " + failedRecord.count());
                }while (failedRecord.count() == 0);

                System.out.println("Current offset for plug-in" + this.connection.position(new TopicPartition(lastStatusRecord.get("topic").toString(),
                        (int)failedPartition)));
                this.connection.seek(new TopicPartition(lastStatusRecord.get("topic").toString(), (int)failedPartition),
                        failedOffset + 1);
            }
        }
    }

    /**
     * This method adds one to the offset of all the partitions of a topic.
     * This is useful when you want to skip an offset with FATAL errors when the FASTEN server is restarted
     * Please note that this is NOT the most efficient way to restart FASTEN server in the case of FATAL errors.
     */
    private void skipPartitionOffsets(){
        ArrayList<TopicPartition> topicPartitions = new ArrayList<>();
        // Note that this assumes that the consumer is subscribed to one topic only
        for(PartitionInfo p: this.connection.partitionsFor(this.kafkaConsumer.consumerTopics().get(0))){
            topicPartitions.add(new TopicPartition(this.kafkaConsumer.consumerTopics().get(0), p.partition()));
        }

        ConsumerRecords<String, String> records = dummyPoll(this.connection);

        if(records.count() != 0){
            for(TopicPartition tp : topicPartitions){
                logger.info("Topic: {} | Current offset for partition {}: {}", this.kafkaConsumer.consumerTopics().get(0)
                        , tp, this.connection.position(tp));
                sendRecord(this.errorLog, this.errorLogTopic, "Topic: " + this.kafkaConsumer.consumerTopics().get(0) +
                        "| Current offset for partition " + tp + ": " + this.connection.position(tp));

                this.connection.seek(tp,  this.connection.position(tp) + 1);

                logger.info("Topic: {} | Offset for partition {} is set to {}", this.kafkaConsumer.consumerTopics().get(0)
                        , tp, this.connection.position(tp));
                sendRecord(this.errorLog, this.errorLogTopic, "Topic: " + this.kafkaConsumer.consumerTopics().get(0) +
                        "| Offset for partition " + tp + " is set to " + this.connection.position(tp));

            }
        }

    }

    @Override
    public void run() {

        NumberFormat timeFormatter = new DecimalFormat("#0.000");

        logger.debug("Starting consumer: {}", kafkaConsumer.getClass());

        try {
            if (this.connection == null) {
                this.connection = new org.apache.kafka.clients.consumer.KafkaConsumer<>(this.connProperties);
                connection.subscribe(kafkaConsumer.consumerTopics());
            }

            sendRecord(this.errorLog, this.errorLogTopic,new Date() + "| " + "Current Offset before running plug-in "
                    + kafkaConsumer.getClass().getCanonicalName());
            getOffsetForPartitions(this.connection, kafkaConsumer.consumerTopics());

            //checkFailStatus();
            if(this.skipOffsets == 1){
                skipPartitionOffsets();
            }

            List<String> topics = kafkaConsumer.consumerTopics();

            do {
                ConsumerRecords<String, String> records = connection.poll(Duration.ofMillis(100));

                sendRecord(this.errorLog, this.errorLogTopic, new Date() + "| " + "Received " + records.count() + " records");

                for (String topic : topics) {
                    for (ConsumerRecord<String, String> r : records.records(topic)) {
                        sendRecord(this.errorLog, this.errorLogTopic,new Date() + "| " + "T: " + r.topic() + " P: "
                                + r.partition() + " Of: " + r.offset() + " | Processing: " + r.key());

                        // Note that this is "at most once" strategy which values progress over completeness.
                        doCommitSync();
                        long startTime = System.currentTimeMillis();
                        kafkaConsumer.consume(topic, r);

                        if(kafkaConsumer.recordProcessSuccessful()){
                            sendRecord(this.errorLog, this.errorLogTopic, new Date() + "| " + "Plug-in " + kafkaConsumer.getClass().getSimpleName() +
                                    " processed successfully record [in " + timeFormatter.format((System.currentTimeMillis() - startTime) / 1000d) + " sec.]: " + r.value());
                        } else {
                            sendRecord(this.cgsStatus, this.cgsStatusTopic, generateRecordStatus(kafkaConsumer.getClass().getSimpleName(),
                                    r, this.kafkaConsumer.getPluginError()));
                        }
                    }
                }
            } while (true);
        } catch (RuntimeException re) {
            sendRecord(this.errorLog, kafkaConsumer.getClass().getSimpleName() + "_errors", new Date() +
                    "| " + "Exception for plug-in:" +
                    kafkaConsumer.getClass().getCanonicalName() + "\n" + ExceptionUtils.getStackTrace(re));
//        } catch (WakeupException e) {
//            logger.info("Received shutdown signal!");
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
            //throw e;
        } catch (CommitFailedException e) {
            // the commit failed with an unrecoverable error. if there is any
            // internal state which depended on the commit, you can clean it
            // up here. otherwise it's reasonable to ignore the error and go on
            logger.debug("Commit failed", e);
        }
    }

}
