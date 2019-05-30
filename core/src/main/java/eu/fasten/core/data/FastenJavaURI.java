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

package eu.fasten.core.data;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/** A class representing a Fasten URI for the Java language; it has to be considered experimental until the BNF for such URIs is set in stone. */

public class FastenJavaURI extends FastenURI {
	private final static FastenJavaURI[] NO_ARGS_ARRAY = new FastenJavaURI[0];
	protected final String className;
	protected final String functionOrAttributeName;
	protected final FastenJavaURI[] args;
	protected final FastenJavaURI returnType;

	public FastenJavaURI(final String s) throws URISyntaxException {
		this(new FastenURI(s));
	}

	public FastenJavaURI(final FastenURI fastenURI) {
		super(fastenURI.uri);
		if (entity == null) {
			className = null;
			functionOrAttributeName = null;
			returnType = null;
			args = null;
			return;
		}
		final var dotPos = entity.indexOf(".");
		if (dotPos == -1) { // entity-type
			className = entity;
			functionOrAttributeName = null;
			returnType = null;
			args = null;
			return;
		}
		className = entity.substring(0, dotPos);
		final var funcArgsType = entity.substring(dotPos + 1);
		final var openParenPos = funcArgsType.indexOf('(');
		if (openParenPos == -1) { // entity-attribute
			args = null;
			returnType = null;
			functionOrAttributeName = null;
			return;
		}
		functionOrAttributeName = funcArgsType.substring(0, openParenPos);
		final var closedParenPos = funcArgsType.indexOf(')');
		if (closedParenPos == -1) throw new IllegalArgumentException("Missing close parenthesis");
		returnType = FastenJavaURI.create(funcArgsType.substring(closedParenPos + 1));
		final var argString = funcArgsType.substring(openParenPos + 1, closedParenPos);
		if (argString.length() == 0) {
			args = NO_ARGS_ARRAY;
			return;
		}

		final var a = argString.split(",");
		args = new FastenJavaURI[a.length];
		for(int i = 0; i < a.length; i++) args[i] = FastenJavaURI.create(URLDecoder.decode(a[i], StandardCharsets.UTF_8));
	}

	/**
	 * Creates a {@link FastenURI} from a string, with the same logic of {@link URI#create(String)}.
	 * @param s a string specifying a {@link FastenURI}.
	 * @return a {@link FastenURI}.
	 */

	public static FastenJavaURI create(final String s) {
		return new FastenJavaURI(FastenURI.create(s));
	}

	/**
	 * Creates a {@link FastenJavaURI} from a {@link URI}.
	 * @param uri a {@link URI} a specifying a {@link FastenJavaURI}.
	 * @return a {@link FastenJavaURI}.
	 * @throws IllegalArgumentException if the argument does not satisfy the further constraints of a {@link FastenJavaURI}.
	 */

	public static FastenJavaURI create(final URI uri) {
		return new FastenJavaURI(FastenURI.create(uri));
	}

	public String getClassName() {
		return className;
	}

	public String getFunctionName() {
		return functionOrAttributeName;
	}

	public FastenJavaURI[] getArgs() {
		return args.clone(); // defensive copy?
	}

	public FastenJavaURI getReturnType() {
		return returnType;
	}


	/** Returns the {@link FastenJavaURI} of a given class in a specific context. The context is used to omit (relativize) the parts of the
	 *  URI that are the same as in the context; it can be <code>null</code> if there is no context. For each package, the name of the
	 *  artefact containing the package is deduced from <code>packageToArtefact</code>; if not present as a key, the artefact is
	 *  assumed to be named as the package. The forge and / or version are deduced similarly from the other two maps.
	 *
	 * @param klass the class for which the URI is needed.
	 * @param context the context URI.
	 * @param packageToArtefact a map from package names to artefact names.
	 * @param artefactToForge a map from artefact names to forge names.
	 * @param artefactToVersion a map from artefact names to versions.
	 * @return the {@link FastenJavaURI} for <code>method</code>.
	 * @throws URISyntaxException
	 */
	private static FastenJavaURI getTypeURI(final Class<?> klass, final FastenJavaURI context, final Map<String, String> packageToArtefact, final Map<String, String> artefactToForge, final Map<String, String> artefactToVersion) throws URISyntaxException {
		final StringBuilder sb = new StringBuilder();
		final String module = klass.getPackageName();
		final String artefact = packageToArtefact.getOrDefault(module, module);
		final String forge = artefactToForge.get(artefact);
		final String version = artefactToVersion.get(artefact);
		final String type = klass.getSimpleName();
		boolean entityOnly = true;
		if (context == null || !artefact.equals(context.getProduct()) || forge != null && !forge.equals(context.getForge()) || version != null && !version.equals(context.getVersion())) {
			if (context == null || !"fasten".equals(context.getScheme())) sb.append("fasten:");
			sb.append("//");
			if (forge != null && (context == null || !forge.equals(context.getForge()))) sb.append(forge + "!");
			sb.append(artefact);
			if (version != null && (context == null || !version.equals(context.getVersion()))) sb.append("$" + version);
			entityOnly = false;
		}
		if (context == null || !module.equals(context.getNamespace())) {
			sb.append("/" + module);
			entityOnly = false;
		}
		if (!entityOnly) sb.append("/");
		sb.append(type);
		return new FastenJavaURI(sb.toString());
	}

	/** Returns the {@link FastenJavaURI} of a given method in a specific context. The context is used to omit (relativize) the parts of the
	 *  URI that are the same as in the context; it can be <code>null</code> if there is no context. For each package, the name of the
	 *  artefact containing the package is deduced from <code>packageToArtefact</code>; if not present as a key, the artefact is
	 *  assumed to be named as the package. The forge and / or version are deduced similarly from the other two maps.
	 *
	 * @param method the method for which the URI is needed.
	 * @param context the context URI.
	 * @param packageToArtefact a map from package names to artefact names.
	 * @param artefactToForge a map from artefact names to forge names.
	 * @param artefactToVersion a map from artefact names to versions.
	 * @return the {@link FastenJavaURI} for <code>method</code>.
	 * @throws URISyntaxException
	 */
	public static FastenJavaURI getURI(final Method method, final FastenJavaURI context, final Map<String, String> packageToArtefact, final Map<String, String> artefactToForge, final Map<String, String> artefactToVersion) throws URISyntaxException {
		final FastenJavaURI typeURI = getTypeURI(method.getDeclaringClass(), context, packageToArtefact, artefactToForge, artefactToVersion);
		final FastenJavaURI returnTypeURI = getTypeURI(method.getReturnType(), typeURI, packageToArtefact, artefactToForge, artefactToVersion);
		final Class<?>[] parameterType = method.getParameterTypes();
		final int n = parameterType.length;
		final FastenJavaURI[] parameterTypeURI = new FastenJavaURI[n];
		for (int i = 0; i < n; i++)
			parameterTypeURI[i] = getTypeURI(parameterType[i], typeURI, packageToArtefact, artefactToForge, artefactToVersion);
		final StringBuilder sb = new StringBuilder();
		sb.append(typeURI.toString() + "." + method.getName() + "(");
		for (int i = 0; i < n; i++) {
			if (i > 0) sb.append(",");
			sb.append(URLEncoder.encode(parameterTypeURI[i].toString(), StandardCharsets.UTF_8));
		}
		sb.append(")");
		sb.append(URLEncoder.encode(returnTypeURI.toString(), StandardCharsets.UTF_8));
		return new FastenJavaURI(sb.toString());
	}

	/** Returns the {@link FastenJavaURI} of a given method in a specific context. The context is used to omit (relativize) the parts of the
	 *  URI that are the same as in the context; it can be <code>null</code> if there is no context. For each package, the name of the
	 *  artefact containing the package is <code>jdk</code>.
	 *
	 * @param method the method for which the URI is needed.
	 * @param context the context URI.
	 * @return the {@link FastenJavaURI} for <code>method</code>.
	 * @throws URISyntaxException
	 */
	public static FastenJavaURI getURI(final Method method, final FastenJavaURI context) throws URISyntaxException {
		final Map<String, String> emptyMap = Collections.<String, String>emptyMap();
		final Object2ObjectOpenHashMap<String, String> jdkMap = new Object2ObjectOpenHashMap<>();
		jdkMap.defaultReturnValue("jdk");
		return getURI(method, context, jdkMap, emptyMap, emptyMap);
	}

	/** Returns the {@link FastenJavaURI} of a given class in a specific context. The context is used to omit (relativize) the parts of the
	 *  URI that are the same as in the context; it can be <code>null</code> if there is no context. For each package, the name of the
	 *  artefact containing the package is <code>jdk</code>.
	 *
	 * @param klass the class for which the URI is needed.
	 * @param context the context URI.
	 * @return the {@link FastenJavaURI} for <code>method</code>.
	 * @throws URISyntaxException
	 */
	public static FastenJavaURI getURI(final Class<?> klass, final FastenJavaURI context) throws URISyntaxException {
		final Map<String, String> emptyMap = Collections.<String, String>emptyMap();
		final Object2ObjectOpenHashMap<String, String> jdkMap = new Object2ObjectOpenHashMap<>();
		jdkMap.defaultReturnValue("jdk");
		return getTypeURI(klass, context, jdkMap, emptyMap, emptyMap);
	}

}
