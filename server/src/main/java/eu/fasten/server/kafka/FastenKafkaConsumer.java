package eu.fasten.server.kafka;

import eu.fasten.core.plugins.KafkaConsumer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastenKafkaConsumer extends FastenKafkaConnection {

    private final Logger logger = LoggerFactory.getLogger(FastenKafkaConsumer.class.getName());

    private final String serverLogTopic = "fasten.server.logs";

    private final KafkaProducer<String, String> serverLog;
    private final KafkaProducer<String, String> failedRecords;

    private org.apache.kafka.clients.consumer.KafkaConsumer<String, String> connection;
    private final String consumerHostName;
    private final int skipOffsets;
    private final KafkaConsumer<String> kafkaConsumer;
    private final CountDownLatch countDownLatch;

    /**
     * Constructs a FastenKafkaConsumer
     *
     * @param p           properties of a consumer
     * @param kc          Kafka consumer
     * @param skipOffsets skip offset number
     */
    public FastenKafkaConsumer(Properties p, KafkaConsumer<String> kc, int skipOffsets) {
        super(p);
        this.kafkaConsumer = kc;
        this.skipOffsets = skipOffsets;

        this.serverLog = new KafkaProducer<>(
                this.setKafkaProducer(p.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG),
                        kc.getClass().getSimpleName() + "_errors"));
        this.failedRecords = new KafkaProducer<>(
                this.setKafkaProducer(p.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG),
                        kc.getClass().getSimpleName() + "_CGS_status"));

        super.setName(kc.getClass().getSimpleName() + "_consumer"); // Consumer's thread name
        this.consumerHostName = this.getConsumerHostName();

        this.countDownLatch = new CountDownLatch(1);

        logger.debug("Thread: " + Thread.currentThread().getName()
                + " | Constructed a Kafka consumer for "
                + kc.getClass().getCanonicalName());

    }

    @Override
    public void run() {
        logger.debug("Starting consumer: {}", kafkaConsumer.getClass());

        try {
            if (this.connection == null) {
                this.connection =
                        new org.apache.kafka.clients.consumer.KafkaConsumer<>(this.connProperties);
                connection.subscribe(kafkaConsumer.consumerTopics());
            }

            logToKafka(this.serverLog, this.serverLogTopic, new Date()
                    + "| " + "Current Offset before running plug-in "
                    + kafkaConsumer.getClass().getCanonicalName());

            if (this.skipOffsets == 1) {
                skipPartitionOffsets();
            }

            List<String> topics = kafkaConsumer.consumerTopics();

            // Start consuming records until interrupted by an exception.
            consumerRecords(topics);

        } catch (WakeupException e) {
            logger.debug(this.getName() + " caught wakeup exception");

        } catch (RuntimeException re) {
            logToKafka(this.serverLog, kafkaConsumer.getClass().getSimpleName()
                    + "_errors", new Date() + "| " + "Exception for plug-in:"
                    + kafkaConsumer.getClass().getCanonicalName() + "\n"
                    + ExceptionUtils.getStackTrace(re));

        } finally {
            logger.info(this.getName() + " closing connection to Kafka...");
            connection.close();
            countDownLatch.countDown();
        }
    }

    /**
     * Sends a wake up signal to Kafka consumer and stops it.
     */
    public void shutdown() {
        connection.wakeup();
    }

    /**
     * Consumes records topics until interrupted by exception.
     *
     * @param topics Kafka topics to consumer records from
     */
    private void consumerRecords(List<String> topics) {
        NumberFormat timeFormatter = new DecimalFormat("#0.000");

        do {
            ConsumerRecords<String, String> records = connection.poll(Duration.ofMillis(100));

            logger.debug("Received {} records", records.count());

            for (String topic : topics) {
                for (ConsumerRecord<String, String> r : records.records(topic)) {
                    logToKafka(this.serverLog, this.serverLogTopic, new Date()
                            + "| [" + this.consumerHostName + "] T: " + r.topic() + " P: "
                            + r.partition() + " Of: " + r.offset() + " | Processing: " + r.key());

                    // This is "at most once" strategy which values progress over completeness.
                    doCommitSync();
                    long startTime = System.currentTimeMillis();
                    kafkaConsumer.consume(topic, r);
                    long endTime = System.currentTimeMillis();

                    if (kafkaConsumer.recordProcessSuccessful()) {
                        logToKafka(this.serverLog, this.serverLogTopic, new Date()
                                + "| [" + this.consumerHostName + "] Plug-in "
                                + kafkaConsumer.getClass().getSimpleName()
                                + " processed successfully record [in "
                                + timeFormatter.format((endTime - startTime) / 1000d)
                                + " sec.]: " + r.key());

                        logToKafka(this.failedRecords, String.format("fasten.%s.out",
                                kafkaConsumer.getClass().getSimpleName()),
                                getStdOutMsg(kafkaConsumer, r));

                    } else {
                        logToKafka(this.failedRecords, String.format("fasten.%s.err",
                                kafkaConsumer.getClass().getSimpleName()),
                                getStdErrMsg(kafkaConsumer, r));
                    }
                    kafkaConsumer.freeResource();
                }
            }
        } while (true);
    }

    /**
     * Send message to Kafka topic.
     *
     * @param producer Kafka producer
     * @param topic    topic to send to
     * @param msg      message
     */
    private void logToKafka(KafkaProducer<String, String> producer, String topic, String msg) {

        ProducerRecord<String, String> errorRecord = new ProducerRecord<>(topic, msg);

        producer.send(errorRecord, (recordMetadata, e) -> {
            if (recordMetadata != null) {
                logger.debug("Sent: {} to {}", msg, this.serverLogTopic);
            } else {
                e.printStackTrace();
            }
        });
        producer.flush();
    }

    /**
     * Create a message that will be send to STDOUT of a given plugin.
     *
     * @param consumer plugin
     * @param record   consumed record
     * @return stdout message
     */
    private String getStdOutMsg(KafkaConsumer<String> consumer,
                                ConsumerRecord<String, String> record) {
        JSONObject stdoutMsg = new JSONObject();
        stdoutMsg.put("created_at", System.currentTimeMillis() / 1000L);
        stdoutMsg.put("plugin_name", consumer.getClass().getSimpleName());
        stdoutMsg.put("plugin_version", consumer.version());

        stdoutMsg.put("input", new JSONObject(record.value()));

        if (consumer instanceof eu.fasten.core.plugins.KafkaProducer) {
            var producer = (eu.fasten.core.plugins.KafkaProducer) consumer;
            //stdoutMsg.put("payload", new JSONObject(producer.produce()));
        } else {
            stdoutMsg.put("payload", "");
        }
        return stdoutMsg.toString();
    }

    /**
     * Create a message that will be send to STDERR of a given plugin.
     *
     * @param consumer plugin
     * @param record   consumed record
     * @return stderr message
     */
    private String getStdErrMsg(KafkaConsumer<String> consumer,
                                ConsumerRecord<String, String> record) {
        JSONObject stderrMsg = new JSONObject();
        stderrMsg.put("created_at", System.currentTimeMillis() / 1000L);
        stderrMsg.put("plugin_name", consumer.getClass().getSimpleName());
        stderrMsg.put("plugin_version", consumer.version());

        stderrMsg.put("input", new JSONObject(record.value()));

        Throwable pluginError = consumer.getPluginError();
        JSONObject error = new JSONObject();
        error.put("error", pluginError.getClass().getSimpleName());
        error.put("msg", pluginError.getMessage());
        error.put("stacktrace", pluginError.getStackTrace());

        stderrMsg.put("err", error);

        return stderrMsg.toString();
    }

    /**
     * This is a synchronous commits and will block until either the commit succeeds
     * or an unrecoverable error is encountered.
     */
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
            logger.error("Commit failed", e);
        }
    }


    /**
     * Sets up a connection for producing error logs of a plug-in into a Kafka topic.
     *
     * @param serverAddress address of server
     * @param clientID      client id
     * @return properties for producer
     */
    private Properties setKafkaProducer(String serverAddress, String clientID) {

        Properties p = new Properties();
        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, serverAddress);
        p.setProperty(ProducerConfig.CLIENT_ID_CONFIG, clientID);
        p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());
        p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());

        return p;
    }


    /**
     * Get host name of this consumer.
     *
     * @return consumers host name
     */
    private String getConsumerHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            logger.error("Could not find the consumer's hostname.");
        }
        return "Unknown";
    }

    /**
     * This is a utility method to get current committed offset of all partitions for a consumer.
     *
     * @param consumer Kafka consumer
     * @param topics   list of topics
     */
    private void getOffsetForPartitions(org.apache.kafka.clients.consumer.KafkaConsumer<String, String> consumer,
                                        List<String> topics) {

        for (String t : topics) {
            for (PartitionInfo p : consumer.partitionsFor(t)) {
                logToKafka(this.serverLog, this.serverLogTopic,
                        "T: " + t + " P: " + p.partition() + " OfC: "
                                + consumer.committed(new TopicPartition(t, p.partition()))
                                .offset());
            }
        }
    }

    /**
     * This method adds one to the offset of all the partitions of a topic.
     * This is useful when you want to skip an offset with FATAL errors when
     * the FASTEN server is restarted.
     * Please note that this is NOT the most efficient way to restart FASTEN server
     * in the case of FATAL errors.
     */
    private void skipPartitionOffsets() {
        ArrayList<TopicPartition> topicPartitions = new ArrayList<>();
        // Note that this assumes that the consumer is subscribed to one topic only
        for (PartitionInfo p : this.connection
                .partitionsFor(this.kafkaConsumer.consumerTopics().get(0))) {

            topicPartitions.add(
                    new TopicPartition(this.kafkaConsumer.consumerTopics().get(0), p.partition()));
        }

        ConsumerRecords<String, String> records = dummyPoll(this.connection);

        if (records.count() != 0) {
            for (TopicPartition tp : topicPartitions) {
                logger.debug("Topic: {} | Current offset for partition {}: {}",
                        this.kafkaConsumer.consumerTopics().get(0),
                        tp, this.connection.position(tp));

                logToKafka(this.serverLog, this.serverLogTopic, "Topic: "
                        + this.kafkaConsumer.consumerTopics().get(0)
                        + "| Current offset for partition " + tp
                        + ": " + this.connection.position(tp));

                this.connection.seek(tp, this.connection.position(tp) + 1);

                logger.debug("Topic: {} | Offset for partition {} is set to {}",
                        this.kafkaConsumer.consumerTopics().get(0),
                        tp, this.connection.position(tp));
                logToKafka(this.serverLog, this.serverLogTopic, "Topic: "
                        + this.kafkaConsumer.consumerTopics().get(0)
                        + "| Offset for partition " + tp
                        + " is set to " + this.connection.position(tp));
            }
        }
    }

    /**
     * This is a dummy poll method for calling lazy methods such as seek.
     *
     * @param consumer Kafka consumer
     * @return consumed Kafka record
     */
    private ConsumerRecords<String, String> dummyPoll(org.apache.kafka.clients.consumer.KafkaConsumer<String, String> consumer) {
        ConsumerRecords<String, String> statusRecords;
        int i = 0;
        do {
            statusRecords = consumer.poll(Duration.ofMillis(100));
            i++;
        } while (i <= 5 && statusRecords.count() == 0);

        return statusRecords;
    }
}
