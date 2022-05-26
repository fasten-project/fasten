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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.LongPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jooq.conf.ParseUnknownFunctions;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.search.SearchEngine.PathResult;
import eu.fasten.core.search.SearchEngine.Result;
import eu.fasten.core.search.TopKProcessor.Update;
import eu.fasten.core.search.predicate.PredicateFactory.MetadataSource;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.io.TextIO;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * A client offering a command-line interface over an
 * instance of {@link SearchEngine}. Please use the command-line help for more information.
 */

public class SearchEngineClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(SearchEngineClient.class);
	private static final int DEFAULT_LIMIT = 10;

	/** The regular expression for commands. */
	private static final Pattern COMMAND_REGEXP = Pattern.compile("\\$\\s*(.*)\\s*");
	
	/** The maximum number of results that should be printed. */
	private int limit = DEFAULT_LIMIT;

	/** Maximum number of dependents. */
	private int maxDependents = Integer.MAX_VALUE;
	
	/** Whether to use {@link FastenJavaURI#toSimpleString()} or not when displaying results. */
	private boolean prettyPrint = true;
		
	/**
	 * The filters whose conjunction will be applied by default when executing a query, unless otherwise
	 * specified (compare, e.g., {@link #fromCallable(long)} and
	 * {@link #fromCallable(long, LongPredicate)}).
	 */

	private final ObjectArrayList<LongPredicate> predicateFilters = new ObjectArrayList<>();
	/**
	 * A list parallel to {@link #predicateFilters} that contains the filter specs (readable format of
	 * the filters).
	 */
	private final ObjectArrayList<String> predicateFiltersSpec = new ObjectArrayList<>();

	/** The search engine used by this client. */
	private SearchEngine se;

	/** The id to be assigned to the next first query. */
	private int nextFutureId;
	
	/** A map from ids to futures. */
	private final Int2ObjectOpenHashMap<Future<Void>> id2Future = new Int2ObjectOpenHashMap<>();

	/** A map from ids to queries. */
	private final Int2ObjectOpenHashMap<String> id2Query = new Int2ObjectOpenHashMap<>();

	/** A map from ids to subscriber. */
	private final Int2ObjectOpenHashMap<WaitOnTerminateFutureSubscriber<?>> id2Subscriber = new Int2ObjectOpenHashMap<>();
	
	/** Creates a client for a given search engine.
	 * 
	 * @param se the search engine that this client uses.
	 */
	public SearchEngineClient(final SearchEngine se) {
		this.se = se;
	}
	
	/** Delegate to {@link SearchEngine}: {@see SearchEngine#throwables}. */
	private List<Throwable> throwables() {
		return se.throwables;
	}
	
	/** Returns the GID corresponding to the given URI, in the context of the {@link SearchEngine} we are using.
	 * 
	 * @param uri the URI.
	 * @return the GID.
	 * @throws SQLException
	 */
	private long getCallableGID(FastenJavaURI uri) throws SQLException {
		return Util.getCallableGID(uri, se.context());
	}

	/** Delegate to {@link SearchEngine}: {@see SearchEngine#fromCallable(long, LongPredicate, int, SubmissionPublisher)}. 
	 * @param filter */
	private Future<Void> fromCallable(final long gid, LongPredicate filter, final int limit, final int maxDependents, final SubmissionPublisher<SortedSet<Result>> publisher) throws RocksDBException {
		return se.fromCallable(gid, filter, limit, maxDependents, publisher);	
	}

	/** Delegate to {@link SearchEngine}: {@see SearchEngine#toCallable(long, LongPredicate, int, SubmissionPublisher)}. 
	 * @param filter */
	private Future<Void> toCallable(final long gid, LongPredicate filter, final int limit, final int maxDependents, final SubmissionPublisher<SortedSet<Result>> publisher) throws RocksDBException {
		return se.toCallable(gid, filter, limit, maxDependents, publisher);
	}


	/** Delegate to {@link SearchEngine}: {@see SearchEngine#toCallable(long, LongPredicate, int, SubmissionPublisher)}. 
	 * @param filter */
	private Future<Void> toRevision(final FastenJavaURI uri, LongPredicate filter, final int limit, final int maxDependents, final SubmissionPublisher<SortedSet<Result>> publisher) throws RocksDBException {
		return se.toRevision(uri, filter, limit, maxDependents, publisher);
	}

	/** Delegate to {@link SearchEngine}: {@see SearchEngine#fromRevision(long, LongPredicate, int, SubmissionPublisher)}. 
	 * @param filter */
	private Future<Void> fromRevision(final FastenJavaURI uri, LongPredicate filter, final int limit, final int maxDependents, final SubmissionPublisher<SortedSet<Result>> publisher) throws RocksDBException {
		return se.fromRevision(uri, filter, limit, maxDependents, publisher);
	}

	/** Delegate to {@link SearchEngine}: {@see SearchEngine#between(long, long, int, SubmissionPublisher)}. */
	private Future<Void> between(final long gidFrom, final long gidTo, final int limit, final LongPredicate filter, final int maxDependents,
			SubmissionPublisher<PathResult> publisher) throws RocksDBException {
		return se.between(gidFrom, gidTo, filter, maxDependents, publisher);
	}

	/** Delegate to {@link SearchEngine}: {@see SearchEngine#resetCounters()}. */
	private void resetCounters() {
		se.resetCounters();
	}

	
	/** Checks that <code>min<=n<=max</code>; if not true, raises an IllegalArgumentException.
	 * 
	 * @param n the value to check.
	 * @param min the minimum allowed threshold.
	 * @param max the maximum allowed threshold.
	 */
	private static void assertNargs(final int n, final int min, final int max) {
		if (n < min || max < n) throw new IllegalArgumentException("Wrong number of arguments to command");
	}
	
	/** Prints the intermediate or final result of a query. The actual outcome depends on whether the result is
	 *  a {@link PathResult[]}, an {@link Update} or an instance of some other type, and whether we are pretty-printing
	 *  or not.
	 * 
	 * @param o the intermediate or final result to print.
	 */
	private void printResult(final Object o) {
		if (o instanceof PathResult[]) {
			final var a = (PathResult[])o;
			for(var p : a) {
				if (prettyPrint) {
					for(int i = 0; i < p.size(); i++) {
						if (i != 0) System.out.println(" ->");
						System.out.print(FastenJavaURI.create(Util.getCallableName(p.getLong(i), se.context()).toString()).toSimpleString());
					}
					System.out.println("\n[dependent " + p.dependent.groupId + ":" + p.dependent.artifactId + ":" + p.dependent.version.toString() + "]");
					System.out.println();
				}
				else System.out.println(p);
				System.out.println();
			}
		}
		else if (o instanceof Update) {
			if (prettyPrint) {
				final var u = ((Update)o).current;
				for(var r : u) System.out.println(FastenJavaURI.create(Util.getCallableName(r.gid, se.context()).toString()).toSimpleString() + "\t" + r.score + "\t" + r.dependent.groupId + ":" + r.dependent.artifactId + ":" + r.dependent.version.toString());
			}
			else System.out.println(o);
		} else if (o == null) System.out.println("No result");
		else System.out.println(o);
	}
	
	/**
	 * Executes a given command.
	 *
	 * @param command the command (including its space-separated arguments, if any).
	 */
	private void executeCommand(final String command) throws InterruptedException, ExecutionException, SQLException {
		final String[] commandAndArgs = command.split("\\s"); // Split command on whitespace
		final String help = 
				"\n\tGENERAL COMMANDS\n" +
				"\t$help                           Help on commands\n" + 
				"\t$limit <LIMIT>                  Print at most <LIMIT> results (-1 for infinity)\n" + 
				"\t$maxDependents <LIMIT>          Maximum number of dependents considered in coreachable query resolution (-1 for infinity)\n" + 
				"\t$time                           Time statistics about the queries issued so far\n" +
				"\t$reset                          Reset time statistics\n" +
				"\t$pretty                         Toggle pretty printing\n" +
				"\n\tFILTER-RELATED COMMANDS\n" +
				"\t$f ?                            Print the current filter\n" + 
				"\t$f pmatches <REGEXP>            Add filter: package (a.k.a. product) matches <REGEXP>\n" + 
				"\t$f vmatches <REGEXP>            Add filter: version matches <REGEXP>\n" + 
				"\t$f xmatches <REGEXP>            Add filter: path (namespace + entity) matches <REGEXP>\n" + 
				"\t$f cmd <KEY> [<REGEXP>]         Add filter: callable metadata contains key <KEY> (satisfying <REGEXP>)\n" + 
				"\t$f mmd <KEY> [<REGEXP>]         Add filter: module metadata contains key <KEY> (satisfying <REGEXP>)\n" + 
				"\t$f pmd <KEY> [<REGEXP>]         Add filter: package+version metadata contains key <KEY> (satisfying <REGEXP>)\n" + 
				"\t$f cmdjp <JP> <REGEXP>          Add filter: callable metadata queried with the JSONPointer <JP> has a value satisfying <REGEXP>\n" + 
				"\t$f mmdjp <JP> <REGEXP>          Add filter: module metadata queried with the JSONPointer <JP> has a value satisfying <REGEXP>\n" + 
				"\t$f pmdjp <JP> <REGEXP>          Add filter: package+version metadata queried with the JSONPointer <JP> has a value satisfying <REGEXP>\n" + 
				"\t$or                             The last two filters are substituted by their disjunction (or)\n" + 
				"\t$and                            The last two filters are substituted by their conjunction (and)\n" + 
				"\t$not                            The last filter is substituted by its negation (not)\n" + 
				"\t$test <URI>                     Test the provided URI against the current filter\n" + 
				"\t$clear                          Clear filters\n" + 
				"\n\tQUERY-RELATED COMMANDS\n" +
				"\t±<URI>                          Issue a new query to find reachable (+) or coreachable (-) callables from the given callable <URI> satisfying all filters\n" + 
				"\t*<URI> <URI>                    Issue a new query to find a path connecting two callables\n" + 
				"\t$show                           List the running queries with their IDs\n" +
				"\t$inspect [<ID>]                 Show the current results of the last query (or query with given ID), without stopping it\n" +
				"\t$wait [<ID>]                    Wait until the last query (or query with given ID) is completed and show its results\n" +
				"\t$cancel [<ID>]                  Cancel the last query (or query with given ID) and stops all the related threads (this may take some time)\n" +
				"";
		try {
			final String verb = commandAndArgs[0].toLowerCase();
			final int nArgs = commandAndArgs.length - 1;
			final Int2IntArrayMap index2arg = new Int2IntArrayMap();
			for (int i = 0; i < nArgs; i++) 
				try {
					index2arg.put(i, Integer.parseInt(commandAndArgs[i + 1]));
				} catch (NumberFormatException e) {}
			
			switch (verb) {

			case "help":
				assertNargs(nArgs, 0, 0);
				System.err.println(help);
				break;

			case "limit":
				assertNargs(nArgs, 1, 1);
				limit = index2arg.get(0);
				if (limit < 0) limit = Integer.MAX_VALUE;
				break;

			case "maxdependents":
				assertNargs(nArgs, 1, 1);
				maxDependents = index2arg.get(0);
				if (maxDependents < 0) maxDependents = Integer.MAX_VALUE;
				break;

			case "clear":
				assertNargs(nArgs, 0, 0);
				predicateFilters.clear();
				predicateFiltersSpec.clear();
				break;

			case "show":
				assertNargs(nArgs, 0, 0);
				for (int i = 0; i < nextFutureId; i++)
					if (id2Query.containsKey(i))
						System.out.printf("%3d\t%s\t%s\n", Integer.valueOf(i), id2Future.get(i).isDone()? "Completed" : "Running", id2Query.get(i));
				break;

			case "pretty":
				System.out.println("Pretty printing is " + ((prettyPrint ^= true) ? "on" : "off"));
				break;

			case "wait":
			case "cancel":
				assertNargs(nArgs, 0, 1);
				final int wcid = nArgs == 1? (int)index2arg.get(0) : nextFutureId - 1;
				Future<Void> future = id2Future.get(wcid);
				if (future == null) System.err.println("No such search ID");
				else {
					if ("wait".equals(verb)) {
						try {
							final var o = id2Subscriber.get(wcid).get();
							printResult(o);
						} catch (InterruptedException e) {
							System.err.println("Interrupted!");
							break;
						}
					} else {
						future.cancel(true);
						id2Future.remove(wcid);
						id2Subscriber.remove(wcid);
						id2Query.remove(wcid);
					}

				}
				break;

			case "inspect":
				assertNargs(nArgs, 0, 1);
				final int insid = nArgs == 1? (int)index2arg.get(0) : nextFutureId - 1;
				final var subscriber = id2Subscriber.get(insid);
				if (subscriber == null) System.err.println("No such search ID");
				else {
					System.out.printf("%3d\t%s\n", Integer.valueOf(insid), id2Future.get(insid).isDone()? "Completed" : "Running");
					final var rr = subscriber.last() == null? new PathResult[0] : subscriber.last();
					printResult(rr);
				}
				break;

				
			case "reset":
				resetCounters();
				break;
				
			case "time":
				assertNargs(nArgs, 0, 0);
				System.err.printf("\n%,d arcs %,.3f arcs/s\nResolve time: %,.3fs  Merge time: %,.3fs  Visit time %,.3fs\n",
						Long.valueOf(se.visitedArcs.get()),
						Double.valueOf(1E9 * se.visitedArcs.get() / se.visitTime.get()),
						Double.valueOf(se.resolveTime.get() * 1E-9),
						Double.valueOf(se.mergeTime.get() * 1E-9),
						Double.valueOf(se.visitTime.get() * 1E-9));
				break;
				
			case "f":
				assertNargs(nArgs, 1, 3);
				LongPredicate predicate = null;
				Pattern regExp;
				MetadataSource mds;
				switch (commandAndArgs[1].toLowerCase()) {
				case "pmatches":
					regExp = Pattern.compile(commandAndArgs[2]);
					predicate = se.predicateFactory().fastenURIMatches(uri -> matchRegexp(uri.getProduct(), regExp));
					break;
				case "vmatches":
					regExp = Pattern.compile(commandAndArgs[2]);
					predicate = se.predicateFactory().fastenURIMatches(uri -> matchRegexp(uri.getVersion(), regExp));
					break;
				case "xmatches":
					regExp = Pattern.compile(commandAndArgs[2]);
					predicate = se.predicateFactory().fastenURIMatches(uri -> matchRegexp(uri.getPath(), regExp));
					break;
				case "cmd":
				case "mmd":
				case "pmd":
					final String key = commandAndArgs[2];
					mds = null;
					switch (commandAndArgs[1].toLowerCase().charAt(0)) {
					case 'c':
						mds = MetadataSource.CALLABLE;
						break;
					case 'm':
						mds = MetadataSource.MODULE;
						break;
					case 'p':
						mds = MetadataSource.PACKAGE_VERSION;
						break;
					default:
						throw new RuntimeException("Cannot happen");
					}
					if (commandAndArgs.length == 3) predicate = se.predicateFactory().metadataContains(mds, key);
					else {
						regExp = Pattern.compile(commandAndArgs[3]);
						predicate = se.predicateFactory().metadataContains(mds, key, s -> matchRegexp(s, regExp));
					}
					break;
				case "cmdjp":
				case "mmdjp":
				case "pmdjp":
					final String jsonPointer = commandAndArgs[2];
					mds = null;
					switch (commandAndArgs[1].toLowerCase().charAt(0)) {
					case 'c':
						mds = MetadataSource.CALLABLE;
						break;
					case 'm':
						mds = MetadataSource.MODULE;
						break;
					case 'p':
						mds = MetadataSource.PACKAGE_VERSION;
						break;
					default:
						throw new RuntimeException("Cannot happen");
					}
					regExp = Pattern.compile(commandAndArgs[3]);
					predicate = se.predicateFactory().metadataQueryJSONPointer(mds, jsonPointer, s -> matchRegexp(s, regExp));
					break;
				case "?":
					System.err.println(String.join(" && ", predicateFiltersSpec));
					break;
				default:
					throw new RuntimeException("Unknown type of predicate " + commandAndArgs[1]);
				}
				if (predicate != null) {
					predicateFilters.push(predicate);
					predicateFiltersSpec.push(String.join(" ", Arrays.copyOfRange(commandAndArgs, 1, commandAndArgs.length)));
				}
				break;

			case "and":
			case "or":
				assertNargs(nArgs, 0, 0);
				if (predicateFilters.size() < 2) throw new RuntimeException("At least two predicates must be present");
				if ("and".equals(verb.toLowerCase())) {
					predicateFilters.push(predicateFilters.pop().and(predicateFilters.pop()));
					predicateFiltersSpec.push("(" + predicateFiltersSpec.pop() + " && " + predicateFiltersSpec.pop() + ")");
				} else {
					predicateFilters.push(predicateFilters.pop().or(predicateFilters.pop()));
					predicateFiltersSpec.push("(" + predicateFiltersSpec.pop() + " || " + predicateFiltersSpec.pop() + ")");
				}
				break;

			case "not":
				assertNargs(nArgs, 0, 0);
				if (predicateFilters.size() < 1) throw new RuntimeException("At least one predicates must be present");
				predicateFilters.push(predicateFilters.pop().negate());
				predicateFiltersSpec.push("!(" + predicateFiltersSpec.pop() + ")");
				break;

			case "test":
				assertNargs(nArgs, 1, 1);
				if (predicateFilters.size() < 1) throw new RuntimeException("At least one predicates must be present");
				final String uri = commandAndArgs[1];
				final long gid = getCallableGID(FastenJavaURI.create(uri));
				if (gid == -1) System.err.println("Unknown URI " + uri);
				else System.out.println(predicateFilters.stream().reduce(x -> true, LongPredicate::and).test(gid));
				break;

			default:
				System.err.println("Unknown command " + command);
			}

		} catch (final RuntimeException e) {
			System.err.println("Exception while executing command " + command);
			e.printStackTrace(System.err);
			System.err.println(help);
		}
	}

	/**
	 * Returns true if the given string fully matches the given regular expression (i.e., it matches it
	 * from start to end).
	 *
	 * @param s the string.
	 * @param regExp the regular expression.
	 * @return true iff s is not null and it matches the regular expression from start to end.
	 */
	private static boolean matchRegexp(final String s, final Pattern regExp) {
		if (s == null) return false;
		final Matcher matcher = regExp.matcher(s);
		return matcher.matches() && matcher.start() == 0 && matcher.end() == s.length();
	}


	public static void main(final String args[]) throws Exception {
		final SimpleJSAP jsap = new SimpleJSAP(SearchEngineClient.class.getName(), "Creates an instance of SearchEngine and answers queries from the command line (rlwrap recommended).", new Parameter[] {
				new FlaggedOption("blacklist", JSAP.STRING_PARSER, null, JSAP.NOT_REQUIRED, 'b', "blacklist", "A blacklist of GIDs of revision call graphs that should be considered as missing."),
				new UnflaggedOption("jdbcURI", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The JDBC URI."),
				new UnflaggedOption("database", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The database name."),
				new UnflaggedOption("rocksDb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The path to the RocksDB database of revision call graphs."),
				new UnflaggedOption("cache", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The RocksDB cache."),
				new UnflaggedOption("resolverGraph", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The path to a resolver graph (will be created if it does not exist)."), });

		System.getProperties().setProperty("org.jooq.no-logo", "true");
		System.getProperties().setProperty("org.jooq.no-tips", "true");

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);

		final String jdbcURI = jsapResult.getString("jdbcURI");
		final String database = jsapResult.getString("database");
		final String rocksDb = jsapResult.getString("rocksDb");
		final String cacheDir = jsapResult.getString("cache");
		final String resolverGraph = jsapResult.getString("resolverGraph");
		final LongOpenHashSet blacklist = new LongOpenHashSet();
		if (jsapResult.userSpecified("blacklist")) TextIO.asLongIterator(new BufferedReader(new InputStreamReader(new FileInputStream(jsapResult.getString("blacklist")), StandardCharsets.US_ASCII))).forEachRemaining(x -> blacklist.add(x));

		final SearchEngine searchEngine = new SearchEngine(jdbcURI, database, rocksDb, cacheDir, resolverGraph, null, blacklist);
		searchEngine.context().settings().withParseUnknownFunctions(ParseUnknownFunctions.IGNORE);
		final SearchEngineClient client = new SearchEngineClient(searchEngine);

		@SuppressWarnings("resource")
		final Scanner scanner = new Scanner(System.in);
		for (;;) {
			System.out.print("[$help for help]>");
			System.out.flush();
			if (!scanner.hasNextLine()) break;
			String line = scanner.nextLine();
			if (line.length() == 0) continue;
			final Matcher matcher = COMMAND_REGEXP.matcher(line);
			if (matcher.matches()) {
				client.executeCommand(matcher.group(1));
				continue;
			}
			try {
				final char dir = line.charAt(0);
				if (dir != '+' && dir != '-' && dir != '*') {
					if (dir != '#') System.err.println("First character must be '+', '-', '*' or '#'");
					continue;
				}
				if (dir == '+' || dir == '-') {
					final FastenJavaURI uri = FastenJavaURI.create(line.substring(1));

					SubmissionPublisher<SortedSet<Result>> publisher = new SubmissionPublisher<>();
					TopKProcessor topKProcessor = new TopKProcessor(client.limit, searchEngine);
					publisher.subscribe(topKProcessor);

					final WaitOnTerminateFutureSubscriber<Update> futureSubscriber = new WaitOnTerminateFutureSubscriber<>();

					topKProcessor.subscribe(futureSubscriber);
					final int id = client.nextFutureId;

					LongPredicate filter = client.predicateFilters.stream().reduce(x -> true, LongPredicate::and);
					if (uri.getPath() == null) 
						client.id2Future.put(id, dir == '+'?
								client.fromRevision(uri, filter, client.limit, client.maxDependents, publisher) :
								client.toRevision(uri, filter, client.limit, client.maxDependents, publisher));

					else {
						final long gid = client.getCallableGID(uri);
						if (gid == -1) {
							System.err.println("Unknown URI " + uri);
							continue;
						}
						client.id2Future.put(id, dir == '+'? 
								client.fromCallable(gid, filter, client.limit,client.maxDependents,  publisher) :
								client.toCallable(gid, filter, client.limit, client.maxDependents, publisher));						
					}
					client.id2Subscriber.put(id, futureSubscriber);
					client.id2Query.put(id, line);
					System.err.println("Id: " + id);
					client.nextFutureId++;

				} else {  // dir == '*'
					String[] uris = line.substring(1).split("\\s+");
					if (uris.length != 2) {
						System.err.println("Exactly two callable URIs must be provided!");
						continue;
					}
					final FastenJavaURI uriFrom = FastenJavaURI.create(uris[0]);
					if (uriFrom == null || uriFrom.getPath() == null) {
						System.err.println("Invalid URI (or not a callable URI): " + uriFrom);
						continue;
					}
					final long gidFrom = client.getCallableGID(uriFrom);
					if (gidFrom == -1) {
						System.err.println("Unknown URI: " + uriFrom);
						continue;
					}
					final FastenJavaURI uriTo = FastenJavaURI.create(uris[1]);
					if (uriTo == null || uriTo.getPath() == null) {
						System.err.println("Invalid URI (or not a callable URI): " + uriTo);
						continue;
					}
					final long gidTo = client.getCallableGID(uriTo);
					if (gidTo == -1) {
						System.err.println("Unknown URI: " + uriTo);
						continue;
					}
					
					SubmissionPublisher<PathResult> publisher = new SubmissionPublisher<>();
					ShortestKProcessor shortestKProcessor = new ShortestKProcessor(client.limit);
					publisher.subscribe(shortestKProcessor);
					final WaitOnTerminateFutureSubscriber<PathResult[]> futureSubscriber = new WaitOnTerminateFutureSubscriber<>();
					shortestKProcessor.subscribe(futureSubscriber);

					LongPredicate filter = client.predicateFilters.stream().reduce(x -> true, LongPredicate::and);

					final int id = client.nextFutureId;
					client.id2Future.put(id, client.between(gidFrom, gidTo, client.limit, filter, client.maxDependents, publisher));
					client.id2Subscriber.put(id, futureSubscriber);
					client.id2Query.put(id, line);
					System.err.println("Id: " + id);
					client.nextFutureId++;
				}

				for (final var t : client.throwables()) {
					System.err.println(t);
					System.err.println("\t" + t.getStackTrace()[0]);
				}
			} catch (final Exception e) {
				e.printStackTrace();
			} finally {}
		}
	}




}