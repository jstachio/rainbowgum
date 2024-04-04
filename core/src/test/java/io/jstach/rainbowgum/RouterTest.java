package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.util.List;

import org.junit.jupiter.api.Test;

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

		var root = InternalRootRouter.of(List.of(router1, router2), StaticLevelResolver.of(Level.ERROR));

		var route = root.route("stuff", Level.DEBUG);

		assertTrue(route.isEnabled());

		if (route.isEnabled()) {
			TestEventBuilder.of().level(Level.DEBUG).to(route).event().message("DEBUG").log();
		}

		String results1 = publisher1.events.toString();
		String results2 = publisher2.events.toString();

		assertEquals(
				"[DefaultLogEvent[timestamp=1970-01-01T00:00:00Z, threadName=main, threadId=1, level=DEBUG, loggerName=test, formattedMessage=DEBUG, keyValues=INSTANCE, throwableOrNull=null]]",
				results1);
		assertEquals("[]", results2);

	}

}
