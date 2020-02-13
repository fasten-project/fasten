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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtendedRevisionCallGraph extends RevisionCallGraph {

    private static Logger logger = LoggerFactory.getLogger(ExtendedRevisionCallGraph.class);
    private Map<FastenURI, Type> classHierarchy;
    private Graph graph;

    public Map<FastenURI, Type> getClassHierarchy() {
        return classHierarchy;
    }

    public Graph getGraph() {
        return graph;
    }

    /**
     * Removes the content of the revision call graph.
     *
     * @param excessiveRemove if it is true method will go through all the content
     *                        of the revision call graph and remove them one by one.
     *                        if false general references will be null.
     */
    public void clear(final Boolean excessiveRemove) {
        if (excessiveRemove) {

            this.classHierarchy.forEach((fastenURI, type) -> {
                fastenURI = null;
                type.methods.forEach((key, uri) ->{ key = null; uri = null;});
                type.superClasses.parallelStream().forEach(i -> i = null);
                type.superInterfaces.parallelStream().forEach(i -> i = null);
            });
            this.classHierarchy.clear();
        } else {
            this.graph = null;
            this.classHierarchy = null;
        }
    }

    public void clear() {
        clear(false);
    }


    /**
     * Type can be a class or interface that inherits (implements) from others or implements methods.
     */
    public static class Type {
        //The source file name of this type.
        final private String sourceFileName;
        //Methods that this type implements
        final private Map<Integer ,FastenURI> methods;
        //Classes that this type inherits from in the order of instantiation.
        final private LinkedList<FastenURI> superClasses;
        //Interfaces that this type or its super classes implement.
        final private List<FastenURI> superInterfaces;

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

        public Type(String sourceFile, Map<Integer,FastenURI> methods, LinkedList<FastenURI> superClasses,
                    List<FastenURI> superInterfaces) {
            this.sourceFileName = sourceFile;
            this.methods = methods;
            this.superClasses = superClasses;
            this.superInterfaces = superInterfaces;
        }
    }

    public ExtendedRevisionCallGraph(String forge, String product, String version, long timestamp,
                                     List<List<Dependency>> depset, Graph graph,
                                     Map<FastenURI, Type> classHierarchy) {
        super(forge, product, version, timestamp, depset, null);
        this.classHierarchy = classHierarchy;
        this.graph = graph;
    }

    public ExtendedRevisionCallGraph(final JSONObject json, final boolean ignoreConstraints) throws JSONException,
        URISyntaxException {
        super(json, ignoreConstraints);
    }

    /**
     * It overrides the toJSON method of the RevisionCallGraph class in order to add ClassHierarchy to it.
     *
     * @return org.json.JSONObject of this type including the classHierarchy.
     */
    @Override
    public JSONObject toJSON() {

        final JSONObject result = new JSONObject();
        result.put("forge", forge);
        result.put("product", product);
        result.put("version", version);
        if (timestamp >= 0) result.put("timestamp", timestamp);
        result.put("depset", Dependency.toJSON(depset));
        final JSONArray graphJSONArray = new JSONArray();
        for (final int[] arc: graph.resolvedCalls) graphJSONArray.put(new JSONArray(new int[] {arc[0], arc[1]}));
        graph.unresolvedCalls.forEach((sourceKey, targetURI) -> {
            graphJSONArray.put(new JSONArray(new String[] {sourceKey.toString(), targetURI.toString()}));
        });
        result.put("graph", graphJSONArray);

        final JSONObject chaJSON = new JSONObject();

        this.getClassHierarchy().forEach((clas, type) -> {

            final JSONObject typeJSON = new JSONObject();

            typeJSON.put("methods", toListOfString(type.methods));
            typeJSON.put("superClasses", toListOfString(type.superClasses));
            typeJSON.put("superInterfaces", toListOfString(type.superInterfaces));

            chaJSON.put(clas.toString(), typeJSON);
        });

        result.put("cha", chaJSON);

        return result;
    }

    public static List<List<String>> toListOfString(final Map<Integer, FastenURI> map) {
        final List<List<String>> methods = new ArrayList<>();
        map.forEach((integer, fastenURI) -> {
            final List<String> method = new ArrayList<>();
            method.add(integer.toString());
            method.add(fastenURI.toString());
            methods.add(method);
        });
        return methods;
    }

    public static List<String> toListOfString(final List<FastenURI> list) {
        final List<String> result = new ArrayList<>();
        for (FastenURI fastenURI : list) {
            result.add(fastenURI.toString());
        }
        return result;
    }

    public static ExtendedRevisionCallGraph create(final String forge, final MavenCoordinate coordinate,
                                                   final long timestamp, final PartialCallGraph partialCallGraph) {

        return new ExtendedRevisionCallGraph(forge,
            coordinate.getProduct(),
            coordinate.getVersionConstraint(),
            timestamp,
            MavenCoordinate.MavenResolver.resolveDependencies(coordinate.getCoordinate()),
            partialCallGraph.getMapedGraph(),
            PartialCallGraph.toURIHierarchy(partialCallGraph.getClassHierarchy()));
    }

    public static ExtendedRevisionCallGraph create(final String forge, final MavenCoordinate coordinate,
                                                   final long timestamp) throws FileNotFoundException {

        logger.info("Generating call graph using Opal ...");
        final var partialCallGraph = new PartialCallGraph(
            MavenCoordinate.MavenResolver.downloadJar(coordinate.getCoordinate()).orElseThrow(RuntimeException::new)
        );

        logger.info("Opal call graph has been generated.");

        logger.info("Converting class hierarchy to URIs ...");

        final var classHierarcy = PartialCallGraph.toURIHierarchy(partialCallGraph.getClassHierarchy());

        logger.info("All entities of the class hierarchy have been converted to URIs.");

        logger.info("Converting edges to URIs ...");

        final var graph = partialCallGraph.getMapedGraph();

        logger.info("All edges of the graph have been converted to URIs.");

        logger.info("Building the extended revision call graph ...");

        return new ExtendedRevisionCallGraph(forge,
            coordinate.getProduct(),
            coordinate.getVersionConstraint(),
            timestamp,
            MavenCoordinate.MavenResolver.resolveDependencies(coordinate.getCoordinate()),
            graph,
            classHierarcy);
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

    public void sortGraphEdges() {
//        this.graph.sort(Comparator.comparing(o -> (o[0] + o[1])));
    }


    public static class Graph{
        private List<int[]> resolvedCalls;
        private Map<Integer,FastenURI> unresolvedCalls;

        public List<int[]> getResolvedCalls() {
            return resolvedCalls;
        }

        public Map<Integer, FastenURI> getUnresolvedCalls() {
            return unresolvedCalls;
        }

        public Graph(List<int[]> resolvedCalls, Map<Integer, FastenURI> unresolvedCalls) {
            this.resolvedCalls = resolvedCalls;
            this.unresolvedCalls = unresolvedCalls;
        }

        public int size() {
            return resolvedCalls.size()+unresolvedCalls.size();
        }
    }
}
