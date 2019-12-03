package eu.fasten.server;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.Collections;
import java.util.Properties;

public class KafkaConsumerCon {

    private static String serverAddress;
    private KafkaConsumer<String, String> kafkaConsumer;
    private static String topic;
    private static String groupId;

    public KafkaConsumerCon(String serverAddress, String topic, String groupId) {
        this.serverAddress = serverAddress;
        this.topic = topic;
        this.groupId = groupId;
    }

    private Properties consumerProps() {
        String deserializer = StringDeserializer.class.getName();
        Properties properties = new Properties();

        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, this.serverAddress);
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, this.groupId);
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, deserializer);
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return properties;
    }

    public KafkaConsumer<String, String> getConsumer(){
        this.kafkaConsumer = new KafkaConsumer<String, String>(this.consumerProps());
        this.kafkaConsumer.subscribe(Collections.singletonList(this.topic));
        return kafkaConsumer;
    }


}
