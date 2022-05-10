package eu.fasten.core.search;

import java.util.concurrent.Executor;
import java.util.concurrent.Flow.Processor;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.SubmissionPublisher;

import eu.fasten.core.search.SearchEngine.PathResult;
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;

/** 
 */
public class ShortestKProcessor extends SubmissionPublisher<SearchEngine.PathResult[]> implements Processor<SearchEngine.PathResult, SearchEngine.PathResult[]> {

	private static final PathResult[] EMPTY_RESULT_ARRAY = new PathResult[0];

		
	/** The current set of results */
	private final ObjectRBTreeSet<PathResult> results;
	/** The maximum number of results. */
	private int maxResults;
	/** The search engine using this processor. */
	private final SearchEngine searchEngine;

	/** 
	 */
    public ShortestKProcessor(final int maxResults, final SearchEngine se) {
    	super();
    	this.maxResults = maxResults;
    	results = new ObjectRBTreeSet<>();
    	this.searchEngine = se;
    }

	/** Creates a new processor, with custom executor. 
	 * 
	 * @param maxResults  maximum number of topmost results to be kept track of.
	 * @param se the search engine using this processor.
	 * @param executor the custom executor to be used for this processor.
	 * @param maxBufferCapacity the maximum buffer capacity for the publication queue.
	 */
    public ShortestKProcessor(final int maxResults, final SearchEngine se, final Executor executor, final int maxBufferCapacity) {
    	super(executor, maxBufferCapacity);
    	this.maxResults = maxResults;
    	results = new ObjectRBTreeSet<>();
    	this.searchEngine = se;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public synchronized void onNext(final PathResult path) {
    	if (results.size() < maxResults) {
    		results.add(path);
    		submit(results.toArray(EMPTY_RESULT_ARRAY));
    	}
    	else {
    		PathResult last = results.last();
    		if (last.compareTo(path) > 0) {
    			results.remove(last);
    			results.add(path);
        		submit(results.toArray(EMPTY_RESULT_ARRAY));
    		}
    	}
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