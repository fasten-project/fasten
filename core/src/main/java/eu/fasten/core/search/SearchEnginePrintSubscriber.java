package eu.fasten.core.search;

import java.util.concurrent.Flow;
import java.util.concurrent.ThreadPoolExecutor;

public class SearchEnginePrintSubscriber<T> implements Flow.Subscriber<T> {
	
	private ThreadPoolExecutor executor;
	
	public SearchEnginePrintSubscriber(final ThreadPoolExecutor executor) {
		this.executor = executor;
	}
	
    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request (Long.MAX_VALUE); // Long.MAX_VALUE used here will be considered to obtain unlimited data.
    }
    @Override
    public void onNext (T item) {
        System.out.println("*** Print subscriber:  " + Thread.currentThread().getName()  + " got item " + item);
    }
    @Override
    public void onError (Throwable t) {
        t.printStackTrace ();
    }
    @Override
    public void onComplete() {
        System.out.println ("*** Print subscriber one");
        executor.shutdown();
    }
}
