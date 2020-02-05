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

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtendedRevisionCallGraph extends RevisionCallGraph {

    private static Logger logger = LoggerFactory.getLogger(ExtendedRevisionCallGraph.class);

    private Map<FastenURI, Type> classHierarchy;

    public Map<FastenURI, Type> getClassHierarchy() {
        return classHierarchy;
    }

    public void setClassHierarchy(Map<FastenURI, Type> classHierarchy) {
        this.classHierarchy = classHierarchy;
    }

    /**
     * Removes the content of the revision call graph.
     * @param excessiveRemove if it is true method will go through all the content
     *                        of the revision call graph and remove them one by one.
     *                        if false general references will be null.
     */
    public void clear(Boolean excessiveRemove) {
        if (excessiveRemove) {
            this.graph.parallelStream().forEach(i -> {
                i[0] = null;
                i[1] = null;
            });
            this.classHierarchy.forEach((fastenURI, type) -> {
                fastenURI = null;
                type.methods.parallelStream().forEach(i -> i = null);
                type.superClasses.parallelStream().forEach(i -> i = null);
                type.superInterfaces.parallelStream().forEach(i -> i = null);
            });
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
        //Methods that this type implements
        private List<FastenURI> methods;
        //Classes that this type inherits from in the order of instantiation.
        private LinkedList<FastenURI> superClasses;
        //Interfaces that this type or its super classes implement.
        private List<FastenURI> superInterfaces;

        public List<FastenURI> getMethods() {
            return methods;
        }

        public LinkedList<FastenURI> getSuperClasses() {
            return superClasses;
        }

        public List<FastenURI> getSuperInterfaces() {
            return superInterfaces;
        }

        public void setMethods(List<FastenURI> methods) {
            this.methods = methods;
        }

        public void setSuperClasses(LinkedList<FastenURI> superClasses) {
            this.superClasses = superClasses;
        }

        public void setSuperInterfaces(List<FastenURI> superInterfaces) {
            this.superInterfaces = superInterfaces;
        }

        public Type(List<FastenURI> methods, LinkedList<FastenURI> superClasses, List<FastenURI> superInterfaces) {
            this.methods = methods;
            this.superClasses = superClasses;
            this.superInterfaces = superInterfaces;
        }
    }

    public ExtendedRevisionCallGraph(String forge, String product, String version, long timestamp, List<List<Dependency>> depset, ArrayList<FastenURI[]> graph, Map<FastenURI, Type> classHierarchy) {
        super(forge, product, version, timestamp, depset, graph);
        this.classHierarchy = classHierarchy;
    }

    public ExtendedRevisionCallGraph(JSONObject json, boolean ignoreConstraints) throws JSONException, URISyntaxException {
        super(json, ignoreConstraints);
    }

    /**
     * It overrides the toJSON method of the RevisionCallGraph class in order to add ClassHierarchy to it.
     *
     * @return org.json.JSONObject of this type including the classHierarchy.
     */
    @Override
    public JSONObject toJSON() {
        var revisionCallGraphJSON = super.toJSON();
        final JSONObject chaJSON = new JSONObject();

        this.getClassHierarchy().forEach((clas, type) -> {

            final JSONObject typeJSON = new JSONObject();

            typeJSON.put("methods", toListOfString(type.methods));
            typeJSON.put("superClasses", toListOfString(type.superClasses));
            typeJSON.put("superInterfaces", toListOfString(type.superInterfaces));

            chaJSON.put(clas.toString(), typeJSON);
        });

        revisionCallGraphJSON.put("cha", chaJSON);

        return revisionCallGraphJSON;
    }

    public static List<String> toListOfString(List<FastenURI> list) {
        List<String> result = new ArrayList<>();
        for (FastenURI fastenURI : list) {
            result.add(fastenURI.toString());
        }
        return result;
    }

    public static ExtendedRevisionCallGraph create(String forge, MavenCoordinate coordinate, long timestamp, PartialCallGraph partialCallGraph) {

        return new ExtendedRevisionCallGraph(forge,
            coordinate.getProduct(),
            coordinate.getVersionConstraint(),
            timestamp,
            MavenCoordinate.MavenResolver.resolveDependencies(coordinate.getCoordinate()),
            partialCallGraph.toURIGraph(),
            PartialCallGraph.toURIHierarchy(partialCallGraph.getClassHierarchy()));
    }

    public static ExtendedRevisionCallGraph create(String forge, MavenCoordinate coordinate, long timestamp) throws FileNotFoundException {

        logger.info("Generating call graph using Opal ...");
        PartialCallGraph partialCallGraph = new PartialCallGraph(
            MavenCoordinate.MavenResolver.downloadJar(coordinate.getCoordinate()).orElseThrow(RuntimeException::new)
        );
        logger.info("Opal call graph has been generated.");
        logger.info("Converting edges to URIs ...");

        var graph = partialCallGraph.toURIGraph();

        logger.info("All edges of the graph have been converted to URIs.");
        logger.info("Cleaning the opal call graph from memory ...");

        partialCallGraph.clearGraph();

        logger.info("The Opal call graph has been removed from memory.");
        logger.info("Converting class hierarchy to URIs ...");

        var classHierarcy = PartialCallGraph.toURIHierarchy(partialCallGraph.getClassHierarchy());

        logger.info("All entities of the class hierarchy have been converted to URIs.");
        logger.info("Cleaning the opal call class hierarchy from memory ...");

        partialCallGraph.clearClassHierarchy();

        logger.info("The Opal call class hierarchy has been removed from memory.");
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
        return this.graph.isEmpty();
    }

    public void sortGraphEdges() {
        this.graph.sort(Comparator.comparing(o -> (o[0].toString() + o[1].toString())));
    }


}
