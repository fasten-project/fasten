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

import java.util.Scanner;
import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.Record2;
import org.rocksdb.RocksDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.Centralities;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.CGMerger;
import eu.fasten.core.search.predicate.CachingPredicateFactory;
import it.unimi.dsi.fastutil.longs.Long2DoubleFunction;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.lang.ObjectParser;
import it.unimi.dsi.law.stat.WeightedTau;

public class TauStats {
	private static final Logger LOGGER = LoggerFactory.getLogger(TauStats.class);

	/** The handle to the Postgres metadata database. */
	private final DSLContext context;
	/** The handle to the RocksDB DAO. */
	private final RocksDao rocksDao;
	/** The resolver. */
	private final GraphMavenResolver resolver;
	/** The scorer that will be used to rank results. */
	private final Scorer scorer;

	public TauStats(final String jdbcURI, final String database, final String rocksDb, final String resolverGraph, final String scorer) throws Exception {
		this(PostgresConnector.getDSLContext(jdbcURI, database, false), new RocksDao(rocksDb, true), resolverGraph, scorer == null ? TrivialScorer.getInstance() : ObjectParser.fromSpec(scorer, Scorer.class));
	}

	public TauStats(final DSLContext context, final RocksDao rocksDao, final String resolverGraph, final Scorer scorer) throws Exception {
		this.context = context;
		this.rocksDao = rocksDao;
		this.scorer = scorer == null ? TrivialScorer.getInstance() : scorer;
		resolver = new GraphMavenResolver();
		resolver.buildDependencyGraph(context, resolverGraph);
		resolver.setIgnoreMissing(true);
		new CachingPredicateFactory(context);
	}

	public static void main(final String args[]) throws Exception {
		final SimpleJSAP jsap = new SimpleJSAP(TauStats.class.getName(), "Creates an instance of SearchEngine and answers queries from the command line (rlwrap recommended).", new Parameter[] {
				new UnflaggedOption("jdbcURI", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The JDBC URI."),
				new UnflaggedOption("database", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The database name."),
				new UnflaggedOption("rocksDb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The path to the RocksDB database of revision call graphs."),
				new UnflaggedOption("resolverGraph", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The path to a resolver graph (will be created if it does not exist)."), });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);

		final String jdbcURI = jsapResult.getString("jdbcURI");
		final String database = jsapResult.getString("database");
		final String rocksDb = jsapResult.getString("rocksDb");
		final String resolverGraph = jsapResult.getString("resolverGraph");

		final TauStats tauStats = new TauStats(jdbcURI, database, rocksDb, resolverGraph, null);
		final DSLContext context = tauStats.context;

		@SuppressWarnings("resource")
		Scanner scanner = new Scanner(System.in);
		while(scanner.hasNextLong()) {
			long gid = scanner.nextLong();
			final var graph = tauStats.rocksDao.getGraphData(gid);
			if (graph == null) continue;
		
			final Record2<String, String> record = context.select(Packages.PACKAGES.PACKAGE_NAME, PackageVersions.PACKAGE_VERSIONS.VERSION).from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES).on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID)).where(PackageVersions.PACKAGE_VERSIONS.ID.eq(Long.valueOf(gid))).fetchOne();
			final String[] a = record.component1().split(":");
			final String groupId = a[0];
			final String artifactId = a[1];
			final String version = record.component2();
			final Set<Revision> dependencySet = tauStats.resolver.resolveDependencies(groupId, artifactId, version, -1, context, true);
			final String name = groupId + ":" + artifactId + "$" + version;
			LOGGER.info("Analyzing graph " + name  + " with id " + gid);

			final var dm = new CGMerger(LongOpenHashSet.toSet(dependencySet.stream().mapToLong(x -> x.id)), context, tauStats.rocksDao);
			final var stitchedGraph = dm.mergeWithCHA(gid);
			
			Long2DoubleFunction globalRank = Centralities.pageRankParallel(stitchedGraph, gid);			
			
			System.out.println(gid + "\t" + name);
			
			for(Revision r: dependencySet) {
				var dep = tauStats.rocksDao.getGraphData(r.id);
				if (dep == null) continue;
				int n = dep.numNodes();
				Long2DoubleFunction localRank = Centralities.pageRankParallel(dep, gid);
				final long[] node2Gid = dep.nodes().toLongArray();
				final Long2IntOpenHashMap gid2Node = new Long2IntOpenHashMap();
				for(int i = 0; i < node2Gid.length; i++) gid2Node.put(node2Gid[i], i);
				
				double[] v = new double[n], w = new double[n];
				for(long x : dep.nodes()) {
					v[gid2Node.get(x)] = localRank.get(x);
					w[gid2Node.get(x)] = globalRank.get(x);
				}
				
				double t = WeightedTau.HYPERBOLIC.compute(v, w);
				System.out.println("\t" + r.id + ":" + t);
			}
	
		}
	}

}