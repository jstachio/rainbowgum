package io.jstach.rainbowgum.kitchensink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType;
import io.jstach.rainbowgum.LogEvent.Caller.CallerType;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

/**
 * A facade passes any string as a logger name, and the name is used to build property
 * keys, resolve levels and groups, and is written by every encoder. Each name here goes
 * through all of that with every optional module loaded.
 * <p>
 * The abbreviations match Logback 1.5.37's {@code %logger{n}} except where a comment says
 * otherwise. doc/logger-name-edge-cases.md compares these names across Logback, Log4j2
 * and Rainbow Gum.
 *
 * @see <a href="https://github.com/jstachio/rainbowgum/issues/752">#752</a>
 */
class LoggerNameEdgeCaseTest {

	enum LoggerName {

		EMPTY("", "||||", "\"\"", "\"\"", "\"\""), //
		DOT(".", "|.|.|.|.", ".", "\".\"", "\".\""), //
		DOTS("..", "|..|..|..|..", "..", "\"..\"", "\"..\""), //
		TRAILING_DOT("a.", "|a.|a.|a.|a.", "a.", "\"a.\"", "\"a.\""), //
		LEADING_DOT(".a", "a|.a|.a|.a|.a", ".a", "\".a\"", "\".a\""), //
		EMPTY_SEGMENT("a..b", "b|a..b|a..b|a..b|a..b", "a..b", "\"a..b\"", "\"a..b\""), //
		TRAILING_DOT_AFTER_SEGMENTS("a.b.", "|a.b.|a.b.|a.b.|a.b.", "a.b.", "\"a.b.\"", "\"a.b.\""), //
		SPACE(" ", " | | | | ", "\" \"", "\" \"", "\" \""), //
		LEADING_SPACE(" a", " a| a| a| a| a", "\" a\"", "\" a\"", "\" a\""), //
		TRAILING_SPACE("a ", "a |a |a |a |a ", "\"a \"", "\"a \"", "\"a \""), //
		INNER_SPACE("a b", "a b|a b|a b|a b|a b", "\"a b\"", "\"a b\"", "\"a b\""), //
		LINE_FEED("a\nb", "a\nb|a\nb|a\nb|a\nb|a\nb", "\"a\\nb\"", "\"a\\nb\"", "\"a\\nb\""), //
		CARRIAGE_RETURN_LINE_FEED("a\r\nb", "a\r\nb|a\r\nb|a\r\nb|a\r\nb|a\r\nb", "\"a\\r\\nb\"", "\"a\\r\\nb\"",
				"\"a\\r\\nb\""), //
		TAB("a\tb", "a\tb|a\tb|a\tb|a\tb|a\tb", "\"a\\tb\"", "\"a\\tb\"", "\"a\\tb\""), //
		// Logback returns its root logger, named ROOT.
		ROOT_LOWER("root", "root|root|root|root|root", "root", "\"root\"", "\"root\""), //
		// Logback returns its root logger, named ROOT.
		ROOT_UPPER("ROOT", "ROOT|ROOT|ROOT|ROOT|ROOT", "ROOT", "\"ROOT\"", "\"ROOT\""), //
		// Logback returns its root logger, named ROOT.
		ROOT_MIXED("Root", "Root|Root|Root|Root|Root", "Root", "\"Root\"", "\"Root\""), //
		ROOT_CHILD("root.child", "child|r.child|r.child|r.child|root.child", "root.child", "\"root.child\"",
				"\"root.child\""), //
		INNER_CLASS("com.Foo$Bar", "Foo$Bar|c.Foo$Bar|c.Foo$Bar|c.Foo$Bar|com.Foo$Bar", "com.Foo$Bar",
				"\"com.Foo$Bar\"", "\"com.Foo$Bar\""), //
		INNER_CLASS_CHILD("com.Foo$Bar.baz", "baz|c.F.baz|c.F.baz|c.F.baz|com.Foo$Bar.baz", "com.Foo$Bar.baz",
				"\"com.Foo$Bar.baz\"", "\"com.Foo$Bar.baz\""), //
		DOLLAR("$", "$|$|$|$|$", "$", "\"$\"", "\"$\""), //
		DOLLAR_SEGMENT("a.$.b", "b|a.$.b|a.$.b|a.$.b|a.$.b", "a.$.b", "\"a.$.b\"", "\"a.$.b\""), //
		LATIN_ACCENTS("café.Ünïcode", "Ünïcode|c.Ünïcode|c.Ünïcode|c.Ünïcode|café.Ünïcode", "café.Ünïcode",
				"\"café.Ünïcode\"", "\"café.Ünïcode\""), //
		// Logback splits the emoji in {1} and {5}.
		EMOJI_SEGMENT("a.😀.b", "b|a.😀.b|a.😀.b|a.😀.b|a.😀.b", "a.😀.b", "\"a.😀.b\"", "\"a.😀.b\""), //
		NUL("a\u0000b", "a\u0000b|a\u0000b|a\u0000b|a\u0000b|a\u0000b", "\"a\\u0000b\"", "\"a\\u0000b\"",
				"\"a\\u0000b\""), //
		ANSI_ESCAPE("a\u001b[31mred", "a\u001b[31mred|a\u001b[31mred|a\u001b[31mred|a\u001b[31mred|a\u001b[31mred",
				"\"a\\u001b[31mred\"", "\"a\\u001b[31mred\"", "\"a\\u001b[31mred\""), //
		QUOTE("a\"b", "a\"b|a\"b|a\"b|a\"b|a\"b", "\"a\\\"b\"", "\"a\\\"b\"", "\"a\\\"b\""), //
		BACKSLASH("a\\b", "a\\b|a\\b|a\\b|a\\b|a\\b", "\"a\\\\b\"", "\"a\\\\b\"", "\"a\\\\b\""), //
		EQUALS("a=b", "a=b|a=b|a=b|a=b|a=b", "\"a=b\"", "\"a=b\"", "\"a=b\""), //
		COLON("a:b", "a:b|a:b|a:b|a:b|a:b", "a:b", "\"a:b\"", "\"a:b\""), //
		HASH("a#b", "a#b|a#b|a#b|a#b|a#b", "a#b", "\"a#b\"", "\"a#b\""), //
		BRACES("a{name}b", "a{name}b|a{name}b|a{name}b|a{name}b|a{name}b", "a{name}b", "\"a{name}b\"", "\"a{name}b\""), //
		TESTCONTAINERS_DIGEST("tc.floci/floci-gcp@sha256:405c128b...", "|t.f...|t.f...|t.f...|t.f...",
				"tc.floci/floci-gcp@sha256:405c128b...", "\"tc.floci/floci-gcp@sha256:405c128b...\"",
				"\"tc.floci/floci-gcp@sha256:405c128b...\""), //
		LONG_CLASS_NAME("com.example.service.impl.VeryLongClassNameForAbbreviation",
				"VeryLongClassNameForAbbreviation|c.e.s.i.VeryLongClassNameForAbbreviation|c.e.s.i.VeryLongClassNameForAbbreviation|c.e.s.i.VeryLongClassNameForAbbreviation|c.e.s.i.VeryLongClassNameForAbbreviation",
				"com.example.service.impl.VeryLongClassNameForAbbreviation",
				"\"com.example.service.impl.VeryLongClassNameForAbbreviation\"",
				"\"com.example.service.impl.VeryLongClassNameForAbbreviation\""), //
		MANY_SEGMENTS("a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p",
				"p|a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p|a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p|a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p|a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p",
				"a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p", "\"a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p\"",
				"\"a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p\""), //
		THREE_DOTS("...", "|...|...|...|...", "...", "\"...\"", "\"...\""), //
		QUESTION_MARK("?", "?|?|?|?|?", "?", "\"?\"", "\"?\""), //
		QUESTION_MARKS("???", "???|???|???|???|???", "???", "\"???\"", "\"???\""), //
		QUESTION_MARK_SEGMENT("a.?.b", "b|a.?.b|a.?.b|a.?.b|a.?.b", "a.?.b", "\"a.?.b\"", "\"a.?.b\""), //
		EMOJI_ONLY("😀😀😀", "😀😀😀|😀😀😀|😀😀😀|😀😀😀|😀😀😀", "😀😀😀", "\"😀😀😀\"", "\"😀😀😀\""), //
		// Logback splits the emoji in {1} and {5}.
		EMOJI_SEGMENTS("😀.😀.😀", "😀|😀.😀.😀|😀.😀.😀|😀.😀.😀|😀.😀.😀", "😀.😀.😀", "\"😀.😀.😀\"",
				"\"😀.😀.😀\""), //
		// Logback splits the emoji in {1}.
		EMOJI_FIRST_SEGMENT("😀.b", "b|😀.b|😀.b|😀.b|😀.b", "😀.b", "\"😀.b\"", "\"😀.b\""), //
		;

		final String name;

		/*
		 * %logger{0}|%logger{1}|%logger{5}|%logger{10}|%logger{20}
		 */
		final String abbreviated;

		/*
		 * The logger value as each encoder writes it: logfmt, JSON5, and
		 * rainbowgum-json's ECS, Logstash, GELF and Logback JSON encoders, which all
		 * escape the same way.
		 */
		final String logfmt;

		final String json5;

		final String json;

		LoggerName(String name, String abbreviated, String logfmt, String json5, String json) {
			this.name = name;
			this.abbreviated = abbreviated;
			this.logfmt = logfmt;
			this.json5 = json5;
			this.json = json;
		}

		@Nullable String parentOrNull() {
			int dot = name.lastIndexOf('.');
			return dot > 0 ? name.substring(0, dot) : null;
		}

	}

	private static LogConfig config(Map<String, String> values) {
		var all = new HashMap<String, String>();
		all.put("logging.level", "warn");
		all.putAll(values);
		return LogConfig.builder().properties(LogProperties.builder().fromFunction(all::get).build()).build();
	}

	private static String levelKey(String name) {
		return name.isEmpty() ? "logging.level" : "logging.level." + name;
	}

	private static List<String> alerts(LogConfig config) {
		return config.alerts().dump().stream().map(e -> e.level() + " " + e.message()).toList();
	}

	@ParameterizedTest
	@EnumSource(LoggerName.class)
	void rootLevel(LoggerName n) {
		var config = config(Map.of());
		assertEquals(Level.WARNING, config.levelResolver().resolveLevel(n.name));
		assertEquals(List.of(), alerts(config));
	}

	@ParameterizedTest
	@EnumSource(LoggerName.class)
	void parentLevel(LoggerName n) {
		String parent = n.parentOrNull();
		if (parent == null) {
			return;
		}
		var config = config(Map.of(levelKey(parent), "debug"));
		Level expected = switch (n) {
			// logging.level.root is the root's alias, and logging.level wins over it.
			case ROOT_CHILD -> Level.WARNING;
			default -> Level.DEBUG;
		};
		assertEquals(expected, config.levelResolver().resolveLevel(n.name));
		assertEquals(List.of(), alerts(config));
	}

	@ParameterizedTest
	@EnumSource(LoggerName.class)
	void exactLevel(LoggerName n) {
		var config = config(Map.of(levelKey(n.name), "debug"));
		Level expected = switch (n) {
			// The key is the root's alias, and logging.level wins over it.
			case ROOT_LOWER -> Level.WARNING;
			default -> Level.DEBUG;
		};
		assertEquals(expected, config.levelResolver().resolveLevel(n.name));
		Level other = n == LoggerName.EMPTY ? Level.DEBUG : Level.WARNING;
		assertEquals(other, config.levelResolver().resolveLevel("zzz.other"));
		assertEquals(List.of(), alerts(config));
	}

	@ParameterizedTest
	@EnumSource(LoggerName.class)
	void groupLevel(LoggerName n) {
		var config = config(Map.of( //
				"logging.groups", "g", //
				"logging.group.g", n.name, //
				"logging.level.g", "debug"));
		Level expected = switch (n) {
			// A list drops an empty element at its start or end, so the group is empty.
			case EMPTY -> Level.WARNING;
			default -> Level.DEBUG;
		};
		assertEquals(expected, config.levelResolver().resolveLevel(n.name));
		assertEquals(Level.WARNING, config.levelResolver().resolveLevel("zzz.other"));
		assertEquals(List.of(), alerts(config));
	}

	@ParameterizedTest
	@EnumSource(LoggerName.class)
	void callerAndChanges(LoggerName n) {
		var config = config(Map.of( //
				"logging.change.mode", "on", //
				"logging.change.logger", "level", //
				"logging.caller.logger", "true"));
		assertEquals(CallerType.BASIC, config.changePublisher().callerType(n.name));
		assertEquals(EnumSet.of(ChangeType.LEVEL), config.changePublisher().allowedChanges(n.name));
		assertEquals(List.of(), alerts(config));
	}

	@ParameterizedTest
	@EnumSource(LoggerName.class)
	void exactCallerAndChanges(LoggerName n) {
		var values = new HashMap<String, String>();
		values.put("logging.change.mode", "on");
		values.put("logging.change.logger", "level");
		values.put("logging.caller.logger", "true");
		values.put(n.name.isEmpty() ? "logging.change.logger" : "logging.change.logger." + n.name, "false");
		values.put(n.name.isEmpty() ? "logging.caller.logger" : "logging.caller.logger." + n.name, "false");
		var config = config(values);
		boolean rootAlias = n == LoggerName.ROOT_LOWER;
		assertEquals(rootAlias ? CallerType.BASIC : CallerType.NONE, config.changePublisher().callerType(n.name));
		assertEquals(rootAlias ? EnumSet.of(ChangeType.LEVEL) : Set.of(),
				config.changePublisher().allowedChanges(n.name));
		assertEquals(List.of(), alerts(config));
	}

	@ParameterizedTest
	@EnumSource(LoggerName.class)
	void encoders(LoggerName n) {
		var outputs = new LinkedHashMap<String, ListLogOutput>();
		var properties = new StringBuilder();
		List<String> encoders = List.of("pattern", "logfmt", "json5", "ecs", "logstash", "gelf", "logback");
		properties.append("logging.appenders=").append(String.join(",", encoders)).append("\n");
		for (String encoder : encoders) {
			outputs.put(encoder, new ListLogOutput());
			properties.append("logging.appender.%1$s.output.uri=list%1$s\n".formatted(encoder));
			properties.append("logging.appender.%1$s.encoder.uri=%1$s\n".formatted(encoder));
		}
		properties.append("logging.appender.gelf.encoder.gelf.host=test\n");
		properties.append("logging.appender.pattern.encoder.pattern.pattern="
				+ "%logger|%logger{0}|%logger{1}|%logger{5}|%logger{10}|%logger{20}%n\n");
		var gum = RainbowGum
			.builder(c -> c.properties(LogProperties.builder().fromProperties(properties.toString()).build())
				.configurator((config, pass) -> {
					outputs.forEach((k, v) -> config.outputRegistry().register("list" + k, r -> LogProvider.of(v)));
					return true;
				}))
			.build();
		try (var g = gum.start()) {
			g.log(LogEventFactory.of(n.name).eventNoArg(Level.ERROR, "m", KeyValues.of(), (Throwable) null));
		}
		assertEquals(n.name + "|" + n.abbreviated + "\n", outputs.get("pattern").toString());
		assertContains(outputs.get("logfmt"), " logger=" + n.logfmt + " ");
		assertContains(outputs.get("json5"), "logger:" + n.json5 + ",");
		assertContains(outputs.get("ecs"), "\"log.logger\":" + n.json + ",");
		assertContains(outputs.get("logstash"), "\"logger_name\":" + n.json + ",");
		assertContains(outputs.get("gelf"), "\"_logger\":" + n.json + ",");
		assertContains(outputs.get("logback"), "\"loggerName\":" + n.json + ",");
	}

	@ParameterizedTest
	@EnumSource(LoggerName.class)
	void slf4j(LoggerName n) {
		var output = new ListLogOutput();
		try (var gum = RainbowGum.builder(c -> c.properties(LogProperties.builder().fromProperties("""
				logging.appenders=list
				logging.appender.list.output.uri=list
				logging.appender.list.encoder.uri=pattern
				logging.appender.list.encoder.pattern.pattern=%level %logger %msg%n
				""").build()).configurator((config, pass) -> {
			config.outputRegistry().register("list", r -> LogProvider.of(output));
			return true;
		})).set()) {
			var logger = LoggerFactory.getLogger(n.name);
			assertEquals(n.name, logger.getName());
			logger.error("m");
		}
		assertEquals("ERROR " + n.name + " m\n", output.toString());
	}

	private static void assertContains(@Nullable ListLogOutput output, String expected) {
		String actual = String.valueOf(output);
		assertTrue(actual.contains(expected), () -> "expected to contain: " + expected + "\nactual: " + actual);
	}

}
