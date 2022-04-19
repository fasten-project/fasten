package eu.fasten.core.search;

import java.util.concurrent.Executor;
import java.util.concurrent.Flow.*;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Function;

import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;

public class SearchEngineTopKProcessor<T extends Comparable<T>> extends SubmissionPublisher<T> implements Processor<T, T> {

	private ObjectRBTreeSet<T> topK;
	private int k;

    public SearchEngineTopKProcessor(final int k, final Executor executor, final int maxBufferCapacity) {
    	super(executor, maxBufferCapacity);
    	topK = new ObjectRBTreeSet<>();
    	this.k = k;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(T item) {
        System.out.println("Transform Processor:  " + Thread.currentThread().getName() + " item " + item);
		if (topK.size() == k && topK.last().compareTo(item) > 0) topK.remove(topK.last());
		if (topK.size() < k) topK.add(item);
    }

    @Override
    public void onError(Throwable t) {
        t.printStackTrace();
    }

    @Override
    public void onComplete() {
        topK.forEach(x -> submit(x));
        close();
    }
}