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


package eu.fasten.analyzer.javacgwala.data.fastenJSON;

import com.ibm.wala.types.TypeName;
import eu.fasten.analyzer.javacgwala.generator.WalaUFIAdapter;
import eu.fasten.analyzer.javacgwala.lapp.core.Method;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.analyzer.javacgwala.data.type.MavenResolvedCoordinate;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.JSONCallGraph;

import java.util.ArrayList;
import java.util.List;

public class CanonicalJSON {

    public static FastenJavaURI convertToFastenURI(Method method) {
        FastenJavaURI uri = new FastenJavaURI(getMethodInfo(method));
        return uri;
    }

    public static FastenJavaURI convertToFastenURI(Method method, String product) {
        FastenJavaURI uri = new FastenJavaURI("//" + product + getMethodInfo(method));
        return uri;
    }

    public static String getType(TypeName type){
        String packagename ,classname;

        if (type == null) return "";
        if (type.getClassName() == null) return "";
        packagename = ( type.getPackage() == null ? "" : type.getPackage().toString().replace("/", "."));
        classname = type.getClassName().toString();

        return "/" + packagename  + "/" + classname;
    }

    public static String getMethodInfo(Method method) {
        String namespace = method.namespace.substring(0, method.namespace.lastIndexOf(".") - 1).replace("$", ".");
        String typeName = method.namespace.substring(method.namespace.lastIndexOf(".") + 1).replace("$", ".");
        String functionName = method.symbol.getName().toString().replace("$", ".").replace("<init>", "initialMethod").replace("<clinit>", "ClassInitial");
        TypeName args[] = method.symbol.getDescriptor().getParameters();
        String argTypes = "";
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                argTypes = (i == args.length - 1 ? FastenJavaURI.pctEncodeArg(getType(args[i])) : FastenJavaURI.pctEncodeArg(getType(args[i])) + ",");
            }
        }
        String returnType = FastenJavaURI.pctEncodeArg(getType(method.symbol.getDescriptor().getReturnType()));
        return "/" + namespace + "/" + typeName + "." + functionName + "(" + argTypes + ")" + returnType;
    }


    private static String cleanupVersion(String version) {
        return version.substring(0, version.contains("-") ? version.indexOf("-") : version.length());
    }


    public static JSONCallGraph.Dependency toFastenDep(MavenResolvedCoordinate coord) {
        //var constraints = coord. Constraint(final String lowerBound, final String upperBound)
        return new JSONCallGraph.Dependency("mvn",
                coord.groupId + ":" + coord.artifactId,
                new JSONCallGraph.Constraint[1]
        );
    }

    public static JSONCallGraph toJsonCallgraph(WalaUFIAdapter wrapped_cg, long date) {
        List<MavenResolvedCoordinate> dependencies = wrapped_cg.callGraph.analyzedClasspath;

        var deparray = new JSONCallGraph.Dependency[dependencies.size()];
        for (int i = 0; i < dependencies.size(); i++){
            deparray[i] = toFastenDep(dependencies.get(i));
        }

        return new JSONCallGraph(
                "mvn",
                dependencies.get(0).groupId + "." + dependencies.get(0).artifactId,
                dependencies.get(0).version,
                date, deparray, (new ArrayList<FastenURI[]>())
        );
    }
}

