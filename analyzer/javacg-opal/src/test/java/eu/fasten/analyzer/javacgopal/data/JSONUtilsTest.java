package eu.fasten.analyzer.javacgopal.data;

import eu.fasten.analyzer.javacgopal.data.exceptions.OPALException;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.JSONUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Objects;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
    void toJSONString() {

        logger.debug("Start serialization ...");
        var startMem = Runtime.getRuntime().freeMemory();
        long startTime = System.currentTimeMillis();
        final var ser1 = JSONUtils.toJSONString(graph);
        logger.debug("Direct String serialization time : {}",
            System.currentTimeMillis() - startTime);
        logger.debug("Direct String Serialization memory : {}",
            startMem - Runtime.getRuntime().freeMemory());

        startTime = System.currentTimeMillis();
        startMem = Runtime.getRuntime().freeMemory();
        final var ser2 = graph.toJSON().toString();
        logger.debug("Json object Serialization time : {}", System.currentTimeMillis() - startTime);
        logger.debug("Json object Serialization memory : {}",
            startMem - Runtime.getRuntime().freeMemory());

//        Assertions.assertEquals(ser1, ser2);

    }
}