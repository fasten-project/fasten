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
import java.util.Arrays;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.LongPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.search.SearchEngine.Result;
import eu.fasten.core.search.TopKProcessor.Update;
import eu.fasten.core.search.predicate.PredicateFactory.MetadataSource;
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
	private static final ArrayImmutableDirectedGraph NO_GRAPH = new ArrayImmutableDirectedGraph.Builder().build();
	private static final int DEFAULT_LIMIT = 10;

	/** The regular expression for commands. */
	private static final Pattern COMMAND_REGEXP = Pattern.compile("\\$\\s*(.*)\\s*");
	
	/** The maximum number of results that should be printed. */
	private int limit = DEFAULT_LIMIT;

	/** Maximum number of dependents used by {@link #to}. */
	private long maxDependents = Long.MAX_VALUE;
		
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
	private final Int2ObjectOpenHashMap<WaitOnTerminateFutureSubscriber<Update>> id2Subscriber = new Int2ObjectOpenHashMap<>();
	
	/** Creates a client for a given search engine.
	 * 
	 * @param se the search engine that this client uses.
	 */
	public SearchEngineClient(final SearchEngine se) {
		this.se = se;
	}
	
	/**
	 * Executes a given command.
	 *
	 * @param command the command (including its space-separated arguments, if any).
	 */
	private void executeCommand(final String command) throws InterruptedException, ExecutionException {
		final String[] commandAndArgs = command.split("\\s"); // Split command on whitespace
		final String help = 
				"\n\tGENERAL COMMANDS\n" +
				"\t$help                           Help on commands\n" + 
				"\t$limit <LIMIT>                  Print at most <LIMIT> results (-1 for infinity)\n" + 
				"\t$maxDependents <LIMIT>          Maximum number of dependents considered in coreachable query resolution (-1 for infinity)\n" + 
				"\t$time                           Time statistics about the queries issued so far\n" +
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
				"\t$clear                          Clear filters\n" + 
				"\n\tQUERY-RELATED COMMANDS\n" +
				"\t±<URI>                          Issue a new query to find reachable (+) or coreachable (-) callables from the given callable <URI> satisfying all filters\n" + 
				"\t$show                            Lists the running queries with their IDs\n" +
				"\t$inspect <ID>                    Show the current results of the query with given ID (without stopping it)\n" +
				"\t$wait <ID>                       Wait until the query with given ID is completed and show its results\n" +
				"\t$cancel <ID>                     Cancels the query with given ID and stops all the related threads (this may take some time)\n" +
				"";
		try {
			final String verb = commandAndArgs[0].toLowerCase();
			switch (verb) {

			case "help":
				System.err.println(help);
				break;

			case "limit":
				limit = Integer.parseInt(commandAndArgs[1]);
				if (limit < 0) limit = Integer.MAX_VALUE;
				break;

			case "maxdependents":
				maxDependents = Long.parseLong(commandAndArgs[1]);
				if (maxDependents < 0) maxDependents = Long.MAX_VALUE;
				break;

			case "clear":
				predicateFilters.clear();
				predicateFiltersSpec.clear();
				break;

			case "show":
				for (int i = 0; i < nextFutureId; i++)
					if (id2Query.containsKey(i))
						System.out.printf("%3d\t%s\n", i, id2Query.get(i));
				break;

			case "wait":
			case "cancel":
				final int id = Integer.parseInt(commandAndArgs[1]);
				Future<Void> future = id2Future.get(id);
				if (future == null) System.err.println("No such search ID");
				else {
					if ("wait".equals(verb)) {
						final var r = id2Subscriber.get(id).get().current;
						for (int i = 0; i < Math.min(limit, r.length); i++) System.out.println(r[i].gid + "\t" + Util.getCallableName(r[i].gid, se.context()) + "\t" + r[i].score);
					} else future.cancel(true);

					id2Future.remove(id);
					id2Subscriber.remove(id);
					id2Query.remove(id);
				}
				break;

			case "inspect":
				final var subscriber = id2Subscriber.get(Integer.parseInt(commandAndArgs[1]));
				if (subscriber == null) System.err.println("No such search ID");
				final var r = subscriber.last() == null? new Result[0] : subscriber.last().current;
				for (int i = 0; i < Math.min(limit, r.length); i++) System.out.println(r[i].gid + "\t" + Util.getCallableName(r[i].gid, se.context()) + "\t" + r[i].score);
				break;

				
			case "time":
				System.err.printf("\n%,d arcs \nResolve time: %,.3fs  Merge time: %,.3fs  Visit time %,.3fs\n",
						se.visitedArcs,
						se.resolveTime.get() * 1E-9,
						se.mergeTime.get() * 1E-9,
						se.visitTime.get() * 1E-9);
				break;
				
			case "f":
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
				if (predicateFilters.size() < 2) throw new RuntimeException("At least two predicates must be present");
				if ("and".equals(commandAndArgs[0].toLowerCase())) {
					predicateFilters.push(predicateFilters.pop().and(predicateFilters.pop()));
					predicateFiltersSpec.push("(" + predicateFiltersSpec.pop() + " && " + predicateFiltersSpec.pop() + ")");
				} else {
					predicateFilters.push(predicateFilters.pop().or(predicateFilters.pop()));
					predicateFiltersSpec.push("(" + predicateFiltersSpec.pop() + " || " + predicateFiltersSpec.pop() + ")");
				}
				break;

			case "not":
				if (predicateFilters.size() < 1) throw new RuntimeException("At least one predicates must be present");
				predicateFilters.push(predicateFilters.pop().negate());
				predicateFiltersSpec.push("!(" + predicateFiltersSpec.pop() + ")");
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


	@SuppressWarnings("boxing")
	public static void main(final String args[]) throws Exception {
		final SimpleJSAP jsap = new SimpleJSAP(SearchEngineClient.class.getName(), "Creates an instance of SearchEngine and answers queries from the command line (rlwrap recommended).", new Parameter[] {
				new FlaggedOption("blacklist", JSAP.STRING_PARSER, null, JSAP.NOT_REQUIRED, 'b', "blacklist", "A blacklist of GIDs of revision call graphs that should be considered as missing."),
				new UnflaggedOption("jdbcURI", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The JDBC URI."),
				new UnflaggedOption("database", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The database name."),
				new UnflaggedOption("rocksDb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The path to the RocksDB database of revision call graphs."),
				new UnflaggedOption("cache", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The RocksDB cache."),
				new UnflaggedOption("resolverGraph", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The path to a resolver graph (will be created if it does not exist)."), });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);

		final String jdbcURI = jsapResult.getString("jdbcURI");
		final String database = jsapResult.getString("database");
		final String rocksDb = jsapResult.getString("rocksDb");
		final String cacheDir = jsapResult.getString("cache");
		final String resolverGraph = jsapResult.getString("resolverGraph");
		final LongOpenHashSet blacklist = new LongOpenHashSet();
		if (jsapResult.userSpecified("blacklist")) TextIO.asLongIterator(new BufferedReader(new InputStreamReader(new FileInputStream(jsapResult.getString("blacklist")), StandardCharsets.US_ASCII))).forEachRemaining(x -> blacklist.add(x));


		final SearchEngine se = new SearchEngine(jdbcURI, database, rocksDb, cacheDir, resolverGraph, null, blacklist);
		final SearchEngineClient sec = new SearchEngineClient(se);

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
				sec.executeCommand(matcher.group(1));
				continue;
			}
			try {
				final char dir = line.charAt(0);
				if (dir != '+' && dir != '-') {
					if (dir != '#') System.err.println("First character must be '+', '-', or '#'");
					continue;
				}
				final FastenJavaURI uri = FastenJavaURI.create(line.substring(1));

				se.resetCounters();

				SubmissionPublisher<SortedSet<Result>> publisher = new SubmissionPublisher<>();
				TopKProcessor topKProcessor = new TopKProcessor(sec.limit);
				publisher.subscribe(topKProcessor);

				final WaitOnTerminateFutureSubscriber<Update> futureSubscriber = new WaitOnTerminateFutureSubscriber<>();

				topKProcessor.subscribe(futureSubscriber);

				final Result[] r;
				if (uri.getPath() == null) {
					if (dir == '+') {
						se.fromRevision(uri, sec.limit, publisher);
						r = futureSubscriber.get().current;
						for (int i = 0; i < Math.min(sec.limit, r.length); i++) System.out.println(r[i].gid + "\t" + Util.getCallableName(r[i].gid, se.context()) + "\t" + r[i].score);
					}
					else {
						final int id = sec.nextFutureId++;
						sec.id2Future.put(id, se.toRevision(uri, sec.limit, publisher));
						sec.id2Subscriber.put(id, futureSubscriber);
						sec.id2Query.put(id, line);
						System.err.println("Id: " + id);
					}
				} else {
					final long gid = Util.getCallableGID(uri, se.context());
					if (gid == -1) {
						System.err.println("Unknown URI " + uri);
						continue;
					}
					if (dir == '+') {
						se.fromCallable(gid, sec.limit, publisher);
						r = futureSubscriber.get().current;
						for (int i = 0; i < Math.min(sec.limit, r.length); i++) System.out.println(r[i].gid + "\t" + Util.getCallableName(r[i].gid, se.context()) + "\t" + r[i].score);
					}
					else {
						final int id = sec.nextFutureId++;
						sec.id2Future.put(id, se.toCallable(gid, sec.limit, publisher));
						sec.id2Subscriber.put(id, futureSubscriber);
						sec.id2Query.put(id, line);
						System.err.println("Id: " + id);
					}
				}

				for (final var t : se.throwables) {
					System.err.println(t);
					System.err.println("\t" + t.getStackTrace()[0]);
				}
			} catch (final Exception e) {
				e.printStackTrace();
			} finally {}
		}
	}
}