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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.function.LongPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.fasten.core.merge.CGMerger;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.conf.ParseUnknownFunctions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Longs;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.search.predicate.CachingPredicateFactory;
import eu.fasten.core.search.predicate.PredicateFactory;
import eu.fasten.core.search.predicate.PredicateFactory.MetadataSource;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.lang.ObjectParser;

public class GraphDepStats {
	private static final Logger LOGGER = LoggerFactory.getLogger(GraphDepStats.class);

	/** The handle to the Postgres metadata database. */
	private final DSLContext context;
	/** The handle to the RocksDB DAO. */
	private final RocksDao rocksDao;
	/** The resolver. */
	private final GraphMavenResolver resolver;
	/** The scorer that will be used to rank results. */
	private final Scorer scorer;

	public GraphDepStats(final String jdbcURI, final String database, final String rocksDb, final String resolverGraph, final String scorer) throws Exception {
		this(PostgresConnector.getDSLContext(jdbcURI, database, false), new RocksDao(rocksDb, true), resolverGraph, scorer == null ? TrivialScorer.getInstance() : ObjectParser.fromSpec(scorer, Scorer.class));
	}

	public GraphDepStats(final DSLContext context, final RocksDao rocksDao, final String resolverGraph, final Scorer scorer) throws Exception {
		this.context = context;
		this.rocksDao = rocksDao;
		this.scorer = scorer == null ? TrivialScorer.getInstance() : scorer;
		resolver = new GraphMavenResolver();
		resolver.buildDependencyGraph(context, resolverGraph);
		resolver.setIgnoreMissing(true);
		new CachingPredicateFactory(context);
	}


	public long[] from(final long rev) throws RocksDBException {
		final var graph = rocksDao.getGraphData(rev);
		if (graph == null) throw new NoSuchElementException("Revision associated with callable missing from the graph database");
		
		final Record2<String, String> record = context.select(Packages.PACKAGES.PACKAGE_NAME, PackageVersions.PACKAGE_VERSIONS.VERSION).from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES).on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID)).where(PackageVersions.PACKAGE_VERSIONS.ID.eq(Long.valueOf(rev))).fetchOne();
		final String[] a = record.component1().split(":");
		final String groupId = a[0];
		final String artifactId = a[1];
		final String version = record.component2();
		final Set<Revision> dependencySet = resolver.resolveDependencies(groupId, artifactId, version, -1, context, true);

		long c = 0;
		for(Revision r: dependencySet) if (rocksDao.getGraphData(r.id) != null) c++;
	
		return new long[] { c, dependencySet.size() };
	}

	public static void main(final String args[]) throws Exception {
		final SimpleJSAP jsap = new SimpleJSAP(GraphDepStats.class.getName(), "Creates an instance of SearchEngine and answers queries from the command line (rlwrap recommended).", new Parameter[] {
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

		final GraphDepStats graphDepStats = new GraphDepStats(jdbcURI, database, rocksDb, resolverGraph, null);
		final DSLContext context = graphDepStats.context;

		RocksDB graphDb = RocksDB.openReadOnly(rocksDb);
		RocksIterator iterator = graphDb.newIterator();
		iterator.seekToFirst();
		for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
			long gid = Longs.fromByteArray(iterator.key());
			long[] from = graphDepStats.from(gid);
			System.out.println(gid + "\t" + from[0] + "\t" + from[1] + "\t" + 100. * from[0] / from[1]);
		}
	}

}