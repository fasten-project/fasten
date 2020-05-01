package eu.fasten.server.kafka;

import eu.fasten.core.plugins.KafkaPlugin;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastenKafkaPlugin implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(FastenKafkaConsumer.class.getName());

    private final Thread thread;

    private KafkaPlugin<String, String> plugin;

    private final String serverLogTopic = "fasten.server.logs";

    private final KafkaConsumer<String, String> connection;
    private final KafkaProducer<String, String> serverLog;
    private final KafkaProducer<String, String> producer;

    private final String consumerHostName;
    private final int skipOffsets;

    /**
     * Constructs a FastenKafkaConsumer
     *
     * @param p           properties of a consumer
     * @param plugin      Kafka plugin
     * @param skipOffsets skip offset number
     */
    public FastenKafkaPlugin(Properties p, KafkaPlugin<String, String> plugin, int skipOffsets) {
        this.thread = new Thread(this);
        //this.thread.setName(this.plugin.getClass().getSimpleName() + "_plugin");

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

        logger.debug("Thread: " + thread.getName()
                + " | Constructed a Kafka plugin for "
                + plugin.getClass().getCanonicalName());
    }

    @Override
    public void run() {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        NumberFormat timeFormatter = new DecimalFormat("#0.000");

        try {
            if (plugin.consumeTopics().isPresent()) {
                connection.subscribe(plugin.consumeTopics().get());
            }

            while (true) {
                if (plugin.consumeTopics().isPresent()) {
                    ConsumerRecords<String, String> records = connection.poll(Duration.ofSeconds(1));
                    for (var r : records) {
                        long startTime = System.currentTimeMillis();
                        plugin.consume(r.value());
                        var futureResult = exec.submit(plugin);
                        var result = futureResult.get();
                        long endTime = System.currentTimeMillis();

                        if (plugin.recordProcessSuccessful()) {
                            emitMessage(this.serverLog, this.serverLogTopic, new Date()
                                    + "| [" + this.consumerHostName + "] Plug-in "
                                    + plugin.getClass().getSimpleName()
                                    + " processed successfully record [in "
                                    + timeFormatter.format((endTime - startTime) / 1000d)
                                    + " sec.]: " + r.key());

                            result.ifPresent(s -> emitMessage(this.producer, String.format("fasten.%s.out",
                                    plugin.getClass().getSimpleName()),
                                    getStdOutMsg(r.value(), s)));
                        } else {
                            emitMessage(this.producer, String.format("fasten.%s.err",
                                    plugin.getClass().getSimpleName()),
                                    getStdErrMsg(r.value()));
                        }
                    }
                } else {
                    var futureResult = exec.submit(plugin);
                    var result = futureResult.get();
                    if (plugin.recordProcessSuccessful()) {
                        emitMessage(this.serverLog, this.serverLogTopic, new Date()
                                + "| [" + this.consumerHostName + "] Plug-in "
                                + plugin.getClass().getSimpleName()
                                + " processed successfully record");

                        result.ifPresent(s -> emitMessage(this.producer, String.format("fasten.%s.out",
                                plugin.getClass().getSimpleName()),
                                getStdOutMsg("", s)));
                    } else {
                        emitMessage(this.producer, String.format("fasten.%s.err",
                                plugin.getClass().getSimpleName()),
                                getStdErrMsg(""));
                    }
                }
            }
        } catch (WakeupException e) {
            logger.debug("Caught wakeup exception");
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            exec.shutdown();
            connection.close();
        }
    }


    public void start() {
        this.thread.start();
    }

    /**
     * Sends a wake up signal to Kafka consumer and stops it.
     */
    public void shutdown() {
        connection.wakeup();
    }

    public Thread getThread() {
        return thread;
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
        stdoutMsg.put("created_at", System.currentTimeMillis() / 1000L);
        stdoutMsg.put("plugin_name", plugin.getClass().getSimpleName());
        stdoutMsg.put("plugin_version", plugin.version());

        stdoutMsg.put("input", new JSONObject(input));
        stdoutMsg.put("payload", new JSONObject(payload));

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

        stderrMsg.put("input", new JSONObject(input));

        Throwable pluginError = plugin.getPluginError();
        JSONObject error = new JSONObject();
        error.put("error", pluginError.getClass().getSimpleName());
        error.put("msg", pluginError.getMessage());
        error.put("stacktrace", pluginError.getStackTrace());

        stderrMsg.put("err", error);

        return stderrMsg.toString();
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
}
