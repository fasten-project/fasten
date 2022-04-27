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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A subscriber that makes it easy to wait for the final call on {@link Subscriber#onComplete()} and
 * retrieve the last argument passed to {@link Subscriber#onNext(Object)}.
 * 
 * <p>
 * Typical usage is as follows:
 * 
 * <pre>
 * WaitOnTerminateFutureSubscriber<Update> futureSubscriber = new WaitOnTerminateFutureSubscriber<>(); // create future subscriber
 * topKProcessor.subscribe(futureSubscriber); // subscribe to result processor
 * [...] // invoke search method using result processor
 * Result[] results = futureSubscriber.get().current; // use results
 * </pre>
 */
final class WaitOnTerminateFutureSubscriber<T> implements Flow.Subscriber<T>, Future<T> {
	private boolean done;
	@SuppressWarnings("null")
	private T last;

	@Override
	public void onSubscribe(Subscription subscription) {
		subscription.request(Long.MAX_VALUE);
	}

	@Override
	public void onNext(T item) {
		last = item;
	}

	@Override
	public void onError(Throwable throwable) {
		throwable.printStackTrace(); // This really shouldn't happen
	}

	@Override
	public synchronized void onComplete() {
		done = true;
		notify();
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return false;
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public synchronized T get() throws InterruptedException, ExecutionException {
		while (!done) wait();
		return last;
	}

	@Override
	public synchronized T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		if (!done) wait(unit.toMillis(timeout));
		return last;
	}

	@Override
	public synchronized boolean isDone() {
		return done;
	}
}
