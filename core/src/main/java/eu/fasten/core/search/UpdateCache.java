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

import java.util.Set;

import org.apache.commons.lang.mutable.MutableInt;
import org.apache.commons.lang3.SerializationUtils;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Longs;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
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
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.logging.ProgressLogger;

public class UpdateCache {
	private static final Logger LOGGER = LoggerFactory.getLogger(UpdateCache.class);

	/** The handle to the Postgres metadata database. */
	private final DSLContext context;
	/** The handle to the RocksDB DAO. */
	private final RocksDao rocksDao;
	/** The resolver. */
	private final GraphMavenResolver resolver;
	
	public UpdateCache(final String jdbcURI, final String database, final String rocksDb, final String resolverGraph) throws Exception {
		this(PostgresConnector.getDSLContext(jdbcURI, database, false), new RocksDao(rocksDb, true), resolverGraph);
	}

	public UpdateCache(final DSLContext context, final RocksDao rocksDao, final String resolverGraph) throws Exception {
		this.context = context;
		this.rocksDao = rocksDao;
		resolver = new GraphMavenResolver();
		resolver.buildDependencyGraph(context, resolverGraph);
		resolver.setIgnoreMissing(true);
		new CachingPredicateFactory(context);
	}

	public static void main(final String args[]) throws Exception {
		final SimpleJSAP jsap = new SimpleJSAP(UpdateCache.class.getName(), "Updates the cache.", new Parameter[] {
				new UnflaggedOption("rocksDb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The path to the RocksDB database of revision call graphs."),
				new UnflaggedOption("cache", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The RocksDB cache."),
				new UnflaggedOption("jdbcURI", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The JDBC URI."),
				new UnflaggedOption("database", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The database name."),
				new UnflaggedOption("resolverGraph", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The path to a resolver graph (will be created if it does not exist)."), });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);

		final String jdbcURI = jsapResult.getString("jdbcURI");
		final String database = jsapResult.getString("database");
		final String rocksDb = jsapResult.getString("rocksDb");
		final String cacheDir = jsapResult.getString("cache");
		final String resolverGraph = jsapResult.getString("resolverGraph");

		final RocksDBData cacheData = SearchEngine.openCache(cacheDir, false);
		var cache = cacheData.cache;
		var mergedHandle = cacheData.columnFamilyHandles.get(0);
		var dependenciesHandle = cacheData.columnFamilyHandles.get(1);
		
		final UpdateCache update = new UpdateCache(jdbcURI, database, rocksDb, resolverGraph);
		final DSLContext context = update.context;

		ProgressLogger pl = new ProgressLogger();
		for(byte[] key: update.rocksDao) {
			pl.update();
			final long gid = Longs.fromByteArray(key);
			final var graph = update.rocksDao.getGraphData(gid);
			// No graph, we don't save
			if (graph == null) continue;

			final Record2<String, String> record = context.select(Packages.PACKAGES.PACKAGE_NAME, PackageVersions.PACKAGE_VERSIONS.VERSION).from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES).on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID)).where(PackageVersions.PACKAGE_VERSIONS.ID.eq(Long.valueOf(gid))).fetchOne();
			if (record == null) {
				LOGGER.warn("Postgres metaData for GID " + gid + " not found");
				// No metadata, we don't save
				continue;
			}
			
			final String[] a = record.component1().split(":");
			final String groupId = a[0];
			final String artifactId = a[1];
			final String version = record.component2();
			final String name = groupId + ":" + artifactId + "$" + version;

			if (update.rocksDao.getGraphMetadata(gid, graph) == null) {
				// No graph metadata, we don't save
				LOGGER.warn("No graph metadata for gid " + gid + " (" + name + ")");
			}
		
			final Set<Revision> dependencySet = update.resolver.resolveDependencies(groupId, artifactId, version, -1, context, true);
			var dependencyIds = LongLinkedOpenHashSet.toSet(dependencySet.stream().mapToLong(x -> x.id));
			dependencyIds.addAndMoveToFirst(gid);

			MutableInt c = new MutableInt();
			dependencyIds.forEach(depId -> {
				try {
					if (update.rocksDao.getGraphData(c.longValue()) != null && update.rocksDao.getGraphData(c.longValue()) != null) c.increment();
				} catch (RocksDBException e) {
					throw new RuntimeException(e);
				}
			});

			if (c.doubleValue() / dependencyIds.size() < 0.9) {
				LOGGER.warn("Not enough dependency graphs for gid " + gid + " (" + name + ")");
				continue;
			}
			
			final DirectedGraph stitchedGraph;

			final var dm = new CGMerger(dependencyIds, context, update.rocksDao);
			stitchedGraph = dm.mergeAllDeps();

			// Problems with merging, we don't save
			if (stitchedGraph == null) {
				LOGGER.warn("CGMerger not working for gid " + gid + " (" + name + ")");
				continue;
			}
			
			cache.put(dependenciesHandle, key, SerializationUtils.serialize(dependencyIds));
			cache.put(mergedHandle, key, SerializationUtils.serialize(ArrayImmutableDirectedGraph.copyOf(stitchedGraph, false)));
		}
		
		pl.done();
	}
}