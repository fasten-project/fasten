package eu.fasten.server.plugins.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.fasten.core.plugins.KafkaPlugin;
import eu.fasten.core.plugins.KafkaPlugin.ProcessingLane;

public class KafkaPluginConsumeBehaviourTest {

	DummyPlugin dummyPlugin;

    @BeforeEach
    public void setUp() {
        dummyPlugin = mock(DummyPlugin.class);
    }

    @SuppressWarnings("unchecked")
    public void setupMocks(FastenKafkaPlugin kafkaPlugin) throws IllegalAccessException {
        // Hacky way to override consumer and producer with a mock.
        KafkaConsumer<String, String> mockConsumer = mock(KafkaConsumer.class);
        FieldUtils.writeField(kafkaPlugin, "connNorm", mockConsumer, true);
        FieldUtils.writeField(kafkaPlugin, "connPrio", mockConsumer, true);
        KafkaProducer<String, String> mockProducer = mock(KafkaProducer.class);
        FieldUtils.writeField(kafkaPlugin, "producer", mockProducer, true);
        List<String> mockTopics = mock(List.class);
        FieldUtils.writeField(kafkaPlugin, "prioTopics", mockTopics, true);
        FieldUtils.writeField(kafkaPlugin, "normTopics", mockTopics, true);

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
        FastenKafkaPlugin kafkaPlugin = spy(new FastenKafkaPlugin(false, new Properties(), new Properties(), new Properties(), dummyPlugin, 0, null, null, "", false, 0, false, false, ""));
        setupMocks(kafkaPlugin);

        kafkaPlugin.handleConsuming();

        verify(dummyPlugin).consume("{key: 'Im a record!'}", ProcessingLane.PRIORITY);
    }

    @Test
    public void testNoLocalStorageTimeout() throws IllegalAccessException {
        FastenKafkaPlugin kafkaPlugin = spy(new FastenKafkaPlugin(false, new Properties(), new Properties(), new Properties(), dummyPlugin, 0, null, null, "", false, 0, false, false, ""));
        setupMocks(kafkaPlugin);

        kafkaPlugin.handleConsuming();

        verify(dummyPlugin).consume("{key: 'Im a record!'}", ProcessingLane.PRIORITY);
    }

    @Test
    public void testAlreadyInLocalStorage(@TempDir File tempDir) throws Exception {
        setEnv("POD_INSTANCE_ID", "test_pod");

        LocalStorage localStorage = new LocalStorage(tempDir.getAbsolutePath());
        localStorage.clear(List.of(1));
        localStorage.store("{key: 'Im a record!'}", 0);

        FastenKafkaPlugin kafkaPlugin = spy(new FastenKafkaPlugin(false, new Properties(), new Properties(), new Properties(), dummyPlugin, 0, null, null, "", true, 5, false, true, tempDir.getAbsolutePath()));
        setupMocks(kafkaPlugin);

        kafkaPlugin.handleConsuming();

        verify(dummyPlugin).setPluginError(any());
        verify(dummyPlugin, never()).consume(any());
        verify(dummyPlugin, never()).consume(any(), any(ProcessingLane.class));
    }

    @Test
    public void testLocalStorage(@TempDir File tempDir) throws Exception {
        setEnv("POD_INSTANCE_ID", "test_pod");

        LocalStorage localStorage = new LocalStorage(tempDir.getAbsolutePath());
        localStorage.clear(List.of(0));

        FastenKafkaPlugin kafkaPlugin = spy(new FastenKafkaPlugin(false, new Properties(), new Properties(), new Properties(), dummyPlugin, 0, null, null, "", false, 5, false, true, tempDir.getAbsolutePath()));
        setupMocks(kafkaPlugin);

        kafkaPlugin.handleConsuming();
        verify(dummyPlugin).consume("{key: 'Im a record!'}", ProcessingLane.PRIORITY);
    }

    @Test
    public void testLocalStorageTimeoutWithTimeout(@TempDir File tempDir) throws Exception {
        setEnv("POD_INSTANCE_ID", "test_pod");

        LocalStorage localStorage = new LocalStorage(tempDir.getAbsolutePath());
        localStorage.clear(List.of(1));

        FastenKafkaPlugin kafkaPlugin = spy(new FastenKafkaPlugin(false, new Properties(), new Properties(), new Properties(), dummyPlugin, 0, null, null, "", true, 5, false, true, tempDir.getAbsolutePath()));
        setupMocks(kafkaPlugin);

        kafkaPlugin.handleConsuming();
        verify(dummyPlugin).consume("{key: 'Im a record!'}", ProcessingLane.PRIORITY);
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
    @SuppressWarnings(value = {"unchecked", "rawtypes"})
    private static void setEnvMap(Map<String, String> newenv) throws Exception {
        try {
            Class<?> processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment");
            Field theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment");
            theEnvironmentField.setAccessible(true);
            Map<String, String> env = (Map<String, String>) theEnvironmentField.get(null);
            env.putAll(newenv);
            Field theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment");
            theCaseInsensitiveEnvironmentField.setAccessible(true);
            Map<String, String> cienv = (Map<String, String>) theCaseInsensitiveEnvironmentField.get(null);
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
        public void setTopics(List<String> consumeTopics) {

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