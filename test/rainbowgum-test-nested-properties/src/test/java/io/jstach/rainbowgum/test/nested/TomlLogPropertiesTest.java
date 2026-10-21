package io.jstach.rainbowgum.test.nested;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogProperties;

class TomlLogPropertiesTest extends AbstractNestedLogPropertiesTest {

	@Override
	String format() {
		return "TOML";
	}

	@Override
	LogProperties levels() {
		return NestedLogProperties.ofToml("""
				[logging.level]
				root = "warn"
				"com.example" = "debug"
				"com.example.sub" = "error"
				"tc.floci/floci-gcp@sha256:405c128b..." = "trace"
				org.dotted.Unquoted = "info"
				""");
	}

	@Override
	LogProperties rootLevelOnly() {
		return NestedLogProperties.ofToml("""
				[logging]
				level = "error"
				""");
	}

	@Override
	LogProperties changes() {
		return NestedLogProperties.ofToml("""
				[logging.change]
				mode = "on"

				[logging.change.logger]
				root = "level"
				"com.example" = "false"

				[logging.caller.logger]
				"com.example" = "true"
				""");
	}

	@Override
	LogProperties groups() {
		return NestedLogProperties.ofToml("""
				[logging]
				groups = ["jdbc"]
				global.ansi.mode = "off"

				[logging.group]
				jdbc = ["org.postgresql", "com.example.odd_name", "root"]

				[logging.level]
				root = "info"
				jdbc = "debug"
				""");
	}

	@Override
	LogProperties typo() {
		return NestedLogProperties.ofToml("""
				[logging.global]
				ansi.mod = "on"

				[logging.level]
				root = "warn"
				"com.example" = "debug"
				""");
	}

	/*
	 * Why logger names are quoted and the root has its own member: TOML rejects a key
	 * that is both a value and a table.
	 */
	@Test
	void flatStyleIsInvalidToml() {
		var e = assertThrows(IllegalArgumentException.class, () -> NestedLogProperties.ofToml("""
				logging.level = "warn"
				logging.level.com.example = "debug"
				"""));
		assertEquals("Invalid TOML: logging.level is not a table (previously defined at line 1, column 1) "
				+ "(line 2, column 1)", e.getMessage());
	}

}
