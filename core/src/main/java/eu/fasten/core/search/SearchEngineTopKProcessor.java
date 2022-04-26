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

public class SearchEngineTopKProcessor extends SubmissionPublisher<SearchEngineTopKProcessor.Update> implements Processor<SortedSet<Result>, SearchEngineTopKProcessor.Update> {

	private static final Result[] EMPTY_RESULT_ARRAY = new Result[0];

	public static final class Update {
		public Result[] current;
		public Result[] additions;
		public Result[] deletions;

		public Update(Result[] current, Result[] additions, Result[] deletions) {
			this.current = current;
			this.additions = additions;
			this.deletions = deletions;
		}
	}
	
	private final ObjectOpenHashSet<Result> results;
	private final ObjectRBTreeSet<Result> sortedResults;

	private int maxResults;

    public SearchEngineTopKProcessor(final int maxResults) {
    	super();
    	this.maxResults = maxResults;
    	results = new ObjectOpenHashSet<>(maxResults, 0.5f);
    	sortedResults = new ObjectRBTreeSet<>();
    }

    public SearchEngineTopKProcessor(final int maxResults, final Executor executor, final int maxBufferCapacity) {
    	super(executor, maxBufferCapacity);
    	this.maxResults = maxResults;
    	results = new ObjectOpenHashSet<>(maxResults, 0.5f);
    	sortedResults = new ObjectRBTreeSet<>();
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }

	/** Merges into the global results tree a new local (from BFS) results tree.
	 * 
	 * @param results the global results set.
	 * @param sortedResults the global results tree.
	 * @param bfsResults the local (from BFS) results tree.
	 * @param maxResults the maximum number of desired results.
	 * @return true of {@code results} has been modified.
	 */
    @Override
    public synchronized void onNext(SortedSet<Result> bfsResults) {
    	final ObjectRBTreeSet<Result> additions = new ObjectRBTreeSet<>();
    	final ObjectRBTreeSet<Result> deletions = new ObjectRBTreeSet<>();
    	
  
    	final Iterator<Result> iterator = bfsResults.iterator();
    	for(int i = 0; i < bfsResults.size(); i++) {
    		final Result result = iterator.next();

    		final Result oldResult = results.get(result);

    		if (oldResult != null) {
    			if (oldResult.score < result.score) {
    				results.add(result);
    				sortedResults.remove(oldResult);
    				sortedResults.add(result);
    				deletions.add(oldResult);
    				additions.add(result);
    			}
    		} else if (sortedResults.size() < maxResults) {
    			results.add(result);
    			sortedResults.add(result);
				additions.add(result);
    		} else if (result.score > sortedResults.last().score) {
    			final Result last = sortedResults.last();
    			results.remove(last);
    			sortedResults.remove(last);
    			deletions.add(last);
    			
    			results.add(result);
    			sortedResults.add(result);
    			additions.add(result);
    		}
    	}
    	
    	submit(new Update(results.toArray(EMPTY_RESULT_ARRAY), additions.toArray(EMPTY_RESULT_ARRAY), deletions.toArray(EMPTY_RESULT_ARRAY)));
    }


    @Override
    public void onError(Throwable t) {
        t.printStackTrace();
    }

    @Override
    public void onComplete() {
        submit(new Update(results.toArray(EMPTY_RESULT_ARRAY), EMPTY_RESULT_ARRAY, EMPTY_RESULT_ARRAY));
        close();
    }
}