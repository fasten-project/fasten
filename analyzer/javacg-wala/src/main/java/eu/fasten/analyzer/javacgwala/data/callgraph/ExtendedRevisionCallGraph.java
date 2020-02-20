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

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.RevisionCallGraph;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.json.JSONObject;

public class ExtendedRevisionCallGraph extends RevisionCallGraph {

    /**
     * Type can be a class or interface that inherits (implements) from others or implements methods.
     */
    public static class Type {
        /**
         * The source file name of this type.
         */
        private String sourceFileName;

        /**
         * Methods that this type implements.
         */
        private Map<FastenURI, Integer> methods;

        /**
         * Classes that this type inherits from in the order of instantiation.
         */
        private LinkedList<FastenURI> superClasses;

        /**
         * Interfaces that this type or its super classes implement.
         */
        private List<FastenURI> superInterfaces;

        /**
         * Construct a Type object from given data.
         *
         * @param sourceFile      Source file
         * @param methods         List of methods
         * @param superClasses    Super classes in the order of inheritance
         * @param superInterfaces Implemented interfaces
         */
        public Type(String sourceFile, Map<FastenURI, Integer> methods, LinkedList<FastenURI> superClasses,
                    List<FastenURI> superInterfaces) {
            this.sourceFileName = sourceFile;
            this.methods = methods;
            this.superClasses = superClasses;
            this.superInterfaces = superInterfaces;
        }

        public String getSourceFileName() {
            return sourceFileName;
        }

        public Map<FastenURI, Integer> getMethods() {
            return methods;
        }

        public LinkedList<FastenURI> getSuperClasses() {
            return superClasses;
        }

        public List<FastenURI> getSuperInterfaces() {
            return superInterfaces;
        }

        public void setMethods(Map<FastenURI, Integer> methods) {
            this.methods = methods;
        }

        public void setSuperClasses(LinkedList<FastenURI> superClasses) {
            this.superClasses = superClasses;
        }

        public void setSuperInterfaces(List<FastenURI> superInterfaces) {
            this.superInterfaces = superInterfaces;
        }
    }

    private Map<FastenURI, Type> classHierarchy;

    /**
     * Creates a JSON call graph with given data.
     *
     * @param forge          the forge
     * @param product        the product
     * @param version        the version
     * @param timestamp      the timestamp (in seconds from UNIX epoch); optional: if not present, \it
     *                       is set to -1
     * @param depset         the depset
     * @param graph          the call graph (no control is done on the graph)
     * @param classHierarchy class hierarchy
     */
    public ExtendedRevisionCallGraph(String forge, String product, String version, long timestamp,
                                     List<List<Dependency>> depset, ArrayList<FastenURI[]> graph,
                                     Map<FastenURI, Type> classHierarchy) {
        super(forge, product, version, timestamp, depset, graph);
        this.classHierarchy = classHierarchy;
    }

    public Map<FastenURI, Type> getClassHierarchy() {
        return classHierarchy;
    }

    public void setClassHierarchy(final Map<FastenURI, Type> classHierarchy) {
        this.classHierarchy = classHierarchy;
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
            this.graph.parallelStream().forEach(i -> {
                i[0] = null;
                i[1] = null;
            });

            this.classHierarchy.forEach((fastenURI, type) -> {
                fastenURI = null;
                type.methods.keySet().parallelStream().forEach(i -> i = null);
                type.methods.values().parallelStream().forEach(i -> i = null);
                type.superClasses.parallelStream().forEach(i -> i = null);
                type.superInterfaces.parallelStream().forEach(i -> i = null);
            });
            this.classHierarchy.clear();
        } else {
            this.graph = null;
            this.classHierarchy = null;
        }
    }

    /**
     * Removes the content of the revision call graph.
     */
    public void clear() {
        clear(false);
    }

    /**
     * It overrides the toJSON method of the RevisionCallGraph class in order to add ClassHierarchy to it.
     *
     * @return org.json.JSONObject of this type including the classHierarchy.
     */
    @Override
    public JSONObject toJSON() {
        final var revisionCallGraphJSON = super.toJSON();
        final JSONObject chaJSON = new JSONObject();

        this.getClassHierarchy().forEach((clas, type) -> {

            final JSONObject typeJSON = new JSONObject();

            typeJSON.put("methods", toListOfString(new ArrayList<>(type.methods.keySet())));
            typeJSON.put("superClasses", toListOfString(type.superClasses));
            typeJSON.put("superInterfaces", toListOfString(type.superInterfaces));

            chaJSON.put(clas.toString(), typeJSON);
        });

        revisionCallGraphJSON.put("cha", chaJSON);

        return revisionCallGraphJSON;
    }

    /**
     * Convert a list of FastenURIs to list of Strings.
     *
     * @param list List of FastenURIs
     * @return List of Strings
     */
    public static List<String> toListOfString(final List<FastenURI> list) {
        final List<String> result = new ArrayList<>();
        for (FastenURI fastenURI : list) {
            if (fastenURI != null) {
                result.add(fastenURI.toString());
            }
        }
        return result;
    }

    /**
     * Note that this is a temporary method for finding a Maven coordinate that generates an empty
     * call graph. Later on, this method might be helpful for not sending an empty call graph.
     *
     * @return true if call graph is empty
     */
    public boolean isCallGraphEmpty() {
        return this.graph.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtendedRevisionCallGraph that = (ExtendedRevisionCallGraph) o;
        return Objects.equals(classHierarchy, that.classHierarchy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classHierarchy);
    }
}
