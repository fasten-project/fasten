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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.Centralities;
import eu.fasten.core.data.callableindex.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.CGMerger;
import eu.fasten.core.search.predicate.CachingPredicateFactory;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.longs.Long2DoubleFunction;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.law.stat.WeightedTau;
import it.unimi.dsi.law.stat.KendallTau;

public class TauStats {
	private static final Logger LOGGER = LoggerFactory.getLogger(TauStats.class);

	/** The handle to the Postgres metadata database. */
	private final DSLContext context;
	/** The handle to the RocksDB DAO. */
	private final RocksDao rocksDao;
	/** The resolver. */
	private final GraphMavenResolver resolver;
	
	public TauStats(final String jdbcURI, final String database, final String rocksDb, final String resolverGraph) throws Exception {
		this(PostgresConnector.getDSLContext(jdbcURI, database, false), new RocksDao(rocksDb, true), resolverGraph);
	}

	public TauStats(final DSLContext context, final RocksDao rocksDao, final String resolverGraph) throws Exception {
		this.context = context;
		this.rocksDao = rocksDao;
		resolver = new GraphMavenResolver();
		resolver.buildDependencyGraph(context, resolverGraph);
		resolver.setIgnoreMissing(true);
		new CachingPredicateFactory(context);
	}

	public static void main(final String args[]) throws Exception {
		final SimpleJSAP jsap = new SimpleJSAP(TauStats.class.getName(), "Creates an instance of SearchEngine and answers queries from the command line (rlwrap recommended).", new Parameter[] {
				new Switch("weighted", 'w', "weighted", "Use the hyperbolic weighted tau."),
				new UnflaggedOption("jdbcURI", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The JDBC URI."),
				new UnflaggedOption("database", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The database name."),
				new UnflaggedOption("rocksDb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The path to the RocksDB database of revision call graphs."),
				new UnflaggedOption("resolverGraph", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The path to a resolver graph (will be created if it does not exist)."), });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);

		final boolean weighted = jsapResult.getBoolean("weighted");
		final String jdbcURI = jsapResult.getString("jdbcURI");
		final String database = jsapResult.getString("database");
		final String rocksDb = jsapResult.getString("rocksDb");
		final String resolverGraph = jsapResult.getString("resolverGraph");

		final TauStats tauStats = new TauStats(jdbcURI, database, rocksDb, resolverGraph);
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

			var deps = LongOpenHashSet.toSet(dependencySet.stream().mapToLong(x -> x.id));
			deps.add(gid);
			final var dm = new CGMerger(deps, context, tauStats.rocksDao);
			final var stitchedGraph = ArrayImmutableDirectedGraph.copyOf(dm.mergeAllDeps(), false);

			Long2DoubleFunction globalRank = Centralities.pageRankParallel(stitchedGraph, 0.85);
			Long2DoubleFunction globalRankT = Centralities.pageRankParallel(stitchedGraph.transpose(), 0.85);
			
			System.out.println(gid + "\t" + name);
			long nodesInDeps = 0;
			
			for(Revision r: dependencySet) {
				LOGGER.info("Comparing with graph " + r.id);
				var depTemp = tauStats.rocksDao.getGraphData(r.id);
				if (depTemp == null) continue;
				var dep = ArrayImmutableDirectedGraph.copyOf(depTemp, false);
				int n = dep.numNodes();
				nodesInDeps += n;
				Long2DoubleFunction localRank = Centralities.pageRankParallel(dep, 0.85);
				Long2DoubleFunction localRankT = Centralities.pageRankParallel(dep.transpose(), 0.85);
				
				DoubleArrayList vl = new DoubleArrayList(), wl = new DoubleArrayList(), vtl =  new DoubleArrayList(), wtl =  new DoubleArrayList();

				for(long x : dep.nodes()) {
					if (stitchedGraph.containsVertex(x)) {
						vl.add(localRank.get(x));
						vtl.add(localRankT.get(x));
						wl.add(globalRank.get(x));
						wtl.add(globalRankT.get(x));
					}
				}

				double[] v = vl.toDoubleArray(), w = wl.toDoubleArray(), vt = vtl.toDoubleArray(), wt = wtl.toDoubleArray(); 
				double t;
				t = weighted ? WeightedTau.HYPERBOLIC.compute(v, w) : KendallTau.INSTANCE.compute(v, w);
				System.out.print("\t++ " + r.id + ":" + t + " \t" + vl.size());
				t = weighted ? WeightedTau.HYPERBOLIC.compute(v, wt) : KendallTau.INSTANCE.compute(v, wt);
				System.out.print("\t+- " + r.id + ":" + t + " \t" + vl.size());
				t = weighted ? WeightedTau.HYPERBOLIC.compute(vt, wt) : KendallTau.INSTANCE.compute(vt, wt);
				System.out.print("\t-- " + r.id + ":" + t + " \t" + vl.size());
				t = weighted ? WeightedTau.HYPERBOLIC.compute(vt, w) : KendallTau.INSTANCE.compute(vt, w);
				System.out.println("\t-+ " + r.id + ":" + t + " \t" + vl.size());
			}
	
			LOGGER.info("Nodes in deps: " + nodesInDeps);
			LOGGER.info("Nodes in stitched graph: " + stitchedGraph.numNodes());
		}
	}
}