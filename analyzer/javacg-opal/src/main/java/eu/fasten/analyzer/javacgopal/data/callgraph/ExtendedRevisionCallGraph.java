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

import java.net.URISyntaxException;
import java.util.*;

import org.json.JSONException;
import org.json.JSONObject;

public class ExtendedRevisionCallGraph extends RevisionCallGraph {

    private Map<FastenURI, Type> classHierarchy;

    public Map<FastenURI, Type> getClassHierarchy() {
        return classHierarchy;
    }

    public void setClassHierarchy(Map<FastenURI, Type> classHierarchy) {
        this.classHierarchy = classHierarchy;
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

    /**
     * Note that this is a temporary method for finding a Maven coordinate that generates an empty
     * call graph. Later on, this method might be helpful for not sending an empty call graph.
     * @return boolean
     */
    public boolean isCallGraphEmpty(){
        return this.graph.isEmpty();
    }

    public void sortGraphEdges(){
        this.graph.sort(Comparator.comparing(o -> (o[0].toString() + o[1].toString())));
    }
}
