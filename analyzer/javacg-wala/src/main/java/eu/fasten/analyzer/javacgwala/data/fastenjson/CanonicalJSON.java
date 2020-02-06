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


package eu.fasten.analyzer.javacgwala.data.fastenjson;

import com.ibm.wala.types.TypeName;
import eu.fasten.analyzer.javacgwala.data.type.MavenResolvedCoordinate;
import eu.fasten.analyzer.javacgwala.generator.WalaUFIAdapter;
import eu.fasten.analyzer.javacgwala.lapp.call.Call;
import eu.fasten.analyzer.javacgwala.lapp.core.LappPackage;
import eu.fasten.analyzer.javacgwala.lapp.core.Method;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.RevisionCallGraph;

import java.util.ArrayList;
import java.util.List;

public class CanonicalJSON {

    /**
     * Convert Wala method {@link Method} to FASTEN compatible {@link FastenJavaURI}.
     *
     * @param method - wala method
     * @return - canonicalized FastenJavaURI
     */
    public static FastenJavaURI convertToFastenURI(Method method) {
        FastenJavaURI uri = FastenJavaURI.create(getMethodInfo(method));
        return uri.canonicalize();
    }

    //public static FastenJavaURI convertToFastenURI(Method method, String product) {
    //    FastenJavaURI uri = new FastenJavaURI("//" + product + getMethodInfo(method));
    //    return uri;
    //}

    /**
     * Converts all nodes {@link Call} of a Wala call graph to URIs.
     *
     * @return - A graph of all nodes in URI format represented in a List of {@link FastenURI}
     */
    public static ArrayList<FastenURI[]> toURIGraph(LappPackage lappPackage) {

        var graph = new ArrayList<FastenURI[]>();

        for (Call resolvedCall : lappPackage.resolvedCalls) {
            addCall(graph, resolvedCall);
        }

        for (Call unresolvedCall : lappPackage.unresolvedCalls) {
            addCall(graph, unresolvedCall);
        }

        return graph;
    }

    private static void addCall(ArrayList<FastenURI[]> graph, Call call) {
        var sourceJavaURI = convertToFastenURI(call.source);
        var targetJavaURI = convertToFastenURI(call.target);

        var uriCall = call.toURICall(sourceJavaURI, targetJavaURI);

        if (uriCall[0] != null && uriCall[1] != null && !graph.contains(uriCall)) {
            graph.add(uriCall);
        }
    }

    /**
     * Getter for the type of a method.
     *
     * @param type - TypeName to extract name from
     * @return - method type
     */
    public static String getType(TypeName type) {
        if (type == null) {
            return "";
        }
        if (type.getClassName() == null) {
            return "";
        }
        var packagename = type.getPackage() == null ? "" : type.getPackage().toString()
                .replace("/", ".");
        var classname = type.getClassName().toString();

        return "/" + packagename + "/" + classname;
    }

    /**
     * Creates a URI representation for method's namespace, typeName, functionName, arguments list,
     * and return type.
     *
     * @param method - method to extract URI from
     * @return - URI representation of a method
     */
    public static String getMethodInfo(Method method) {
        String namespace = method.namespace.substring(0, method.namespace.lastIndexOf("."));
        String typeName = method.namespace.substring(method.namespace.lastIndexOf(".") + 1);
        String functionName = method.symbol.getName().toString()
                .replace("<init>", "initialMethod").replace("<clinit>", "ClassInitial");
        functionName = functionName.contains("lambda")
                ? FastenJavaURI.pctEncodeArg(functionName) : functionName;
        TypeName[] args = method.symbol.getDescriptor().getParameters();
        String argTypes = "";
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                argTypes = i == args.length - 1 ? FastenJavaURI.pctEncodeArg(getType(args[i]))
                        : FastenJavaURI.pctEncodeArg(getType(args[i])) + ",";
            }
        }
        String returnType = FastenJavaURI.pctEncodeArg(getType(method.symbol.getDescriptor()
                .getReturnType()));
        return "/" + namespace + "/" + typeName + "." + functionName + "("
                + argTypes + ")" + returnType;
    }


    //private static String cleanupVersion(String version) {
    //    return version.substring(0, version.contains("-") ? version.indexOf("-")
    //            : version.length());
    //}

    //private static String coordToProduct(MavenResolvedCoordinate coord) {
    //    return coord.artifactId + "." + coord.groupId;
    //}


    /**
     * Converts MavenResolvedCoordinate to a list of FASTEN compatible dependencies.
     *
     * @param coord - MavenResolvedCoordinate to convert
     * @return - List of FASTEN compatible dependencies
     */
    public static List<RevisionCallGraph.Dependency> toFastenDep(MavenResolvedCoordinate coord) {
        //var constraints = coord.   Constraint(final String lowerBound, final String upperBound)
        var result = new ArrayList<RevisionCallGraph.Dependency>();
        result.add(new RevisionCallGraph.Dependency("mvn",
                coord.groupId + ":" + coord.artifactId,
                new ArrayList<>()
        //Arrays.asList(new RevisionCallGraph.Constraint[1])
        ));
        return result;

    }

    /**
     * Convert a Wala call graph to FASTEN compatible format.
     *
     * @param wrappedCG - adapted wala call graph
     * @param date       - date
     * @return - FASTEN call graph
     */
    public static RevisionCallGraph toJsonCallgraph(WalaUFIAdapter wrappedCG, long date) {
        List<MavenResolvedCoordinate> dependencies = wrappedCG.callGraph.analyzedClasspath;

        List<List<RevisionCallGraph.Dependency>> deparray = new ArrayList<>(dependencies.size());
        //Arrays.asList(new RevisionCallGraph.Dependency[dependencies.size()]);
        for (MavenResolvedCoordinate dependency : dependencies) {
            deparray.add(toFastenDep(dependency));
        }

        var graph = toURIGraph(wrappedCG.lappPackage);

        return new RevisionCallGraph(
                "mvn",
                dependencies.get(0).groupId + "." + dependencies.get(0).artifactId,
                dependencies.get(0).version,
                date, deparray, graph
        );
    }
}

