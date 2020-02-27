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

package eu.fasten.analyzer.javacgwala.data.callgraph;

import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.util.config.AnalysisScopeReader;
import eu.fasten.analyzer.javacgwala.data.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import java.io.File;
import java.io.FileNotFoundException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallGraphConstructor {

    private static Logger logger = LoggerFactory.getLogger(CallGraphConstructor.class);

    /**
     * Build a {@link PartialCallGraph} given classpath.
     *
     * @param coordinate Coordinate
     * @return Partial call graph
     */
    public static PartialCallGraph build(MavenCoordinate coordinate) throws FileNotFoundException {
        final NumberFormat timeFormatter = new DecimalFormat("#0.000");
        logger.info("Generating call graph for the Maven coordinate using WALA: {}",
                coordinate.getCoordinate());
        long startTime = System.currentTimeMillis();

        var rawGraph = generateCallGraph(MavenCoordinate.MavenResolver
                .downloadJar(coordinate.getCoordinate()).orElseThrow(RuntimeException::new)
                .getAbsolutePath());

        logger.info("Generated the call graph in {} seconds.",
                timeFormatter.format((System.currentTimeMillis() - startTime) / 1000d));
        return WalaResultAnalyzer.wrap(rawGraph);
    }

    /**
     * Create a call graph instance given a class path.
     *
     * @param classpath Path to class or jar file
     * @return Call Graph
     */
    public static CallGraph generateCallGraph(String classpath) {
        try {
            var classLoader = Thread.currentThread().getContextClassLoader();
            var exclusionFile = new File(Objects.requireNonNull(classLoader
                    .getResource("Java60RegressionExclusions.txt")).getFile());

            AnalysisScope scope = AnalysisScopeReader
                    .makeJavaBinaryAnalysisScope(classpath, exclusionFile);

            var cha = ClassHierarchyFactory.makeWithRoot(scope);

            EntryPointsGenerator entryPointsGenerator = new EntryPointsGenerator(cha);
            var entryPoints = entryPointsGenerator.getEntryPoints();
            var options = new AnalysisOptions(scope, entryPoints);
            var cache = new AnalysisCacheImpl();

            var builder = Util.makeZeroCFABuilder(Language.JAVA, options, cache, cha, scope);

            return builder.makeCallGraph(options, null);
        } catch (Exception e) {
            return null;
        }
    }
}
