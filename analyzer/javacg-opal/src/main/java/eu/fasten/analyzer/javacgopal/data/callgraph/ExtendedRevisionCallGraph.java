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

package eu.fasten.analyzer.javacgopal.data.callgraph;

import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.RevisionCallGraph;

import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.util.*;

import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtendedRevisionCallGraph extends RevisionCallGraph {

    private static Logger logger = LoggerFactory.getLogger(ExtendedRevisionCallGraph.class);

    private final Map<FastenURI, Type> classHierarchy;
    private final Graph graph;
    private final String cgGenerator;

    public Map<FastenURI, Type> getClassHierarchy() {
        return classHierarchy;
    }

    public Graph getGraph() {
        return graph;
    }

    public static class Graph{
        private final List<int[]> resolvedCalls;
        private final Map<Pair<Integer, FastenURI>, Map<String, Integer>> unresolvedCalls;

        public Graph(final List<int[]> resolvedCalls, final Map<Pair<Integer, FastenURI>, Map<String, Integer>> unresolvedCalls) {
            this.resolvedCalls = resolvedCalls;
            this.unresolvedCalls = unresolvedCalls;
        }

        public List<int[]> getResolvedCalls() {
            return resolvedCalls;
        }

        public Map<Pair<Integer, FastenURI>, Map<String, Integer>> getUnresolvedCalls() {
            return unresolvedCalls;
        }

        public int size() {
            return resolvedCalls.size()+unresolvedCalls.size();
        }
    }

    /**
     * Type can be a class or interface that inherits (implements) from others or implements methods.
     */
    public static class Type {
        //The source file name of this type.
        private final String sourceFileName;
        //Methods that this type implements
        private final Map<Integer ,FastenURI> methods;
        //Classes that this type inherits from in the order of instantiation.
        private final LinkedList<FastenURI> superClasses;
        //Interfaces that this type or its super classes implement.
        private final List<FastenURI> superInterfaces;

        public String getSourceFileName() {
            return sourceFileName;
        }

        public Map<Integer,FastenURI> getMethods() {
            return this.methods;
        }

        public LinkedList<FastenURI> getSuperClasses() {
            return superClasses;
        }

        public List<FastenURI> getSuperInterfaces() {
            return superInterfaces;
        }

        public Type(final String sourceFile, final Map<Integer,FastenURI> methods, final LinkedList<FastenURI> superClasses,
                    final List<FastenURI> superInterfaces) {
            this.sourceFileName = sourceFile;
            this.methods = methods;
            this.superClasses = superClasses;
            this.superInterfaces = superInterfaces;
        }
    }

    public ExtendedRevisionCallGraph(final String forge, final String product, final String version,
                                     final long timestamp, final List<List<Dependency>> depset,
                                     final Graph graph, final Map<FastenURI, Type> classHierarchy,final String cgGenerator) {

        super(forge, product, version, timestamp, depset, new ArrayList<>());
        this.classHierarchy = classHierarchy;
        this.graph = graph;
        this.cgGenerator = cgGenerator;
    }

    public ExtendedRevisionCallGraph(final JSONObject json, final boolean ignoreConstraints,final String cgGenerator) throws JSONException,
        URISyntaxException {
        super(json, ignoreConstraints);
        this.cgGenerator = cgGenerator;
        //TODO load ERCG from JSON
        this.graph = new Graph(new ArrayList<>(), new HashMap<>());
        this.classHierarchy = new HashMap<>();
    }

    public static ExtendedRevisionCallGraph createWithOPAL(final String forge, final MavenCoordinate coordinate,
                                                           final long timestamp) throws FileNotFoundException {

        logger.info("Generating call graph using Opal ...");
        final var partialCallGraph = new PartialCallGraph(
            MavenCoordinate.MavenResolver.downloadJar(coordinate.getCoordinate()).orElseThrow(RuntimeException::new)
        );
        logger.info("Call graph generation is done, creating extended revision call graph ...");
        return new ExtendedRevisionCallGraph(forge,
            coordinate.getProduct(),
            coordinate.getVersionConstraint(),
            timestamp,
            MavenCoordinate.MavenResolver.resolveDependencies(coordinate.getCoordinate()),
            new Graph(partialCallGraph.getResolvedCalls(),partialCallGraph.getUnresolvedCalls()),
            partialCallGraph.getClassHierarchy(),
            partialCallGraph.getGENERATOR());
    }

    public static ExtendedRevisionCallGraph createWithOPAL(final String forge, final MavenCoordinate coordinate,
                                                           final long timestamp, final PartialCallGraph partialCallGraph) {

        final var cha = partialCallGraph.getClassHierarchy();
        return new ExtendedRevisionCallGraph(forge,
            coordinate.getProduct(),
            coordinate.getVersionConstraint(),
            timestamp,
            MavenCoordinate.MavenResolver.resolveDependencies(coordinate.getCoordinate()),
            new Graph(partialCallGraph.getResolvedCalls(), partialCallGraph.getUnresolvedCalls()),
            cha,
            partialCallGraph.getGENERATOR());
    }

    /** Produces the JSON representation of this {@link ExtendedRevisionCallGraph}.
     *
     * @return the JSON representation.
     */
    @Override
    public JSONObject toJSON() {

        final var result = new JSONObject();
        result.put("forge", forge);
        result.put("product", product);
        result.put("version", version);
        if (timestamp >= 0) result.put("timestamp", timestamp);
        result.put("depset", Dependency.toJSON(depset));
        result.put("Generator", cgGenerator);
        final var graphJSON = new JSONObject();
        final var resolvedCallsArray = new JSONArray();
        for (final var entry : graph.resolvedCalls) {
            resolvedCallsArray.put(entry);
        }
        final var unresolvedCallsObject = new JSONObject();
        for (final var entry : graph.unresolvedCalls.entrySet()) {
            unresolvedCallsObject.put(Arrays.toString(new String[]{entry.getKey().getKey().toString(), entry.getKey().getValue().toString()}), new JSONObject(entry.getValue()));
        }

        graphJSON.put("resolvedCalls", resolvedCallsArray);
        graphJSON.put("unrisolvedCalls",unresolvedCallsObject);
        result.put("graph", graphJSON);

        final var chaJSON = new JSONObject();

        for (final var entry : this.getClassHierarchy().entrySet()) {
            final var type = entry.getValue();
            final var typeJSON = new JSONObject();

            typeJSON.put("methods", toListOfString(type.methods));
            typeJSON.put("superClasses", toListOfString(type.superClasses));
            typeJSON.put("superInterfaces", toListOfString(type.superInterfaces));

            chaJSON.put(entry.getKey().toString(), typeJSON);
        }

        result.put("cha", chaJSON);

        return result;
    }

    public static List<List<String>> toListOfString(final Map<Integer, FastenURI> map) {
        final List<List<String>> methods = new ArrayList<>();
        for (final var entry : map.entrySet()) {
            final List<String> method = new ArrayList<>();
            method.add(entry.getKey().toString());
            method.add(entry.getValue().toString());
            methods.add(method);
        }
        return methods;
    }

    public static List<String> toListOfString(final List<FastenURI> list) {
        final List<String> result = new ArrayList<>();
        for (final var fastenURI : list) {
            result.add(fastenURI.toString());
        }
        return result;
    }

    /**
     * Note that this is a temporary method for finding a Maven coordinate that generates an empty
     * call graph. Later on, this method might be helpful for not sending an empty call graph.
     *
     * @return boolean
     */
    public boolean isCallGraphEmpty() {
        return this.graph.resolvedCalls.isEmpty() && this.graph.unresolvedCalls.isEmpty();
    }

    public void sortResolvedCalls() {
//        this.graph.sort(Comparator.comparing(o -> (o[0] + o[1])));
        List<int[]> sortedList = new ArrayList<>(this.graph.resolvedCalls);
        Collections.sort(sortedList, (o1, o2) -> (Integer.toString(o1[0])+ o1[1]).compareTo(o2[0] +Integer.toString(o2[1])));
        this.graph.resolvedCalls.clear();
        this.graph.resolvedCalls.addAll(sortedList);
    }



}
