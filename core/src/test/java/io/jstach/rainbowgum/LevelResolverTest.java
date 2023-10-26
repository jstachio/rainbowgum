package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.System.Logger.Level;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LevelResolverTest {

	@Test
	void testResolveLevelString() {
		var m = Map.of("logging.level.io", "WARNING");
		LogConfig config = LogConfig.of(ServiceRegistry.of(), (k) -> m.get(k));
		Level actual = config.levelResolver().levelOrNull("io");
		Level expected = Level.WARNING;
		assertEquals(actual, expected);
		expected = config.levelResolver().resolveLevel("io.avaje");
		assertEquals(actual, expected);

		var resolver = LevelResolver.builder().buildLevelResolver(config.levelResolver());
		var level = resolver.resolveLevel("io.avaje");
		assertEquals(expected, level);
	}

}
