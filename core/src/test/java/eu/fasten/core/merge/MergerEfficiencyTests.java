/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.core.merge;

import it.unimi.dsi.fastutil.longs.LongLongPair;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;

public class MergerEfficiencyTests {

    private static List<ExtendedRevisionJavaCallGraph> depSet;

    @BeforeAll
    static void setUp() throws IOException, URISyntaxException {
        Path inputPath = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().
                getResource("merge/efficiencyTests/jpacman-framework-6f703ad")).toURI().getPath()).toPath();
        depSet = Files.list(inputPath).
                filter(path -> path.toString().endsWith(".json")).
                map(path -> {
                        ExtendedRevisionJavaCallGraph rcg = null;
                    try {
                        rcg = new ExtendedRevisionJavaCallGraph(new JSONObject(Files.readString(path)));
                        System.out.println("Read " + path + " (" + rcg.getNodeCount() + " nodes).");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return rcg;
                }).collect(Collectors.toList());
    }

    @Test
    public void localMergerEfficiencyTest() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        long timeBefore = threadMXBean.getCurrentThreadCpuTime();
        var merger = new CGMerger(depSet);
        var result = merger.mergeAllDeps();
        long timeAfter = threadMXBean.getCurrentThreadCpuTime();

        double secondsTaken = (timeAfter - timeBefore) / 1e9;
        DecimalFormat df = new DecimalFormat("###.###");
        int numNodes = result.numNodes();
        long numEdges = result.numArcs();

        System.out.println("CPU time used for merging: " + df.format(secondsTaken) + " seconds." +
                " Merged graph has " + numNodes + " nodes and " + numEdges + " edges.");

        Assertions.assertTrue(
                secondsTaken < 25, "CPU time used for merging should be less than 25 seconds, but was " + secondsTaken);
        Assertions.assertEquals(50513, numNodes);
        Assertions.assertEquals(764288, numEdges);
    }

    @Test
    public void localMergerRepeatedEfficiencyTests() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        for(int k = 10; k -- != 0;) {
            long timeBefore = threadMXBean.getCurrentThreadCpuTime();
            var merger = new CGMerger(depSet);
            var result = merger.mergeAllDeps();
            long timeAfter = threadMXBean.getCurrentThreadCpuTime();

            double secondsTaken = (timeAfter - timeBefore) / 1e9;
            DecimalFormat df = new DecimalFormat("###.###");
            int numNodes = result.numNodes();
            long numEdges = result.numArcs();
            System.out.println("CPU time used for merging: " + df.format(secondsTaken) + " seconds." +
                    " Merged graph has " + numNodes + " nodes and " + numEdges + " edges.");
        }
    }
}
