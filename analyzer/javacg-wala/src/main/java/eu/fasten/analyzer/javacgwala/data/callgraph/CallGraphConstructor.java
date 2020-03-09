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
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.util.config.AnalysisScopeReader;
import eu.fasten.analyzer.javacgwala.data.MavenCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Objects;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallGraphConstructor {

    private static Logger logger = LoggerFactory.getLogger(CallGraphConstructor.class);

    private static boolean setProperties = true;

    /**
     * Build a {@link PartialCallGraph} given classpath.
     *
     * @param coordinate Coordinate
     * @return Partial call graph
     */
    public static PartialCallGraph build(final MavenCoordinate coordinate)
            throws IOException, ClassHierarchyException, CallGraphBuilderCancelException {
        final NumberFormat timeFormatter = new DecimalFormat("#0.000");
        logger.info("Generating call graph for the Maven coordinate using WALA: {}",
                coordinate.getCoordinate());
        final long startTime = System.currentTimeMillis();

        final var rawGraph = generateCallGraph(MavenCoordinate.MavenResolver
                .downloadJar(coordinate.getCoordinate()).orElse(null)
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
    public static CallGraph generateCallGraph(final String classpath)
            throws IOException, ClassHierarchyException, CallGraphBuilderCancelException {
        if (setProperties) {
            setProperties();
        }
        final var classLoader = Thread.currentThread().getContextClassLoader();
        final var exclusionFile = new File(Objects.requireNonNull(classLoader
                .getResource("Java60RegressionExclusions.txt")).getFile());

        final var scope = AnalysisScopeReader
                .makeJavaBinaryAnalysisScope(classpath, exclusionFile);

        final var cha = ClassHierarchyFactory.makeWithRoot(scope);

        final var entryPointsGenerator = new EntryPointsGenerator(cha);
        final var entryPoints = entryPointsGenerator.getEntryPoints();
        final var options = new AnalysisOptions(scope, entryPoints);
        final var cache = new AnalysisCacheImpl();

        final var builder = Util.makeZeroCFABuilder(Language.JAVA, options, cache, cha, scope);

        return builder.makeCallGraph(options, null);
    }

    private static void setProperties() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        File file = new File(classLoader.getResource("wala.properties").getFile());

        try {
            PropertiesConfiguration conf = new PropertiesConfiguration("wala.properties");
            conf.setProperty("java_runtime_dir", file.getAbsolutePath().substring(0,
                    file.getAbsolutePath().lastIndexOf("/")) + "/jdk1.8.0_241.jdk/Contents/Home");
            conf.save();
            setProperties = false;
        } catch (ConfigurationException ex) {
            logger.error("Wrong configuration for Wala plugin");
        }
    }
}
