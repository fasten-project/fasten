import eu.fasten.analyzer.mergercacheprocessorplugin.MergerCacheProcessorPlugin;

import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.maven.GraphMavenResolver;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class MergerCacheProcessorPluginTest {

    private MergerCacheProcessorPlugin.MergerCacheProcessorExtension pluginExtension;

    @BeforeEach
    public void setUp() {
        pluginExtension = new MergerCacheProcessorPlugin.MergerCacheProcessorExtension();
        pluginExtension.setTopic("fasten.MergeCacheInvalidationPlugin.out");
    }

    @Test
    public void nullGraphResolverFailureTest() {
        pluginExtension.consume("json");
        assertNotNull(pluginExtension.getPluginError());
    }

    @Test
    public void consumeJsonErrorTest() {
        pluginExtension.loadGraphResolver(
                Mockito.mock(DSLContext.class),
                Mockito.mock(MetadataDao.class),
                Mockito.mock(RocksDao.class),
                Mockito.mock(GraphMavenResolver.class)
        );
        pluginExtension.consume("{\"input\":{\"foo\":\"bar\"}}");
        assertNotNull(pluginExtension.getPluginError());
    }

    @Test
    public void consumeTest() {
        var json ="{\"payload\": [" +
                    "\"id\": 123," +
                    "\"groupId\": \"org.wso2.carbon.apimgt.org\"," +
                    "\"artifactId\": \"wso2.carbon.apimgt.api\"," +
                    "\"version\": \"6.5.213\"," +
                    "\"createdAt\": \"2018-09-01 09:01:15\"" +
                "]}";
        pluginExtension.loadGraphResolver(
                Mockito.mock(DSLContext.class),
                Mockito.mock(MetadataDao.class),
                Mockito.mock(RocksDao.class),
                Mockito.mock(GraphMavenResolver.class)
        );
        pluginExtension.consume(json);
        assertNull(pluginExtension.getPluginError());
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Optional.of(Collections.singletonList("fasten.MergeCacheInvalidationPlugin.out"));
        assertEquals(topics, pluginExtension.consumeTopic());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Optional.of(Collections.singletonList("fasten.MergeCacheInvalidationPlugin.out"));
        assertEquals(topics1, pluginExtension.consumeTopic());
        var differentTopic = "DifferentKafkaTopic";
        var topics2 = Optional.of(Collections.singletonList(differentTopic));
        pluginExtension.setTopic(differentTopic);
        assertEquals(topics2, pluginExtension.consumeTopic());
    }

}
