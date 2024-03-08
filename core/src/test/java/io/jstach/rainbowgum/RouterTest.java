package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RouterTest {

	@Test
	void testSingleRouter() throws Exception {

		LevelResolver resolver = InternalLevelResolver.of(Map.of("stuff", Level.INFO, "", Level.DEBUG));
		var publisher = new TestSyncPublisher();
		@SuppressWarnings("resource")
		var router = new SimpleRouter(publisher, resolver);
		var route = router.route("stuff.crap", Level.DEBUG);
		assertFalse(route.isEnabled());
		assertTrue(router.route("blah", Level.DEBUG).isEnabled());

	}

	@Test
	void testCompositeRouter() throws Exception {

		LevelResolver resolver1 = InternalLevelResolver.of(Map.of("stuff", Level.INFO, "", Level.DEBUG));
		var publisher1 = new TestSyncPublisher();
		var router1 = new SimpleRouter(publisher1, resolver1);

		LevelResolver resolver2 = InternalLevelResolver.of(Map.of("stuff", Level.DEBUG, "", Level.WARNING));
		var publisher2 = new TestSyncPublisher();
		var router2 = new SimpleRouter(publisher2, resolver2);

		var root = InternalRootRouter.of(List.of(router1, router2), InternalLevelResolver.of(Level.ERROR));

		var route = root.route("stuff", Level.DEBUG);

		assertTrue(route.isEnabled());

		if (route.isEnabled()) {
			route.log(EmptyLogEvent.DEBUG);
		}

		String results1 = publisher1.events.toString();
		String results2 = publisher2.events.toString();

		assertEquals("[DEBUG]", results1);
		assertEquals("[]", results2);

	}

}
