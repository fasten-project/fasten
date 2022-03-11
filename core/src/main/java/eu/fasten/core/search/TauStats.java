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

import org.apache.commons.lang3.SerializationUtils;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Longs;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.Centralities;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.callableindex.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.CGMerger;
import eu.fasten.core.search.SearchEngine.RocksDBData;
import eu.fasten.core.search.predicate.CachingPredicateFactory;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.longs.Long2DoubleFunction;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.lang.EnumStringParser;
import it.unimi.dsi.law.stat.KendallTau;
import it.unimi.dsi.law.stat.WeightedTau;

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

	public TauStats(final String rocksDb) throws Exception {
		this.context = null;
		this.rocksDao = new RocksDao(rocksDb, true);
		resolver = null;
	}

	public enum Centrality {
		DEGREE, PAGERANK, HARMONIC
	}

	public static void main(final String args[]) throws Exception {
		final SimpleJSAP jsap = new SimpleJSAP(TauStats.class.getName(), "Creates an instance of SearchEngine and answers queries from the command line (rlwrap recommended).", new Parameter[] {
				new Switch("weighted", 'w', "weighted", "Use the hyperbolic weighted tau."),
				new UnflaggedOption("centrality", EnumStringParser.getParser(Centrality.class, true), JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The centrality (one of " + java.util.Arrays.toString(Centrality.values()) + ")."),
				new UnflaggedOption("rocksDb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The path to the RocksDB database of revision call graphs."),
				new UnflaggedOption("cache", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The RocksDB cache."),
				new UnflaggedOption("jdbcURI", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The JDBC URI."),
				new UnflaggedOption("database", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The database name."),
				new UnflaggedOption("resolverGraph", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The path to a resolver graph (will be created if it does not exist)."), });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);

		final boolean weighted = jsapResult.getBoolean("weighted");
		final Centrality centrality = (Centrality)jsapResult.getObject("centrality");
		final String jdbcURI = jsapResult.getString("jdbcURI");
		final String database = jsapResult.getString("database");
		final String rocksDb = jsapResult.getString("rocksDb");
		final String cacheDir = jsapResult.getString("cache");
		final String resolverGraph = jsapResult.getString("resolverGraph");

		RocksDBData cacheData = SearchEngine.openCache(cacheDir);
		var cache = cacheData.cache;
		var mergedHandle = cacheData.columnFamilyHandles.get(0);
		var dependenciesHandle = cacheData.columnFamilyHandles.get(1);
		
		final TauStats tauStats = database != null ? new TauStats(jdbcURI, database, rocksDb, resolverGraph) : new TauStats(rocksDb);
		final DSLContext context = tauStats.context;

		@SuppressWarnings("resource")
		Scanner scanner = new Scanner(System.in);
		while(scanner.hasNextLong()) {
			long gid = scanner.nextLong();
			final var graph = tauStats.rocksDao.getGraphData(gid);
			if (graph == null) continue;
		
			final Record2<String, String> record = context.select(Packages.PACKAGES.PACKAGE_NAME, PackageVersions.PACKAGE_VERSIONS.VERSION).from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES).on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID)).where(PackageVersions.PACKAGE_VERSIONS.ID.eq(Long.valueOf(gid))).fetchOne();
			if (record == null) {
				LOGGER.warn("MetaData for GID " + gid + " not found");
				continue;
			}
			final String[] a = record.component1().split(":");
			final String groupId = a[0];
			final String artifactId = a[1];
			final String version = record.component2();

			final byte[] gidAsByteArray = Longs.toByteArray(gid);
			final byte[] depFromCache  = cache.get(dependenciesHandle, gidAsByteArray);

			final LongLinkedOpenHashSet dependencyIds; 
			if (depFromCache == null) {
				final Set<Revision> dependencySet = tauStats.resolver.resolveDependencies(groupId, artifactId, version, -1, context, true);
				dependencyIds = LongLinkedOpenHashSet.toSet(dependencySet.stream().mapToLong(x -> x.id));
				dependencyIds.addAndMoveToFirst(gid);
				cache.put(dependenciesHandle, gidAsByteArray, SerializationUtils.serialize(dependencyIds));
			}
			else dependencyIds = SerializationUtils.deserialize(depFromCache);

			final DirectedGraph stitchedGraph;
			final String name = groupId + ":" + artifactId + "$" + version;

			byte[] stitchedFromCache = cache.get(mergedHandle, gidAsByteArray);
			if (stitchedFromCache != null) {
				if (stitchedFromCache.length != 0) stitchedGraph = ArrayImmutableDirectedGraph.copyOf(SerializationUtils.deserialize(stitchedFromCache), false);
				else {
					LOGGER.warn("Cached returned null on id " + gid);
					continue;
				}
			}
			else {
				LOGGER.info("Analyzing graph " + name  + " with id " + gid);
				LOGGER.info("Dependencies: " + dependencyIds);

				final var dm = new CGMerger(dependencyIds, context, tauStats.rocksDao);
				stitchedGraph = ArrayImmutableDirectedGraph.copyOf(dm.mergeAllDeps(), false);
				
				if (stitchedGraph == null) throw new NullPointerException("mergeWithCHA() returned null");
				else cache.put(mergedHandle, gidAsByteArray, SerializationUtils.serialize((ArrayImmutableDirectedGraph)stitchedGraph));

			}
			
			Long2DoubleFunction globalRankForward = null;
			Long2DoubleFunction globalRankBackward = null;
			switch(centrality) {
			case PAGERANK:
				globalRankForward = Centralities.pageRankParallel(stitchedGraph.transpose(), 0.85);
				globalRankBackward = Centralities.pageRankParallel(stitchedGraph, 0.85);
				break;
			case HARMONIC:
				globalRankForward = Centralities.harmonicApproximateParallel(stitchedGraph.transpose(), 1E-2);
				globalRankBackward = Centralities.harmonicApproximateParallel(stitchedGraph, 1E-2);
				break;
			}
			
			System.out.println(gid + "\t" + name);
			
			for(long r: dependencyIds) {
				LOGGER.info("Comparing with graph " + r);
				var depTemp = tauStats.rocksDao.getGraphData(r);
				if (depTemp == null) continue;
				var dep = ArrayImmutableDirectedGraph.copyOf(depTemp, false);
				Long2DoubleFunction localRankBackward = null;
				Long2DoubleFunction localRankForward = null;

				switch(centrality) {
				case PAGERANK:
					localRankForward = Centralities.pageRankParallel(dep.transpose(), 0.85);
					localRankBackward = Centralities.pageRankParallel(dep, 0.85);
					break;
				case HARMONIC:
					localRankForward = Centralities.harmonicExact(dep.transpose());
					localRankBackward = Centralities.harmonicExact(dep);
					break;
				}
				
				DoubleArrayList localForward = new DoubleArrayList(), localBackward = new DoubleArrayList(), globalForward =  new DoubleArrayList(), globalBackward =  new DoubleArrayList();

				for(long x : dep.nodes()) {
					if (stitchedGraph.containsVertex(x)) {
						switch(centrality) {
						case PAGERANK:
						case HARMONIC:
							localForward.add(localRankForward.get(x));
							globalForward.add(globalRankForward.get(x));
							localBackward.add(localRankBackward.get(x));
							globalBackward.add(globalRankBackward.get(x));
							break;
						case DEGREE:
							localForward.add(dep.outdegree(x));
							globalForward.add(stitchedGraph.outdegree(x));
							localBackward.add(dep.indegree(x));
							globalBackward.add(stitchedGraph.indegree(x));
							break;
						}
					}
				}

				double[] lf = localForward.toDoubleArray(), lb = localBackward.toDoubleArray(), gf = globalForward.toDoubleArray(), gb = globalBackward.toDoubleArray(); 
				double t;

				if (lf.length == 0) continue;
				
				t = weighted ? WeightedTau.HYPERBOLIC.compute(lf, gf) : KendallTau.INSTANCE.compute(lf, gf);
				if (Double.isNaN(t)) t = 0;
				System.out.print("\t++ " + r + ":" + t + " \t" + localForward.size());

				t = weighted ? WeightedTau.HYPERBOLIC.compute(lf, gb) : KendallTau.INSTANCE.compute(lf, gb);
				if (Double.isNaN(t)) t = 0;
				System.out.print("\t+- " + r + ":" + t + " \t" + localForward.size());

				t = weighted ? WeightedTau.HYPERBOLIC.compute(lb, gb) : KendallTau.INSTANCE.compute(lb, gb);
				if (Double.isNaN(t)) t = 0;
				System.out.print("\t-- " + r + ":" + t + " \t" + localForward.size());

				t = weighted ? WeightedTau.HYPERBOLIC.compute(lb, gf) : KendallTau.INSTANCE.compute(lb, gf);
				if (Double.isNaN(t)) t = 0;
				System.out.print("\t-+ " + r + ":" + t + " \t" + localForward.size());

				t = weighted ? WeightedTau.HYPERBOLIC.compute(lb, lf) : KendallTau.INSTANCE.compute(lb, lf);
				if (Double.isNaN(t)) t = 0;
				System.out.print("\tl+- " + r + ":" + t + " \t" + localForward.size());

				t = weighted ? WeightedTau.HYPERBOLIC.compute(gb, gf) : KendallTau.INSTANCE.compute(gb, gf);
				if (Double.isNaN(t)) t = 0;
				System.out.print("\tg+- " + r + ":" + t + " \t" + localForward.size());

				System.out.println();
			}
		}
	}
}