package eu.fasten.analyzer.javacgopal.data;

import eu.fasten.analyzer.javacgopal.data.exceptions.OPALException;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.JSONUtils;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class JSONUtilsTest {
    private static final Logger logger = LoggerFactory.getLogger(JSONUtilsTest.class);

    private static ExtendedRevisionJavaCallGraph graph;
    @BeforeAll
    static void setUp() throws FileNotFoundException, OPALException {
        var coordinate = new MavenCoordinate("com.github.shoothzj", "java-tool", "3.0.30.RELEASE", "jar");
        graph = PartialCallGraph.createExtendedRevisionJavaCallGraph(coordinate,
            "", "CHA", 1574072773);
    }

    @Test
    void toJSONString() throws IOException {

        logger.debug("Start serialization ...");

        final var ser1 = AvgConsumption(true,20, 20);
        final var ser2 = AvgConsumption(false,20, 20);

        JSONAssert.assertEquals(ser1, ser2, JSONCompareMode.LENIENT);

    }

    private String AvgConsumption(boolean directSerializer, final int warmUp,
                                      final int iterations) {
        String result = "";
        final var times = new ArrayList<Long>();
        final var mems = new ArrayList<Long>();
        for (int i = 0; i < warmUp+iterations; i++) {
            if (i>warmUp) {
                var startMem = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
                long startTime = System.currentTimeMillis();
                if (directSerializer) {
                result = JSONUtils.toJSONString(graph);
                }else {
                    result = graph.toJSON().toString();
                }
                var endMem = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
                mems.add(endMem-startMem);
                times.add(System.currentTimeMillis() - startTime);
            }
        }
        logger.debug("Direct Serializer: " + directSerializer + " avg time : {}",
            times.stream().mapToDouble(a -> a).average().getAsDouble());
        logger.debug("Direct Serializer: " + directSerializer + " avg memory : {}",
            mems.stream().mapToDouble(a -> a).average().getAsDouble());
        return result;
    }
}