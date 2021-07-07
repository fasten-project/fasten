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

package eu.fasten.core.search;

import org.jooq.DSLContext;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.graphdb.RocksDao;

public class Test {

	/** The handle to the RocksDB DAO. */
	private final RocksDao rocksDao;

	public Test(final String rocksDb) throws Exception {
		this(new RocksDao(rocksDb, true));
	}

	/**
	 * Creates a new search engine using a given {@link DSLContext} and {@link RocksDao}.
	 *
	 * @param context the DSL context.
	 * @param rocksDao the RocksDB DAO.
	 * @param resolverGraph the path to a serialized resolver graph (will be created if it does not
	 *            exist).
	 * @param scorer a scorer that will be used to sort results; if {@code null}, a
	 *            {@link TrivialScorer} will be used instead.
	 */

	public Test(final RocksDao rocksDao) throws Exception {
		this.rocksDao = rocksDao;
	}

	@SuppressWarnings("boxing")
	public static void main(final String args[]) throws Exception {
		final SimpleJSAP jsap = new SimpleJSAP(Test.class.getName(), "Creates an instance of SearchEngine and answers queries from the command line (rlwrap recommended).", new Parameter[] {
				new UnflaggedOption("rocksDb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The path to the RocksDB database of revision call graphs.") });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);

		final String rocksDb = jsapResult.getString("rocksDB");

		/* WARNING
		 *
		 * As of JDK 11.0.10, replacing the constant string below with the parameter "rocksDb" causes
		 * a JVM crash.
		 */

		final Test searchEngine = new Test(rocksDb);

	}

}