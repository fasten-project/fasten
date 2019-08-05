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


package eu.fasten.analyzer.data.fastenJSON;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ibm.wala.types.TypeName;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.analyzer.data.type.MavenResolvedCoordinate;
import eu.fasten.analyzer.generator.WalaUFIAdapter;
import eu.fasten.analyzer.lapp.call.Call;
import eu.fasten.analyzer.lapp.core.Method;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
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

    public static String createJson(WalaUFIAdapter wrapped_cg, String date) throws IOException {

        List<MavenResolvedCoordinate> dependencies = wrapped_cg.callGraph.analyzedClasspath;

        JSONObject canonicalJson = new JSONObject();
        canonicalJson.put("version", dependencies.get(0).version);
        canonicalJson.put("product", dependencies.get(0).groupId + dependencies.get(0).artifactId);
        canonicalJson.put("forge", "mvn");
        canonicalJson.put("timestamp", date);


        JSONArray depset = new JSONArray();
        for (int i = 1; i < dependencies.size(); i++) {
            MavenResolvedCoordinate currentDep = dependencies.get(i);
            JSONObject dep = new JSONObject();
            dep.put("forge", "mvn");
            dep.put("product", currentDep.groupId + currentDep.artifactId);
            JSONArray constraints = new JSONArray();
            constraints.add("[" + currentDep.version + "]");
            dep.put("constraints", constraints);
            depset.add(dep);
        }
        canonicalJson.put("depset", depset);

        JSONArray graph = new JSONArray();
        for (Call resolvedCall : wrapped_cg.lappPackage.resolvedCalls) {
            JSONArray call = new JSONArray();
            call.add(convertToFastenURI(resolvedCall.source).toString());
            call.add(convertToFastenURI(resolvedCall.target).toString());
            graph.add(call);
        }
        for (Call unresolvedCall : wrapped_cg.lappPackage.resolvedCalls) {
            JSONArray call = new JSONArray();
            call.add(convertToFastenURI(unresolvedCall.source).toString());
            call.add(convertToFastenURI(unresolvedCall.target, "SomeDependency").toString());
            graph.add(call);
        }
        canonicalJson.put("graph", graph);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String json = gson.toJson(canonicalJson);

        return json;

    }

}

