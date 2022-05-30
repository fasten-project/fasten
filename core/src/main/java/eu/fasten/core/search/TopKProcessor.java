package eu.fasten.core.search;

import java.util.Iterator;
import java.util.SortedSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow.Processor;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.SubmissionPublisher;

import eu.fasten.core.search.SearchEngine.Result;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;

/** A processor that receive updates in the form of batches of sorted sets of {@linkplain Result search results} (sorted by score, with
 *  no gid repeated) and keeps track of the topmost gids with largest score. Every time this set changes (some elements are deleted and/or
 *  added) the changes and the overall new set are published in the form of an {@link Update} (that contains sorted array representation of
 *  the current set, of the additions and of the deletions).
 */
public class TopKProcessor extends SubmissionPublisher<TopKProcessor.Update> implements Processor<SortedSet<Result>, TopKProcessor.Update> {

	private static final Result[] EMPTY_RESULT_ARRAY = new Result[0];

	/** Contains information about a single update: it contains a sorted array representation of the current set, of the additions and of the deletions.
	 */
	public final class Update {
		/** Current sorted array of the topmost results. */
		public Result[] current;
		/** Array of the new topmost results. */
		public Result[] additions;
		/** Array of the results that should be removed from the previous update. */
		public Result[] deletions;

		public Update(Result[] current, Result[] additions, Result[] deletions) {
			this.current = current;
			this.additions = additions;
			this.deletions = deletions;
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(); 
			Result[] r = current;
			for (int i = 0; i < Math.min(maxResults, r.length); i++) 
				sb.append(r[i].gid + "\t" + Util.getCallableName(r[i].gid, searchEngine.context()) + "\t" + String.format("%.4f", Double.valueOf(r[i].score)) + "\t" + r[i].dependent.groupId + ":" + r[i].dependent.artifactId + ":" + r[i].dependent.version.toString() + "\n");
			return sb.toString();
		}
	}
	
	
	/** The current set of results, with identity determined by gid only. */
	private final ObjectOpenHashSet<Result> results;
	/** The current sorted set of results (gids are not repeated here). */
	private final ObjectRBTreeSet<Result> sortedResults;
	/** The maximum number of results. */
	private int maxResults;
	/** The search engine using this processor. */
	private final SearchEngine searchEngine;

	/** Creates a new processor, with default executor.
	 * 
	 * @param maxResults maximum number of topmost results to be kept track of.
	 */
    public TopKProcessor(final int maxResults, final SearchEngine se) {
    	super();
    	this.maxResults = maxResults;
    	results = new ObjectOpenHashSet<>(maxResults, 0.5f);
    	sortedResults = new ObjectRBTreeSet<>();
    	this.searchEngine = se;
    }

	/** Creates a new processor, with custom executor. 
	 * 
	 * @param maxResults  maximum number of topmost results to be kept track of.
	 * @param se the search engine using this processor.
	 * @param executor the custom executor to be used for this processor.
	 * @param maxBufferCapacity the maximum buffer capacity for the publication queue.
	 */
    public TopKProcessor(final int maxResults, final SearchEngine se, final Executor executor, final int maxBufferCapacity) {
    	super(executor, maxBufferCapacity);
    	this.maxResults = maxResults;
    	results = new ObjectOpenHashSet<>(maxResults, 0.5f);
    	sortedResults = new ObjectRBTreeSet<>();
    	this.searchEngine = se;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public synchronized void onNext(SortedSet<Result> bfsResults) {
    	final ObjectRBTreeSet<Result> additions = new ObjectRBTreeSet<>();
    	final ObjectRBTreeSet<Result> deletions = new ObjectRBTreeSet<>();
    	
  
    	final Iterator<Result> iterator = bfsResults.iterator();
    	for(int i = 0; i < bfsResults.size(); i++) {
    		final Result result = iterator.next();

    		final Result oldResult = results.get(result);

    		if (oldResult != null) {    // GID was already there
    			if (oldResult.score < result.score) {   // ...but with smaller score: substitute it!
    				results.remove(oldResult);
    				results.add(result);
    				sortedResults.remove(oldResult);
    				sortedResults.add(result);
    				deletions.add(oldResult);
    				additions.add(result);
    			}
    		} else if (sortedResults.size() < maxResults) { // New GID, not enough results: add in all cases
    			results.add(result);
    			sortedResults.add(result);
				additions.add(result);
    		} else if (result.score > sortedResults.last().score) { // New GID, already have results: substitute to bottommost, if better
    			final Result last = sortedResults.last();
    			results.remove(last);
    			sortedResults.remove(last);
    			deletions.add(last);
    			
    			results.add(result);
    			sortedResults.add(result);
    			additions.add(result);
    		}
    	}
    	
    	submit(new Update(sortedResults.toArray(EMPTY_RESULT_ARRAY), additions.toArray(EMPTY_RESULT_ARRAY), deletions.toArray(EMPTY_RESULT_ARRAY)));
    }


    @Override
    public void onError(Throwable t) {
        t.printStackTrace();
    }

    @Override
    public void onComplete() {
        close();
    }
}