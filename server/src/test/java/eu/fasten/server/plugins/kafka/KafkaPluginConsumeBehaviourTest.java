package eu.fasten.server.plugins.kafka;

import eu.fasten.core.plugins.KafkaPlugin;

import java.lang.reflect.Field;
import java.util.*;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

public class KafkaPluginConsumeBehaviourTest {

    DummyPlugin dummyPlugin;

    @BeforeEach
    public void setUp() {
        dummyPlugin = mock(DummyPlugin.class);
    }

    public void setupMocks(FastenKafkaPlugin kafkaPlugin) throws IllegalAccessException {
        // Hacky way to override consumer and producer with a mock.
        KafkaConsumer<String, String> mockConsumer = mock(KafkaConsumer.class);
        FieldUtils.writeField(kafkaPlugin, "connection", mockConsumer, true);
        KafkaProducer<String, String> mockProducer = mock(KafkaProducer.class);
        FieldUtils.writeField(kafkaPlugin, "producer", mockProducer, true);

        // Another set of mocks.
        ConsumerRecords<String, String> records = mock(ConsumerRecords.class);
        ConsumerRecord<String, String> record = mock(ConsumerRecord.class);

        ArrayList<ConsumerRecord<String, String>> listOfRecords = new ArrayList<>();
        listOfRecords.add(record);

        when(mockConsumer.poll(any())).thenReturn(records);
        when(records.iterator()).thenReturn(listOfRecords.iterator());
        when(record.value()).thenReturn("{key: 'Im a record!'}");
    }

    @Test
    public void testNoLocalStorageNoTimeout() throws IllegalAccessException {
        FastenKafkaPlugin kafkaPlugin = spy(new FastenKafkaPlugin(false, new Properties(), new Properties(), dummyPlugin, 0, null, null, "", false, 0, false, false, ""));
        setupMocks(kafkaPlugin);

        kafkaPlugin.handleConsuming();

        verify(dummyPlugin).consume("{key: 'Im a record!'}");
    }

    @Test
    public void testNoLocalStorageTimeout() throws IllegalAccessException {
        FastenKafkaPlugin kafkaPlugin = spy(new FastenKafkaPlugin(false, new Properties(), new Properties(), dummyPlugin, 0, null, null, "", false, 0, false, false, ""));
        setupMocks(kafkaPlugin);

        kafkaPlugin.handleConsuming();

        verify(dummyPlugin).consume("{key: 'Im a record!'}");
    }

    @Test
    public void testAlreadyInLocalStorage() throws Exception {
        setEnv("POD_INSTANCE_ID", "test_pod");

        LocalStorage localStorage = new LocalStorage("src/test/resources");
        localStorage.clear(List.of(1));
        localStorage.store("{key: 'Im a record!'}", 0);

        FastenKafkaPlugin kafkaPlugin = spy(new FastenKafkaPlugin(false, new Properties(), new Properties(), dummyPlugin, 0, null, null, "", true, 5, false, true, "src/test/resources"));
        setupMocks(kafkaPlugin);

        kafkaPlugin.handleConsuming();

        verify(dummyPlugin).setPluginError(any());
        verify(dummyPlugin, never()).consume(any());
    }

    @Test
    public void testLocalStorage() throws Exception {
        setEnv("POD_INSTANCE_ID", "test_pod");

        LocalStorage localStorage = new LocalStorage("src/test/resources");
        localStorage.clear(List.of(0));

        FastenKafkaPlugin kafkaPlugin = spy(new FastenKafkaPlugin(false, new Properties(), new Properties(), dummyPlugin, 0, null, null, "", false, 5, false, true, "src/test/resources"));
        setupMocks(kafkaPlugin);

        kafkaPlugin.handleConsuming();
        verify(dummyPlugin).consume("{key: 'Im a record!'}");
    }

    @Test
    public void testLocalStorageTimeoutWithTimeout() throws Exception {
        setEnv("POD_INSTANCE_ID", "test_pod");

        LocalStorage localStorage = new LocalStorage("src/test/resources");
        localStorage.clear(List.of(1));

        FastenKafkaPlugin kafkaPlugin = spy(new FastenKafkaPlugin(false, new Properties(), new Properties(), dummyPlugin, 0, null, null, "", true, 5, false, true, "src/test/resources"));
        setupMocks(kafkaPlugin);

        kafkaPlugin.handleConsuming();
        verify(dummyPlugin).consume("{key: 'Im a record!'}");
    }

    public static void setEnv(String key, String value) {
        var map = new HashMap<String, String>();
        map.put(key, value);
        try {
            setEnvMap(map);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // https://stackoverflow.com/questions/318239/how-do-i-set-environment-variables-from-java
    private static void setEnvMap(Map<String, String> newenv) throws Exception {
        try {
            Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);
            Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
            env.putAll(newenv);
            Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            Map<String, String> cienv = (Map<String, String>)     theCaseInsensitiveEnvironmentField.get(null);
            cienv.putAll(newenv);
        } catch (NoSuchFieldException e) {
            Class[] classes = Collections.class.getDeclaredClasses();
            Map<String, String> env = System.getenv();
            for(Class cl : classes) {
                if("java.util.Collections$UnmodifiableMap".equals(cl.getName())) {
                    Field field = cl.getDeclaredField("m");
                    field.setAccessible(true);
                    Object obj = field.get(env);
                    Map<String, String> map = (Map<String, String>) obj;
                    map.clear();
                    map.putAll(newenv);
                }
            }
        }
    }

    class DummyPlugin implements KafkaPlugin {


        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.empty();
        }

        @Override
        public void addTopic(String topicNames) {
        }

        @Override
        public void consume(String record) {
        }

        @Override
        public Optional<String> produce() {
            return Optional.empty();
        }

        @Override
        public String getOutputPath() {
            return null;
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public String description() {
            return null;
        }

        @Override
        public String version() {
            return null;
        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public Exception getPluginError() {
            return null;
        }

        @Override
        public void setPluginError(Exception throwable) {

        }


        @Override
        public void freeResource() {

        }
    }
}
