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

package eu.fasten.analyzer.javacgopalv3.evaluation;

import eu.fasten.analyzer.javacgopalv3.data.OPALCallSite;
import eu.fasten.analyzer.javacgopalv3.ExtendedRevisionCallGraph;
import eu.fasten.analyzer.javacgopalv3.ExtendedRevisionCallGraph.Node;
import eu.fasten.core.data.FastenURI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONArray;
import org.json.JSONObject;

public class JCGFormat {

    public static JSONObject convertERCGTOJCG(final ExtendedRevisionCallGraph ercg) {

        if (ercg.isCallGraphEmpty()) {
            return new JSONObject();
        }
        return getJCGJSON(groupByCallSite(getAdjacencyList(ercg)));
    }

    private static JSONObject getJCGJSON(
            final Map<FastenURI, Map<Map<Object, Object>, List<FastenURI>>> cgWithCallSites) {

        final var result = new JSONObject();
        final var reachableMethods = new JSONArray();

        for (final var source : cgWithCallSites.keySet()) {

            final var method = new JSONObject();
            method
                    .put("method", getMethodJSON(source));
            final var callSites = new JSONArray();
            for (final var targetsOfACallSite : cgWithCallSites.get(source).entrySet()) {
                for (final var callSite : targetsOfACallSite.getKey().entrySet()) {
                    final var callSiteJson = new JSONObject();
                    final var cs = new OPALCallSite(callSite.getValue().toString());
                    final var pc = (Integer) callSite.getKey();
                    callSiteJson.put("declaredTarget", getMethodJSON(cs.getReceiver(), targetsOfACallSite.getValue().get(0)));
                    callSiteJson.put("line", cs.getLine());
                    callSiteJson.put("pc", pc);
                    final var targets = new JSONArray();
                    for (final var target : targetsOfACallSite.getValue()) {
                        targets.put(getMethodJSON(target));
                    }
                    callSiteJson.put("targets", targets);
                    callSites.put(callSiteJson);
                }
            }
            method.put("callSites", callSites);

            reachableMethods.put(method);
        }
        result.put("reachableMethods", reachableMethods);
        return result;

    }

    private static <E> void putToJsonArray(final JSONArray array, final String key,
                                           final E content) {
        array.put(new JSONObject() {
            {
                put(key, content);
            }
        });
    }

    private static JSONObject getMethodJSON(final String type, final FastenURI uri) {
        return getMethodJSON(FastenURI.create(type + "." + StringUtils.substringAfter(uri.getEntity(), ".")));
    }

    private static JSONObject getMethodJSON(final FastenURI uri) {

        final var method = decodeMethod(uri.getEntity());
        final var result = new JSONObject();

        final var params = new JSONArray();
        for (var param : StringUtils.substringBetween(method, "(", ")").split("[,]")) {
            if (!param.isEmpty()) {
                params.put(toJVMType(dereletivize(uri.getNamespace(), param)));
            }
        }

        var name = decodeMethod(StringUtils.substringBetween(method, ".", "("));
        if (name.equals("<init>")) {
            name = "<clinit>";
        }
        if (name.equals(method.split("[.]")[0])) {
            name = "<init>";
        }

        result.put("name", name);
        result.put("parameterTypes", params);
        result.put("returnType", toJVMType(dereletivize(uri.getNamespace(),
                StringUtils.substringAfterLast(method, ")"))));
        result.put("declaringClass", toJVMType(uri.getNamespace() + "/" + method.split("[.]")[0]));

        return result;

    }

    private static String dereletivize(String type, String param) {
        return !param.contains("/") && !param.contains(".") ? type + "/" + param : param;
    }

    private static String decodeMethod(final String methodSignature) {
        String result = methodSignature;
        while (result.contains("%")) {
            result = URLDecoder.decode(result, StandardCharsets.UTF_8);
        }
        return result;
    }


    private static String toJVMType(final String type) {

        String result = type.replace(".", "/"), brakets = "";

        if (result.isEmpty()) {
            return result;
        }

        if (result.contains("[")) {
            brakets = "[".repeat(StringUtils.countMatches(result, "["));
            result = result.replaceAll("\\[", "").replaceAll("\\]", "");
        }
        if (result.startsWith("/")) {
            result = result.substring(1);
        }

        result = convertJavaTypes(result);
        if (result.contains("/")) {
            result = "L" + result + ";";
        }

        return brakets + result;

    }

    private static String convertJavaTypes(final String type) {

        final var WraperTypes = Map.of(
                "java/lang/ByteType", "B",
                "java/lang/ShortType", "S",
                "java/lang/IntegerType", "I",
                "java/lang/LongType", "J",
                "java/lang/FloatType", "F",
                "java/lang/DoubleType", "D",
                "java/lang/BooleanType", "Z",
                "java/lang/CharType", "C",
                "java/lang/VoidType", "V");

        return WraperTypes.getOrDefault(type, type);
    }

    private static Map<FastenURI, Map<Map<Object, Object>, List<FastenURI>>> groupByCallSite(
            final Map<FastenURI, List<Pair<FastenURI, Map<Object, Object>>>> cg) {

        final Map<FastenURI, Map<Map<Object, Object>, List<FastenURI>>> result = new HashMap<>();

        for (final var source : cg.keySet()) {
            final Map<Map<Object, Object>, List<FastenURI>> callSites = new HashMap<>();
            for (final var target : cg.get(source)) {
                final var line = target.getRight();
                callSites.put(line,
                        Stream.concat(callSites.getOrDefault(line, new ArrayList<>()).stream(),
                                Stream.of(target.getLeft())).collect(Collectors.toList()));
            }
            result.put(source, callSites);
        }

        return result;
    }

    private static String getSignature(final String rawEntity) {
        return rawEntity.substring(rawEntity.indexOf(".") + 1);
    }

    private static Map<FastenURI, List<Pair<FastenURI, Map<Object, Object>>>> getAdjacencyList(
            final ExtendedRevisionCallGraph ercg) {

        final Map<FastenURI, List<Pair<FastenURI, Map<Object, Object>>>> result = new HashMap<>();

        final var methods = ercg.mapOfAllMethods();
        for (final var internalCall : ercg.getGraphV3().getInternalCalls().entrySet()) {
            putCall(result, methods, internalCall);
        }

        for (final var externalCall : ercg.getGraphV3()
                .getExternalCalls().entrySet()) {
            putCall(result, methods, externalCall);
        }

        for (final var resolvedCall : ercg.getGraphV3()
                .getResolvedCalls().entrySet()) {
            putCall(result, methods, resolvedCall);
        }

        return result;
    }

    private static void putCall(final Map<FastenURI, List<Pair<FastenURI, Map<Object, Object>>>> result,
                                final Map<Integer, Node> methods,
                                final Map.Entry<List<Integer>, Map<Object, Object>> call) {

        final var source = methods.get(call.getKey().get(0)).getUri();
        final var targets = result.getOrDefault(source, new ArrayList<>());

        targets.add(MutablePair.of(methods.get(call.getKey().get(1)).getUri(),
                call.getValue()));

        result.put(source, targets);
    }
}
