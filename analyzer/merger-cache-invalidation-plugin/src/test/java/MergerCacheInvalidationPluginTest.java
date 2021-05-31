import eu.fasten.analyzer.mergercacheinvalidationplugin.MergerCacheInvalidationPlugin;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.data.Revision;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class MergerCacheInvalidationPluginTest {

    private MergerCacheInvalidationPlugin.MergerCacheInvalidationExtension pluginExtension;

    @BeforeEach
    public void setUp() {
        pluginExtension = new MergerCacheInvalidationPlugin.MergerCacheInvalidationExtension();
        pluginExtension.setTopic("fasten.GraphDBExtension.out");
    }

    @Test
    public void nullGraphResolverFailureTest() {
        pluginExtension.consume("json");
        assertNotNull(pluginExtension.getPluginError());
    }

    @Test
    public void consumeJsonErrorTest() {
        pluginExtension.loadGraphResolver(Mockito.mock(GraphMavenResolver.class));
        pluginExtension.consume("{\"payload\":{\"foo\":\"bar\"}}");
        assertNotNull(pluginExtension.getPluginError());
    }

    @Test
    public void consumeTest() {
        var json ="{\"input\": {" +
                    "\"product\": \"org.wso2.carbon.apimgt.org:wso2.carbon.apimgt.api\"," +
                    "\"version\": \"6.5.213\"," +
                    "\"nodes\": [317953184, 25223]," +
                    "\"edges\": [[317953063, 317952568], [317951606, 25223]]," +
                "}}";
        var mockResolver = Mockito.mock(GraphMavenResolver.class);
        var dependantsMock = new ObjectLinkedOpenHashSet<>(new Revision[]{new Revision("groupId", "artifactId", "version", Timestamp.valueOf("2018-09-01 09:01:15"))});
        Mockito
                .when(mockResolver.resolveDependents("org.wso2.carbon.apimgt.org", "wso2.carbon.apimgt.api", "6.5.213", -1, true))
                .thenReturn(dependantsMock);
        pluginExtension.loadGraphResolver(mockResolver);
        pluginExtension.consume(json);
        assertNull(pluginExtension.getPluginError());
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Optional.of(Collections.singletonList("fasten.GraphDBExtension.out"));
        assertEquals(topics, pluginExtension.consumeTopic());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Optional.of(Collections.singletonList("fasten.GraphDBExtension.out"));
        assertEquals(topics1, pluginExtension.consumeTopic());
        var differentTopic = "DifferentKafkaTopic";
        var topics2 = Optional.of(Collections.singletonList(differentTopic));
        pluginExtension.setTopic(differentTopic);
        assertEquals(topics2, pluginExtension.consumeTopic());
    }

}
