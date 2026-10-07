package io.jstach.rainbowgum.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.KeyValueNullStrategy;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.format.KeyValuesFormatterBuilder.Format;
import io.jstach.rainbowgum.output.ListLogOutput;

class KeyValuesFormatterTest {

	private static LogEvent event(KeyValues keyValues) {
		return LogEvent.of(Instant.EPOCH, "main", 1, Level.INFO, "com.example.App", "hello", keyValues, null);
	}

	private static String log(LogFormatter formatter, KeyValues keyValues) {
		var output = new ListLogOutput();
		try (var gum = RainbowGum.builder()
			.route(r -> r.appender("list", a -> a.output(output).encoder(LogEncoder.of(formatter))))
			.build()
			.start()) {
			gum.log(event(keyValues));
		}
		return output.toString();
	}

	@Test
	void defaultsWriteAllKeysInOrderAndValuesRemainStrings() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("number", "42");
		kvs.putKeyValue("boolean", "true");
		kvs.putKeyValue("textNull", "null");
		kvs.putKeyValue("nested", "{\"x\":1}");
		kvs.putKeyValue("empty", "");
		kvs.putKeyValue("null", null);
		assertEquals("""
				{"number":"42","boolean":"true","textNull":"null","nested":"{\\\"x\\\":1}","empty":"","null":null}""",
				log(new KeyValuesFormatterBuilder().build(), kvs));
	}

	static Stream<Arguments> identifiers() {
		return Stream.of(Arguments.of("requestId", "requestId"), Arguments.of("_id", "_id"), Arguments.of("$id", "$id"),
				Arguments.of("id42", "id42"), Arguments.of("null", "null"), Arguments.of("true", "true"),
				Arguments.of("class", "class"), Arguments.of("café", "café"), Arguments.of("用户", "用户"),
				Arguments.of("𐐀", "𐐀"), Arguments.of("a\u0301", "a\u0301"),
				Arguments.of("a\u200c\u200d", "a\u200c\u200d"), Arguments.of("", "\"\""),
				Arguments.of("42id", "\"42id\""), Arguments.of("user name", "\"user name\""),
				Arguments.of("trace.id", "\"trace.id\""), Arguments.of("request-id", "\"request-id\""),
				Arguments.of("path/to", "\"path/to\""), Arguments.of("quote\"key", "\"quote\\\"key\""),
				Arguments.of("line\nkey", "\"line\\nkey\""), Arguments.of("€price", "\"€price\""),
				Arguments.of("\u0301a", "\"\u0301a\""), Arguments.of("\u200ca", "\"\u200ca\""),
				Arguments.of("😀", "\"😀\""));
	}

	@ParameterizedTest
	@MethodSource("identifiers")
	void json5OnlyLeavesIdentifierKeysUnquoted(String key, String expectedKey) {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue(key, "value");
		assertEquals("{" + expectedKey + ":\"value\"}",
				log(new KeyValuesFormatterBuilder().format(Format.JSON5).build(), kvs));
	}

	@ParameterizedTest
	@EnumSource(Format.class)
	void quotesAndBackslashesAreEscapedInKeysAndValues(Format format) {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("say\"hi\\", "a\"b\\c/d");
		assertEquals("""
				{"say\\\"hi\\\\":"a\\\"b\\\\c/d"}""", log(new KeyValuesFormatterBuilder().format(format).build(), kvs));
	}

	@ParameterizedTest
	@EnumSource(Format.class)
	void allControlCharactersStayOnOneLine(Format format) {
		var controls = new StringBuilder();
		for (char c = 0; c < 32; c++) {
			controls.append(c);
		}
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("controls", controls.toString());
		String key = format == Format.JSON ? "\"controls\"" : "controls";
		assertEquals("{" + key + ":\""
				+ "\\u0000\\u0001\\u0002\\u0003\\u0004\\u0005\\u0006\\u0007\\b\\t\\n\\u000b\\f\\r\\u000e\\u000f"
				+ "\\u0010\\u0011\\u0012\\u0013\\u0014\\u0015\\u0016\\u0017\\u0018\\u0019\\u001a\\u001b\\u001c\\u001d\\u001e\\u001f\"}",
				log(new KeyValuesFormatterBuilder().format(format).build(), kvs));
	}

	@ParameterizedTest
	@EnumSource(Format.class)
	void unicodeSurvivesEncodingAndInvalidSurrogatesAreEscaped(Format format) {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("text", "café 用户 😀\u2028\u2029\ud800x\udc00");
		kvs.putKeyValue("\ud800", "\udfff");
		String key = format == Format.JSON ? "\"text\"" : "text";
		assertEquals("{" + key + ":\"café 用户 😀\\u2028\\u2029\\ud800x\\udc00\",\"\\ud800\":\"\\udfff\"}",
				log(new KeyValuesFormatterBuilder().format(format).build(), kvs));
	}

	@ParameterizedTest
	@EnumSource(KeyValueNullStrategy.class)
	void nullStrategyAppliesToAllKeysWithoutStrayCommas(KeyValueNullStrategy strategy) {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("first", null);
		kvs.putKeyValue("user", "ada");
		kvs.putKeyValue("middle", null);
		kvs.putKeyValue("empty", "");
		kvs.putKeyValue("last", null);
		String expected = switch (strategy) {
			case KEEP -> "{\"first\":null,\"user\":\"ada\",\"middle\":null,\"empty\":\"\",\"last\":null}";
			case EMPTY -> "{\"first\":\"\",\"user\":\"ada\",\"middle\":\"\",\"empty\":\"\",\"last\":\"\"}";
			case SKIP -> "{\"user\":\"ada\",\"empty\":\"\"}";
		};
		assertEquals(expected, log(new KeyValuesFormatterBuilder().nullStrategy(strategy).build(), kvs));
	}

	@ParameterizedTest
	@EnumSource(KeyValueNullStrategy.class)
	void selectionControlsOrderAndMissingKeysFollowTheNullStrategy(KeyValueNullStrategy strategy) {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("empty", "");
		kvs.putKeyValue("user", "ada");
		kvs.putKeyValue("null", null);
		kvs.putKeyValue("ignored", "x");
		String expected = switch (strategy) {
			case KEEP -> "{missing:null,user:\"ada\",null:null,empty:\"\",last:null}";
			case EMPTY -> "{missing:\"\",user:\"ada\",null:\"\",empty:\"\",last:\"\"}";
			case SKIP -> "{user:\"ada\",empty:\"\"}";
		};
		var formatter = new KeyValuesFormatterBuilder().format(Format.JSON5)
			.keys(List.of("missing", "user", "null", "empty", "user", "last"))
			.nullStrategy(strategy)
			.build();
		assertEquals(expected, log(formatter, kvs));
	}

	@ParameterizedTest
	@EnumSource(Format.class)
	void emptyAndFullySkippedSelectionsStillProduceAnObject(Format format) {
		var builder = new KeyValuesFormatterBuilder().format(format);
		assertEquals("{}", log(builder.build(), KeyValues.of()));
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("user", "ada");
		assertEquals("{}", log(builder.keys(List.of()).build(), kvs));
		assertEquals("{}", log(builder.keys(List.of("missing")).nullStrategy(KeyValueNullStrategy.SKIP).build(), kvs));
		kvs.remove("user");
		kvs.putKeyValue("null", null);
		assertEquals("{}", log(builder.allKeys().build(), kvs));
	}

	@Test
	void selectionAndBuiltFormattersAreIndependentOfLaterChanges() {
		var keys = new ArrayList<>(List.of("missing", "user"));
		var builder = new KeyValuesFormatterBuilder().keys(keys);
		keys.clear();
		var formatter = builder.build();
		builder.allKeys().format(Format.JSON5).nullStrategy(KeyValueNullStrategy.SKIP);
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("extra", "x");
		kvs.putKeyValue("user", "ada");
		assertEquals("{\"missing\":null,\"user\":\"ada\"}", log(formatter, kvs));
		assertEquals("{extra:\"x\",user:\"ada\"}", log(builder.build(), kvs));
	}

	@Test
	void composesWithOtherFormattersAndAppendsToExistingText() {
		var formatter = LogFormatter.builder()
			.message()
			.text(" ")
			.add(new KeyValuesFormatterBuilder().format(Format.JSON5).build())
			.newline()
			.build();
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		assertEquals("hello {requestId:\"42\"}\n", log(formatter, kvs));
		var output = new StringBuilder("prefix: ");
		formatter.format(output, event(kvs));
		assertEquals("prefix: hello {requestId:\"42\"}\n", output.toString());
	}

}
