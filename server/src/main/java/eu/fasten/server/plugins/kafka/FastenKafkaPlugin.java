package eu.fasten.server.plugins.kafka;

import com.google.common.base.Strings;
import eu.fasten.core.plugins.KafkaPlugin;
import eu.fasten.server.plugins.FastenServerPlugin;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import org.apache.commons.lang.StringUtils;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
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

public class FastenKafkaPlugin implements FastenServerPlugin {
    private final Logger logger = LoggerFactory.getLogger(FastenKafkaPlugin.class.getName());

    private Thread thread;

    private final KafkaPlugin<String, String> plugin;

    private final String serverLogTopic = "fasten.server.logs";

    private final KafkaConsumer<String, String> connection;
    private final KafkaProducer<String, String> serverLog;
    private final KafkaProducer<String, String> producer;

    private final String consumerHostName;
    private final int skipOffsets;

    /**
     * Constructs a FastenKafkaConsumer.
     *
     * @param p           properties of a consumer
     * @param plugin      Kafka plugin
     * @param skipOffsets skip offset number
     */
    public FastenKafkaPlugin(Properties p, KafkaPlugin<String, String> plugin, int skipOffsets) {
        this.plugin = plugin;

        this.connection = new KafkaConsumer<>(p);
        this.serverLog = new KafkaProducer<>(
                this.setKafkaProducer(p.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG),
                        plugin.getClass().getSimpleName() + "_server_logs"));
        this.producer = new KafkaProducer<>(
                this.setKafkaProducer(p.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG),
                        plugin.getClass().getSimpleName() + "_CGS_status"));

        this.consumerHostName = this.getConsumerHostName();
        this.skipOffsets = skipOffsets;

        logger.debug("Constructed a Kafka plugin for " + plugin.getClass().getCanonicalName());
    }

    @Override
    public void run() {
        try {
            if (plugin.consumeTopics().isPresent()) {
                connection.subscribe(plugin.consumeTopics().get());
            }
            if (this.skipOffsets == 1) {
                skipPartitionOffsets();
            }

            while (true) {
                if (plugin.consumeTopics().isPresent()) {
                    handleConsuming();
                } else {
                    doCommitSync();

                    handleProducing(null, null);
                }
            }
        } catch (WakeupException e) {
            logger.debug("Caught wakeup exception");
        } finally {
            connection.close();
        }
    }

    /**
     * Starts a thread.
     */
    public void start() {
        this.thread = new Thread(this);
        this.thread.setName(this.plugin.getClass().getSimpleName() + "_plugin");
        this.thread.start();
    }

    /**
     * Sends a wake up signal to Kafka consumer and stops it.
     */
    public void stop() {
        connection.wakeup();
    }

    /**
     * Getter for the thread.
     *
     * @return thread
     */
    public Thread thread() {
        return thread;
    }

    /**
     * Consumes a message from a Kafka topics and passes it to a plugin.
     */
    private void handleConsuming() {
        ConsumerRecords<String, String> records = connection.poll(Duration.ofSeconds(1));
        for (var r : records) {
            doCommitSync();

            long startTime = System.currentTimeMillis();
            plugin.consume(r.value());
            long endTime = System.currentTimeMillis();

            handleProducing(r.value(), String.valueOf(endTime - startTime));
        }
    }

    /**
     * Writes messages to server log and stdout/stderr topics.
     *
     * @param input input message [can be null]
     * @param time  precessing time [can be null]
     */
    private void handleProducing(String input, String time) {
        if (plugin.recordProcessSuccessful()) {
            var result = plugin.produce();

            emitMessage(this.serverLog, this.serverLogTopic,
                    "Timestamp: " + System.currentTimeMillis()
                    + " | [" + this.consumerHostName + "] Plug-in "
                    + plugin.getClass().getSimpleName()
                    + " processed successfully record"
                    + (StringUtils.isNotEmpty(time) ? " [in " + time + " ms]" : ""));

            emitMessage(this.producer, String.format("fasten.%s.out",
                    plugin.getClass().getSimpleName()),
                    getStdOutMsg(input, result.orElse(null)));
        } else {
            emitMessage(this.producer, String.format("fasten.%s.err",
                    plugin.getClass().getSimpleName()),
                    getStdErrMsg(input));
        }
    }

    /**
     * Send message to Kafka topic.
     *
     * @param producer Kafka producer
     * @param topic    topic to send to
     * @param msg      message
     */
    private void emitMessage(KafkaProducer<String, String> producer, String topic, String msg) {
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
     * @param input   consumed record
     * @param payload output of the plugin
     * @return stdout message
     */
    private String getStdOutMsg(String input, String payload) {
        JSONObject stdoutMsg = new JSONObject();
        stdoutMsg.put("created_at", System.currentTimeMillis());
        stdoutMsg.put("plugin_name", plugin.getClass().getSimpleName());
        stdoutMsg.put("plugin_version", plugin.version());

        stdoutMsg.put("input", StringUtils.isNotEmpty(input) ? new JSONObject(input) : "");
        stdoutMsg.put("payload", StringUtils.isNotEmpty(payload) ? new JSONObject(payload) : "");

        return stdoutMsg.toString();
    }

    /**
     * Create a message that will be send to STDERR of a given plugin.
     *
     * @param input consumed record
     * @return stderr message
     */
    private String getStdErrMsg(String input) {
        JSONObject stderrMsg = new JSONObject();
        stderrMsg.put("created_at", System.currentTimeMillis() / 1000L);
        stderrMsg.put("plugin_name", plugin.getClass().getSimpleName());
        stderrMsg.put("plugin_version", plugin.version());

        stderrMsg.put("input", !Strings.isNullOrEmpty(input) ? new JSONObject(input) : "");

        Throwable pluginError = plugin.getPluginError();
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
    private void getOffsetForPartitions(KafkaConsumer<String, String> consumer,
                                        List<String> topics) {

        for (String t : topics) {
            for (PartitionInfo p : consumer.partitionsFor(t)) {
                emitMessage(this.serverLog, this.serverLogTopic,
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
        List<String> topics = new ArrayList<>();
        this.plugin.consumeTopics().ifPresentOrElse(topics::addAll, () -> {
        });
        if (topics.isEmpty()) {
            return;
        }
        // Note that this assumes that the consumer is subscribed to one topic only
        for (PartitionInfo p : this.connection.partitionsFor(topics.get(0))) {
            topicPartitions.add(new TopicPartition(topics.get(0), p.partition()));
        }

        ConsumerRecords<String, String> records = dummyPoll(this.connection);

        if (records.count() != 0) {
            for (TopicPartition tp : topicPartitions) {
                logger.debug("Topic: {} | Current offset for partition {}: {}", topics.get(0),
                        tp, this.connection.position(tp));

                emitMessage(this.serverLog, this.serverLogTopic, "Topic: " + topics.get(0)
                        + "| Current offset for partition " + tp
                        + ": " + this.connection.position(tp));

                this.connection.seek(tp, this.connection.position(tp) + 1);

                logger.debug("Topic: {} | Offset for partition {} is set to {}",
                        topics.get(0),
                        tp, this.connection.position(tp));
                emitMessage(this.serverLog, this.serverLogTopic, "Topic: " + topics.get(0)
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
    private ConsumerRecords<String, String> dummyPoll(KafkaConsumer<String, String> consumer) {
        ConsumerRecords<String, String> statusRecords;
        int i = 0;
        do {
            statusRecords = consumer.poll(Duration.ofMillis(100));
            i++;
        } while (i <= 5 && statusRecords.count() == 0);

        return statusRecords;
    }
}
