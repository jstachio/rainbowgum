package io.jstach.rainbowgum.test.nested;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogProperties;

class HoconLogPropertiesTest extends AbstractNestedLogPropertiesTest {

	@Override
	String format() {
		return "HOCON";
	}

	@Override
	LogProperties levels() {
		return NestedLogProperties.ofHocon("""
				logging.level {
				  root = warn
				  "com.example" = debug
				  "com.example.sub" = error
				  "tc.floci/floci-gcp@sha256:405c128b..." = trace
				  org.dotted.Unquoted = info
				}
				""");
	}

	@Override
	LogProperties rootLevelOnly() {
		return NestedLogProperties.ofHocon("""
				logging.level = error
				""");
	}

	@Override
	LogProperties changes() {
		return NestedLogProperties.ofHocon("""
				logging.change.mode = on
				logging.change.logger {
				  root = level
				  "com.example" = false
				}
				logging.caller.logger {
				  "com.example" = true
				}
				""");
	}

	@Override
	LogProperties groups() {
		return NestedLogProperties.ofHocon("""
				logging {
				  groups = [jdbc]
				  global.ansi.mode = off
				  group.jdbc = [org.postgresql, com.example.odd_name, root]
				  level { root = info, jdbc = debug }
				}
				""");
	}

	@Override
	LogProperties typo() {
		return NestedLogProperties.ofHocon("""
				logging.global.ansi.mod = on
				logging.level {
				  root = warn
				  "com.example" = debug
				}
				""");
	}

	/*
	 * Why the root has its own member: HOCON merges the later object over the earlier
	 * value, so the root level is silently lost.
	 */
	@Test
	void flatStyleLosesTheRootLevel() {
		var levels = config(NestedLogProperties.ofHocon("""
				logging.level = warn
				logging.level.com.example = debug
				""")).levelResolver();
		assertEquals(Level.DEBUG, levels.resolveLevel("com.example.Foo"));
		var noRootLevel = config(NestedLogProperties.ofHocon("")).levelResolver();
		assertEquals(noRootLevel.resolveLevel("other"), levels.resolveLevel("other"));
	}

}
