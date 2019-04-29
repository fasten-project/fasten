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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** A class representing a Fasten URI for the Java language; it has to be considered experimental until the BNF for such URIs is set in stone. */

public class FastenJavaURI extends FastenURI {
	private final static FastenJavaURI[] NO_ARGS_ARRAY = new FastenJavaURI[0];
	protected final String className;
	protected final String functionName;
	protected final FastenJavaURI[] args;
	protected final FastenJavaURI returnType;

	public FastenJavaURI(final String s) throws URISyntaxException {
		this(new FastenURI(s));
	}

	public FastenJavaURI(final FastenURI fastenURI) {
		super(fastenURI.uri);
		if (entity == null) {
			className = null;
			functionName = null;
			returnType = null;
			args = null;
			return;
		}
		final var dotPos = entity.indexOf(".");
		if (dotPos == -1) {
			className = entity;
			functionName = null;
			returnType = null;
			args = null;
			return;
		}
		className = entity.substring(0, dotPos);
		final var funcArgsType = entity.substring(dotPos + 1);
		final var openParenPos = funcArgsType.indexOf('(');
		if (openParenPos == -1) throw new IllegalArgumentException("Missing open parenthesis");
		functionName = funcArgsType.substring(0, openParenPos);
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
		return functionName;
	}

	public FastenJavaURI[] getArgs() {
		return args.clone(); // defensive copy?
	}

	public FastenJavaURI getReturnType() {
		return returnType;
	}
	
}
