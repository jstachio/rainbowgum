package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;

class RouterTest {

	@Test
	void testSingleRouter() throws Exception {

		// LevelResolver resolver = InternalLevelResolver.of(Map.of("stuff", Level.INFO,
		// "", Level.DEBUG));
		LevelResolver resolver = LevelResolver.builder().level(Level.DEBUG).level(Level.INFO, "stuff").build();
		assertEquals(Level.INFO, resolver.resolveLevel("stuff.crap"));
		var publisher = new TestSyncPublisher();
		@SuppressWarnings("resource")
		var router = new SimpleRouter("1", publisher, resolver);
		var route = router.route("stuff.crap", Level.DEBUG);
		assertFalse(route.isEnabled());
		assertTrue(router.route("blah", Level.DEBUG).isEnabled());

	}

	@Test
	void testCompositeRouter() throws Exception {

		// LevelResolver resolver1 = InternalLevelResolver.of(Map.of("stuff", Level.INFO,
		// "", Level.DEBUG));
		LevelResolver resolver1 = LevelResolver.builder().level(Level.DEBUG).level(Level.INFO, "stuff").build();
		var publisher1 = new TestSyncPublisher();
		var router1 = new SimpleRouter("1", publisher1, resolver1);

		// LevelResolver resolver2 = InternalLevelResolver.of(Map.of("stuff", Level.DEBUG,
		// "", Level.WARNING));
		LevelResolver resolver2 = LevelResolver.builder().level(Level.DEBUG, "stuff").level(Level.WARNING).build();

		var publisher2 = new TestSyncPublisher();
		var router2 = new SimpleRouter("2", publisher2, resolver2);

		var config = LogConfig.builder().build();
		var root = InternalRootRouter.of(List.of(router1, router2), config);

		var route = root.route("stuff", Level.DEBUG);

		assertTrue(route.isEnabled());

		if (route.isEnabled()) {
			TestEventBuilder.of().level(Level.DEBUG).to(route).event().message("DEBUG").log();
		}

		String results1 = publisher1.events.toString();
		String results2 = publisher2.events.toString();

		assertEquals(
				"[DefaultLogEvent[timestamp=1970-01-01T00:00:00Z, threadName=main, threadId=1, level=DEBUG, loggerName=test, formattedMessage=DEBUG, keyValues={}, throwableOrNull=null]]",
				results1);
		assertEquals("[]", results2);

	}

	@Test
	void testSyncRouterDoesNotFreezeEvent() throws Exception {
		/*
		 * A synchronous route fully encodes and writes the event on the calling thread
		 * before log() returns, so there is no other thread that could race with further
		 * mutation of the live key values. Freezing (and therefore defensively copying)
		 * would just be wasted allocation here.
		 */
		var resolver = LevelResolver.builder().level(Level.DEBUG).build();
		var publisher = new TestSyncPublisher();
		@SuppressWarnings("resource")
		var router = new SimpleRouter("1", publisher, resolver);
		var route = router.route("stuff", Level.DEBUG);

		var mkvs = MutableKeyValues.of().add("phase", "A");
		TestEventBuilder.of().level(Level.DEBUG).to(route).event().keyValues(mkvs).message("msg").log();

		assertEquals(1, publisher.events.size());
		var captured = publisher.events.getFirst();
		assertSame(mkvs, captured.keyValues());
	}

	@Test
	void testAsyncRouterFreezesEvent() throws Exception {
		/*
		 * An async route hands the event to a worker thread and returns immediately, so
		 * the calling thread can go on to mutate MDC (or any other mutable state backing
		 * the event) before the worker gets around to it. The router must freeze
		 * (defensively copy) the event before publishing it asynchronously.
		 */
		var resolver = LevelResolver.builder().level(Level.DEBUG).build();
		var publisher = new TestAsyncPublisher();
		@SuppressWarnings("resource")
		var router = new SimpleRouter("1", publisher, resolver);
		var route = router.route("stuff", Level.DEBUG);

		var mkvs = MutableKeyValues.of().add("phase", "A");
		TestEventBuilder.of().level(Level.DEBUG).to(route).event().keyValues(mkvs).message("msg").log();

		assertEquals(1, publisher.events.size());
		var captured = publisher.events.getFirst();
		assertNotSame(mkvs, captured.keyValues());
		assertFalse(captured.keyValues() instanceof MutableKeyValues);
		assertEquals("A", captured.keyValues().getValueOrNull("phase"));

		// mutating the original after the fact must not affect the captured snapshot.
		mkvs.add("phase", "B");
		assertEquals("A", captured.keyValues().getValueOrNull("phase"));
	}

}
