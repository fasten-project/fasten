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

package eu.fasten.core.query;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;

import org.json.JSONException;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.KnowledgeBase;
import it.unimi.dsi.fastutil.io.BinIO;
/** A sample in-memory indexer that reads, compresses and stores in memory
 *  graphs stored in JSON format and answers to impact queries.
 *
 */
public class QueryEngine {

	private static final Logger LOGGER = LoggerFactory.getLogger(QueryEngine.class);
	private final KnowledgeBase kb;
	
	/** Creates an indexer using the given database instance.
	 * 
	 * @param db the database instance used by this indexer.
	 */
	public QueryEngine(final KnowledgeBase kb) {
		this.kb = kb;
	}
	
	public static class ReachabilityQuery implements Query {
		private FastenURI fastenURI;

		public ReachabilityQuery(final FastenURI fastenURI) {
			this.fastenURI = fastenURI;
		}
		
		@Override
		public Collection<FastenURI> execute(KnowledgeBase kb) {
			return kb.reaches(fastenURI);
		}		
	}

	public static class CoreachabilityQuery implements Query {
		private FastenURI fastenURI;

		public CoreachabilityQuery(final FastenURI fastenURI) {
			this.fastenURI = fastenURI;
		}
		
		@Override
		public Collection<FastenURI> execute(KnowledgeBase kb) {
			return kb.coreaches(fastenURI);
		}		
	}

	@SuppressWarnings("boxing")
	public static void main(final String[] args) throws JSONException, URISyntaxException, JSAPException, IOException, RocksDBException, InterruptedException, ExecutionException, ClassNotFoundException {
		final SimpleJSAP jsap = new SimpleJSAP( QueryEngine.class.getName(),
				"Searches a given knowledge base (associated to a database)",
				new Parameter[] {
						new UnflaggedOption("kb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The file containing the Knoweldge Base instance." ),
						new UnflaggedOption("db", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The directory containing the RocksDB instance." ),
		});

		final JSAPResult jsapResult = jsap.parse(args);
		if ( jsap.messagePrinted() ) return;

		String kbFilename = jsapResult.getString("kb");
		String dbName = jsapResult.getString("db");
		
		KnowledgeBase kb = (KnowledgeBase) BinIO.loadObject(kbFilename);
		kb.callGraphDB(dbName);
		
		QueryEngine indexer = new QueryEngine(kb);
		
		final BufferedReader br = new BufferedReader( new InputStreamReader( jsapResult.userSpecified( "input" ) ? new FileInputStream( jsapResult.getString( "input") ) : System.in ) );

		for ( ;; ) {
			System.out.print( ">" );
			final String q = br.readLine();
			if (q == null || "$quit".equals(q)) {
				System.err.println("Exiting");
				break; // CTRL-D
			}
			if ( q.length() == 0 ) continue;

			final FastenURI uri;
			try {
				uri = FastenURI.create(q.substring(1));
			}
			catch(final Exception e) {
				e.printStackTrace(System.err);
				continue;
			}

			Query query;
			switch(q.charAt(0)) {
				case '+':
					query = new ReachabilityQuery(uri);
					break;
				case '-':
					query = new CoreachabilityQuery(uri);
					break;
				default:
					System.err.println("Unknown query operator " + q.charAt(0));
					continue;
			}
			long elapsed = - System.nanoTime();
			final Collection<FastenURI> result = query.execute(kb);
			if (result == null) {
				System.out.println("Method not indexed");
				continue;
			}

			if (result.size() == 0) {
				System.out.println("Query returned no results");
				continue;
			}

			elapsed += System.nanoTime();
			System.err.printf("Elapsed: %.3fs (%d results, %.3f nodes/s)\n", elapsed / 1E09, result.size(), 1E09 * result.size() / elapsed);
			final Iterator<FastenURI> iterator = result.iterator();
			for(int i = 0; iterator.hasNext() && i < 10; i++) System.out.println(iterator.next());
			if (result.size() > 10) System.out.println("[...]");
		}

		kb.close();
	}
}
