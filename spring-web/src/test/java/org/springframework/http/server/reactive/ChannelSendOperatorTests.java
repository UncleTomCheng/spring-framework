/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.http.server.reactive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Rossen Stoyanchev
 * @author Stephane Maldini
 */
public class ChannelSendOperatorTests {

	private OneByOneAsyncWriter writer;


	@Before
	public void setUp() throws Exception {
		this.writer = new OneByOneAsyncWriter();
	}

	private <T> Mono<Void> sendOperator(Publisher<String> source){
		return new ChannelSendOperator<>(source, writer::send);
	}

	@Test
	public void errorBeforeFirstItem() throws Exception {
		IllegalStateException error = new IllegalStateException("boo");
		Mono<Void> completion = Mono.<String>error(error).as(this::sendOperator);
		Signal<Void> signal = completion.materialize().block();

		assertNotNull(signal);
		assertSame("Unexpected signal: " + signal, error, signal.getThrowable());
	}

	@Test
	public void completionBeforeFirstItem() throws Exception {
		Mono<Void> completion = Flux.<String>empty().as(this::sendOperator);
		Signal<Void> signal = completion.materialize().block();

		assertNotNull(signal);
		assertTrue("Unexpected signal: " + signal, signal.isOnComplete());

		assertEquals(0, this.writer.items.size());
		assertTrue(this.writer.completed);
	}

	@Test
	public void writeOneItem() throws Exception {
		Mono<Void> completion = Flux.just("one").as(this::sendOperator);
		Signal<Void> signal = completion.materialize().block();

		assertNotNull(signal);
		assertTrue("Unexpected signal: " + signal, signal.isOnComplete());

		assertEquals(1, this.writer.items.size());
		assertEquals("one", this.writer.items.get(0));
		assertTrue(this.writer.completed);
	}


	@Test
	public void writeMultipleItems() throws Exception {
		List<String> items = Arrays.asList("one", "two", "three");
		Mono<Void> completion = Flux.fromIterable(items).as(this::sendOperator);
		Signal<Void> signal = completion.materialize().block();

		assertNotNull(signal);
		assertTrue("Unexpected signal: " + signal, signal.isOnComplete());

		assertEquals(3, this.writer.items.size());
		assertEquals("one", this.writer.items.get(0));
		assertEquals("two", this.writer.items.get(1));
		assertEquals("three", this.writer.items.get(2));
		assertTrue(this.writer.completed);
	}

	@Test
	public void errorAfterMultipleItems() throws Exception {
		IllegalStateException error = new IllegalStateException("boo");
		Flux<String> publisher = Flux.generate(() -> 0, (idx , subscriber) -> {
			int i = ++idx;
			subscriber.next(String.valueOf(i));
			if (i == 3) {
				subscriber.error(error);
			}
			return i;
		});
		Mono<Void> completion = publisher.as(this::sendOperator);
		Signal<Void> signal = completion.materialize().block();

		assertNotNull(signal);
		assertSame("Unexpected signal: " + signal, error, signal.getThrowable());

		assertEquals(3, this.writer.items.size());
		assertEquals("1", this.writer.items.get(0));
		assertEquals("2", this.writer.items.get(1));
		assertEquals("3", this.writer.items.get(2));
		assertSame(error, this.writer.error);
	}


	private static class OneByOneAsyncWriter {

		private List<String> items = new ArrayList<>();

		private boolean completed = false;

		private Throwable error;


		public Publisher<Void> send(Publisher<String> publisher) {
			return subscriber -> Executors.newSingleThreadScheduledExecutor().schedule(() ->
							publisher.subscribe(new WriteSubscriber(subscriber)),50, TimeUnit.MILLISECONDS);
		}


		private class WriteSubscriber implements Subscriber<String> {

			private Subscription subscription;

			private final Subscriber<? super Void> subscriber;

			public WriteSubscriber(Subscriber<? super Void> subscriber) {
				this.subscriber = subscriber;
			}

			@Override
			public void onSubscribe(Subscription subscription) {
				this.subscription = subscription;
				this.subscription.request(1);
			}

			@Override
			public void onNext(String item) {
				items.add(item);
				this.subscription.request(1);
			}

			@Override
			public void onError(Throwable ex) {
				error = ex;
				this.subscriber.onError(ex);
			}

			@Override
			public void onComplete() {
				completed = true;
				this.subscriber.onComplete();
			}
		}
	}

}
