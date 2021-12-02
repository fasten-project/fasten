/*
 * Copyright 2021 Delft University of Technology
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fasten.analyzer.javacgopal.data;

import static eu.fasten.core.utils.TestUtils.getTestResource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jooq.tools.csv.CSVReader;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fasten.analyzer.javacgopal.ExtendedRevisionCallGraphConstructor;
import eu.fasten.core.data.ExtendedBuilderJava;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.JSONUtils;
import eu.fasten.core.data.callgraph.CGAlgorithm;
import eu.fasten.core.data.callgraph.CallPreservationStrategy;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.data.opal.exceptions.OPALException;
import eu.fasten.core.maven.utils.MavenUtilities;
import eu.fasten.core.merge.CGMerger;

class JSONUtilsTest {
    private static final Logger logger = LoggerFactory.getLogger(JSONUtilsTest.class);

    private static ExtendedRevisionJavaCallGraph graph, artifact, dependency;
    private static List<MavenCoordinate> coords;
    private int batchVolume = 20; //percentage of batch tests to be executed in the build

    @BeforeAll
    static void setUp() throws IOException, OPALException, MissingArtifactException {

        var coordinate =
            new MavenCoordinate("com.github.shoothzj", "java-tool", "3.0.30.RELEASE", "jar");
        var ercgConstructor = new ExtendedRevisionCallGraphConstructor();
		graph = ercgConstructor.create(coordinate,
            CGAlgorithm.CHA, 1574072773, MavenUtilities.MAVEN_CENTRAL_REPO, CallPreservationStrategy.ONLY_STATIC_CALLSITES);

        coordinate =
            new MavenCoordinate("abbot", "costello", "1.4.0", "jar");
        artifact = ercgConstructor.create(coordinate,
            CGAlgorithm.CHA, 1574072773, MavenUtilities.MAVEN_CENTRAL_REPO, CallPreservationStrategy.ONLY_STATIC_CALLSITES);

        coordinate =
            new MavenCoordinate("abbot", "abbot", "1.4.0", "jar");
        dependency = ercgConstructor.create(coordinate,
            CGAlgorithm.CHA, 1574072773, MavenUtilities.MAVEN_CENTRAL_REPO, CallPreservationStrategy.ONLY_STATIC_CALLSITES);
        final var deps = new ArrayList<>(Collections.singletonList(dependency));
        deps.add(artifact);
        final var merger = new CGMerger(deps);
        merger.mergeWithCHA(artifact);

        coords =
            readDataCSV(Objects.requireNonNull(getTestResource("121Coordinates.csv")));
    }

    @Test
    void toJSONString() throws IOException {

        final var ser1 = avgConsumption(graph, "direct", "direct", 20, 20);
        final var ser2 = avgConsumption(graph, "jsonObject", "jsonObject", 20, 20);
        JSONAssert.assertEquals(ser1, ser2, JSONCompareMode.STRICT);

    }

    @Test
    void shouldNotHaveCommaInTheEnd(){
        ExtendedBuilderJava builder = new ExtendedBuilderJava();
        final var rcg =
            builder.timestamp(-1).classHierarchy(graph.getClassHierarchy()).graph(graph.getGraph())
                .forge(graph.forge).cgGenerator(graph.getCgGenerator()).version(graph.version).product(graph.product).build();
        final var rcgString = JSONUtils.toJSONString(rcg);
        Assertions.assertTrue(rcgString.endsWith("]]}"));
        JSONAssert.assertEquals(rcg.toJSON().toString(), rcgString,
            JSONCompareMode.STRICT);
    }

    @Test
    void mergedGraphTest() throws IOException {

        final var ser1 = avgConsumption(artifact, "direct", "direct", 20, 20);
        final var ser2 = avgConsumption(artifact, "jsonObject", "jsonObject", 20, 20);
        JSONAssert.assertEquals(ser1, ser2, JSONCompareMode.STRICT);

    }

    @Test
    void batchOfCGsTest() throws IOException {
        final var coordsSize = (coords.size() * batchVolume)/100;

        logger.debug("Testing {} serialization", coordsSize);

        for (int i = 0; i < coordsSize; i++) {
            MavenCoordinate coord = coords.get(i);
            final var cg = new ExtendedRevisionCallGraphConstructor().create(coord,
                CGAlgorithm.CHA, 1574072773, MavenUtilities.getRepos().get(0), CallPreservationStrategy.ONLY_STATIC_CALLSITES);

            logger.debug("Serialization for: {}", coord.getCoordinate());
            final var ser1 = avgConsumption(cg, "direct", "direct", 20, 20);
            final var ser2 = avgConsumption(cg, "jsonObject", "jsonObject", 20, 20);
            // TODO delete these files after the test!

            JSONAssert.assertEquals(ser1, ser2, JSONCompareMode.STRICT);

            logger.debug("Deserialization for: {}", coord.getCoordinate());
            final var s1 = new ExtendedRevisionJavaCallGraph(new JSONObject(ser1));
            final var s2 = new ExtendedRevisionJavaCallGraph(new JSONObject(ser2));
            Assertions.assertEquals(s1,s2);
        }

    }

    private static List<MavenCoordinate> readDataCSV(
        final File input) throws IOException {

        List<MavenCoordinate> result = new ArrayList<>();

        try (var csvReader = new CSVReader(new FileReader(input), ',', '\'', 1)) {
            String[] values;
            while ((values = csvReader.readNext()) != null) {
                result.add(MavenCoordinate.fromString(values[0], "jar"));
            }
        }
        return result;
    }

    private String avgConsumption(final ExtendedRevisionJavaCallGraph ercg,
                                  final String serializationMethod, final String path,
                                  final int warmUp,
                                  final int iterations) throws IOException {
        String result = "";
        final var times = new ArrayList<Long>();
        final var mems = new ArrayList<Long>();
        for (int i = 0; i < warmUp + iterations; i++) {
            if (i > warmUp) {
                System.gc();
                var startMem = getUsedMem();
                long startTime = System.currentTimeMillis();
                if (serializationMethod.equals("direct")) {
                    result = JSONUtils.toJSONString(ercg);
                    writeToFile(path, result);
                } else {
                    result = ercg.toJSON().toString();
                    writeToFile(path, result);
                }
                var endMem = getUsedMem();
                mems.add(endMem - startMem);
                times.add(System.currentTimeMillis() - startTime);
            }
        }
        logger.debug(serializationMethod + " serializer avg time : {}",
            times.stream().mapToDouble(a -> a).average().getAsDouble());
        logger.debug(serializationMethod + " serializer avg memory : {}",
            mems.stream().mapToDouble(a -> a).average().getAsDouble());
        return result;
    }

    private long getUsedMem() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    private static void writeToFile(final String path, final String content) throws IOException {

        File file = new File(path);
        FileWriter fw = new FileWriter(file.getAbsoluteFile());
        fw.write(content);
        fw.flush();
        fw.close();
    }

}