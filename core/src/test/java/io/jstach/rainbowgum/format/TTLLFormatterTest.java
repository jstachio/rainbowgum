package io.jstach.rainbowgum.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.LogReporter;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class TTLLFormatterTest {

	static final Instant TIME = Instant.parse("2026-10-05T12:00:00.123Z");

	private static LogEvent event(KeyValues keyValues) {
		return LogEvent.of(TIME, "main", 7, Level.INFO, "com.example.App", "hello world", keyValues, null);
	}

	private static KeyValues requestKeyValues() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		kvs.putKeyValue("user", "Ada Lovelace");
		return kvs;
	}

	private static String format(io.jstach.rainbowgum.LogFormatter formatter, LogEvent event) {
		var sb = new StringBuilder();
		formatter.format(sb, event);
		return sb.toString();
	}

	/*
	 * Logs one event through a gum whose appender uses the given encoder properties.
	 */
	private static String log(String encoder, String properties, LogEvent event) {
		return log(encoder, properties, event, null);
	}

	private static String log(String encoder, String properties, LogEvent event, TTLL.@Nullable ColorTheme theme) {
		var output = new ListLogOutput();
		String all = "logging.appenders=list\nlogging.appender.list.output.uri=list\n"
				+ (encoder.isEmpty() ? "" : "logging.appender.list.encoder.uri=" + encoder + "\n") + properties;
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(all).build()).build();
		if (theme != null) {
			config.serviceRegistry().put(TTLL.ColorTheme.class, theme.name(), theme);
		}
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(event);
		}
		return output.toString();
	}

	@Test
	void defaultsAreTheClassicLayout() {
		assertEquals("12:00:00.123 [main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world\n",
				format(new TTLLFormatterBuilder("test").build(), event(requestKeyValues())));
	}

	@Test
	void withoutKeyValuesMatchesTheStandardEventFormatter() {
		var event = event(requestKeyValues());
		assertEquals(format(StandardEventFormatter.builder().build(), event), format(
				new TTLLFormatterBuilder("test").keyValues(TTLL.KeyValuesFormat.NONE.formatter()).build(), event));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"timestamp=iso|2026-10-05T12:00:00.123Z [main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"timestamp=none|[main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"timestamp=HH:mm|12:00 [main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"thread=id|12:00:00.123 [7] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"thread=both|12:00:00.123 [7][main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"thread=name_or_id|12:00:00.123 [main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"thread=none|12:00:00.123 INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"level=plain|12:00:00.123 [main] INFO com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"level=bracketed|12:00:00.123 [main] [INFO] com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"level=none|12:00:00.123 [main] com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"logger=short|12:00:00.123 [main] INFO  App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"logger=none|12:00:00.123 [main] INFO  {requestId=42&user=Ada%20Lovelace} - hello world", //
			"keyValues=percent|12:00:00.123 [main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"keyValues=json|12:00:00.123 [main] INFO  com.example.App {\"requestId\":\"42\",\"user\":\"Ada Lovelace\"} - hello world", //
			"keyValues=JSON5|12:00:00.123 [main] INFO  com.example.App {requestId:\"42\",user:\"Ada Lovelace\"} - hello world", //
			"keyValues=true|12:00:00.123 [main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"keyValues=false|12:00:00.123 [main] INFO  com.example.App - hello world", //
			"keyValues=default|12:00:00.123 [main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"timestamp=false|[main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"timestamp=default|12:00:00.123 [main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"thread=false|12:00:00.123 INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"thread=true|12:00:00.123 [main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"level=false|12:00:00.123 [main] com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"level=default|12:00:00.123 [main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"logger=false|12:00:00.123 [main] INFO  {requestId=42&user=Ada%20Lovelace} - hello world", //
			"logger=true|12:00:00.123 [main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"theme=default|12:00:00.123 [main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world" })
	void eachPartIsConfigurableByProperty(String property, String expected) {
		assertEquals(expected + "\n",
				log("ttll", "logging.appender.list.encoder.ttll." + property + "\n", event(requestKeyValues())));
	}

	@Test
	void everyPartNoneLeavesTheMessage() {
		String properties = """
				logging.appender.list.encoder.ttll.timestamp=none
				logging.appender.list.encoder.ttll.thread=none
				logging.appender.list.encoder.ttll.level=none
				logging.appender.list.encoder.ttll.logger=none
				""";
		assertEquals("{requestId=42&user=Ada%20Lovelace} - hello world\n",
				log("ttll", properties, event(requestKeyValues())));
		assertEquals("hello world\n", log("ttll", properties + "logging.appender.list.encoder.ttll.keyValues=none\n",
				event(requestKeyValues())));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"none|auto|", "none|show|", "none|omit|", //
			"percent|auto|", "percent|omit|", "percent|show|' {}'", //
			"json5|auto|", "json5|omit|", "json5|show|' {}'", //
			"json|auto|' {}'", "json|show|' {}'", //
			"percent|default|", "percent|false|", "percent|true|' {}'" })
	void emptyKeyValuesFollowKeyValuesWhenEmpty(String format, String whenEmpty, @Nullable String braces) {
		String properties = "logging.appender.list.encoder.ttll.keyValues=" + format
				+ "\nlogging.appender.list.encoder.ttll.keyValuesWhenEmpty=" + whenEmpty + "\n";
		String b = braces == null ? "" : braces;
		assertEquals("12:00:00.123 [main] INFO  com.example.App" + b + " - hello world\n",
				log("ttll", properties, event(KeyValues.of())));
		String colored = b.isEmpty() ? "" : " " + E + "2;39m{}" + R;
		assertEquals(coloredLine("1;34", "INFO ", colored),
				log("ttll", properties + "logging.appender.list.encoder.ttll.color=force\n", event(KeyValues.of())));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"level,timestamp,logger,keyValues|INFO  12:00:00.123 com.example.App {requestId=42&user=Ada%20Lovelace} - hello world|INFO  12:00:00.123 com.example.App - hello world", //
			"keyValues,level,logger|{requestId=42&user=Ada%20Lovelace} INFO  com.example.App - hello world|INFO  com.example.App - hello world", //
			"timestamp,key_values,logger|12:00:00.123 {requestId=42&user=Ada%20Lovelace} com.example.App - hello world|12:00:00.123 com.example.App - hello world", //
			"keyValues|{requestId=42&user=Ada%20Lovelace} - hello world|hello world", //
			"time,thread|12:00:00.123 [main] - hello world|12:00:00.123 [main] - hello world", //
			"logger, LEVEL|com.example.App INFO  - hello world|com.example.App INFO  - hello world" })
	void orderPropertyListsThePartsInOrder(String order, String expected, String expectedWithoutKeyValues) {
		String properties = "logging.appender.list.encoder.ttll.order=" + order + "\n";
		assertEquals(expected + "\n", log("ttll", properties, event(requestKeyValues())));
		assertEquals(expectedWithoutKeyValues + "\n", log("ttll", properties, event(KeyValues.of())));
	}

	@Test
	void orderWithKeyValuesFirstSkipsTheirSpaceWhenThereAreNone() {
		String properties = """
				logging.appender.list.encoder.ttll.order=keyValues,timestamp,logger
				logging.appender.list.encoder.ttll.keyValues=json
				""";
		assertEquals("{} 12:00:00.123 com.example.App - hello world\n", log("ttll", properties, event(KeyValues.of())));
		assertEquals("12:00:00.123 com.example.App - hello world\n",
				log("ttll", properties.replace("json", "json5"), event(KeyValues.of())));
	}

	@Test
	void orderWithKeyValuesFirstIsColored() {
		String actual = log("ttll", """
				logging.appender.list.encoder.ttll.order=keyValues,level
				logging.appender.list.encoder.ttll.color=force
				""", event(requestKeyValues()));
		assertEquals(
				E + "2;39m{requestId=42&user=Ada%20Lovelace}" + R + " " + E + "1;34mINFO " + R + " - hello world\n",
				actual);
	}

	@Test
	void orderGivenToTheBuilder() {
		var formatter = new TTLLFormatterBuilder("test").order(List.of(TTLL.Part.LOGGER, TTLL.Part.THREAD)).build();
		assertEquals("com.example.App [main] - hello world\n", format(formatter, event(requestKeyValues())));
	}

	@Test
	void orderListingAPartTwiceFails() {
		var properties = LogProperties.builder()
			.fromProperties("logging.appender.list.encoder.ttll.order=level,logger,level")
			.build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new TTLLFormatterBuilder("list").fromProperties(properties).build());
		assertEquals("""
				Validation failed for io.jstach.rainbowgum.format.TTLLFormatterBuilder: \
				order lists level more than once: [level, logger, level]""", e.getMessage());
	}

	@Test
	void invalidOrderPartFails() {
		var properties = LogProperties.builder()
			.fromProperties("logging.appender.list.encoder.ttll.order=level,message")
			.build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new TTLLFormatterBuilder("list").fromProperties(properties));
		String expected = """
				Validation failed for io.jstach.rainbowgum.format.TTLLFormatterBuilder:
				Error for property. key: 'logging.appender.list.encoder.ttll.order' from PROPERTIES_STRING[logging.appender.list.encoder.ttll.order], \
				'message' is not a valid value for io.jstach.rainbowgum.format.TTLL.Part. \
				Valid values: 'timestamp', 'thread', 'level', 'logger', 'key_values', 'time', 'keyvalues'""";
		assertEquals(expected, e.getMessage());
	}

	@Test
	void omitWithJsonFails() {
		var properties = LogProperties.builder()
			.fromProperties(
					"logging.appender.list.encoder.ttll.keyValues=json\nlogging.appender.list.encoder.ttll.keyValuesWhenEmpty=omit")
			.build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new TTLLFormatterBuilder("list").fromProperties(properties).build());
		assertEquals("""
				Validation failed for io.jstach.rainbowgum.format.TTLLFormatterBuilder: \
				keyValuesWhenEmpty=omit cannot be used with keyValues=json, since JSON readers expect an object on \
				every line. Use keyValues=json5 or keyValues=percent, or keyValuesWhenEmpty=auto or show.""",
				e.getMessage());
	}

	@Test
	void everyPartNoneWithoutKeyValuesIsJustTheMessage() {
		String properties = """
				logging.appender.list.encoder.ttll.timestamp=none
				logging.appender.list.encoder.ttll.thread=none
				logging.appender.list.encoder.ttll.level=none
				logging.appender.list.encoder.ttll.logger=none
				""";
		assertEquals("hello world\n", log("ttll", properties, event(KeyValues.of())));
		assertEquals("{} - hello world\n", log("ttll",
				properties + "logging.appender.list.encoder.ttll.keyValuesWhenEmpty=show\n", event(KeyValues.of())));
	}

	@ParameterizedTest
	@EnumSource(value = TTLL.KeyValuesFormat.class, names = { "JSON", "JSON5" })
	void jsonKeyValuesWorkWithTheDefaultEncoderAndUriQuery(TTLL.KeyValuesFormat format) {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		kvs.putKeyValue("user name", "Ada\n\"Lovelace\"");
		kvs.putKeyValue("missing", null);
		String object = format == TTLL.KeyValuesFormat.JSON
				? "{\"requestId\":\"42\",\"user name\":\"Ada\\n\\\"Lovelace\\\"\",\"missing\":null}"
				: "{requestId:\"42\",\"user name\":\"Ada\\n\\\"Lovelace\\\"\",missing:null}";
		String expected = "12:00:00.123 [main] INFO  com.example.App " + object + " - hello world\n";
		assertEquals(expected,
				log("", "logging.appender.list.encoder.ttll.keyValues=" + format.name() + "\n", event(kvs)));
		assertEquals(expected, log("ttll:///?keyValues=" + format.name(), "", event(kvs)));
	}

	@ParameterizedTest
	@EnumSource(value = TTLL.KeyValuesFormat.class, names = { "JSON", "JSON5" })
	void jsonChoicesWorkWithTheBuilderAndColorTheWholeObject(TTLL.KeyValuesFormat format) {
		var formatter = new TTLLFormatterBuilder("list").keyValues(format.formatter())
			.color(TTLL.ColorMode.FORCE)
			.build();
		var output = new ListLogOutput();
		try (var gum = RainbowGum.builder()
			.route(r -> r.appender("list", a -> a.output(output).encoder(LogEncoder.of(formatter))))
			.build()
			.start()) {
			gum.log(event(requestKeyValues()));
		}
		String object = format == TTLL.KeyValuesFormat.JSON ? "{\"requestId\":\"42\",\"user\":\"Ada Lovelace\"}"
				: "{requestId:\"42\",user:\"Ada Lovelace\"}";
		assertEquals(coloredLine("1;34", "INFO ", " " + E + "2;39m" + object + R), output.toString());
	}

	@Test
	void uriQueryConfiguresTheEncoder() {
		assertEquals(
				"12:00:00.123 [main] INFO  com.example.App {requestId:\"42\",user:\"Ada Lovelace\"} - hello world\n",
				log("ttll:///?keyValues=json5", "", event(requestKeyValues())));
	}

	@Test
	void defaultEncoderHonorsTheProperties() {
		assertEquals("12:00:00.123 [main] INFO  App {requestId:\"42\",user:\"Ada Lovelace\"} - hello world\n", log("",
				"logging.appender.list.encoder.ttll.keyValues=json5\nlogging.appender.list.encoder.ttll.logger=short\n",
				event(requestKeyValues())));
	}

	@Test
	void nameOrIdWritesTheIdWhenTheNameIsEmpty() {
		var unnamed = LogEvent.of(TIME, "", 7, Level.INFO, "com.example.App", "hello world", KeyValues.of(), null);
		assertEquals("12:00:00.123 [7] INFO  com.example.App - hello world\n",
				log("ttll", "logging.appender.list.encoder.ttll.thread=name_or_id\n", unnamed));
		assertEquals("12:00:00.123 [] INFO  com.example.App - hello world\n",
				log("ttll", "logging.appender.list.encoder.ttll.thread=name\n", unnamed));
	}

	@Test
	void bothThreadIsColoredAsOnePart() {
		String actual = log("ttll",
				"logging.appender.list.encoder.ttll.color=force\nlogging.appender.list.encoder.ttll.thread=both\n",
				event(KeyValues.of()));
		assertEquals("\u001B[2;39m[7][main]\u001B[0;39m", actual.substring(actual.indexOf("\u001B[2;39m[7]"),
				actual.indexOf("[main]") + "[main]\u001B[0;39m".length()));
	}

	@Test
	void invalidPartNameFails() {
		var properties = LogProperties.builder()
			.fromProperties("logging.appender.list.encoder.ttll.thread=group")
			.build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new TTLLFormatterBuilder("list").fromProperties(properties));
		String expected = """
				Validation failed for io.jstach.rainbowgum.format.TTLLFormatterBuilder:
				Error for property. key: 'logging.appender.list.encoder.ttll.thread' from PROPERTIES_STRING[logging.appender.list.encoder.ttll.thread], \
				'group' is not a valid value for io.jstach.rainbowgum.format.TTLL.ThreadFormat. Valid values: 'name', 'id', 'name_or_id', 'both', 'none', 'true', 'false', 'default'""";
		assertEquals(expected, e.getMessage());
	}

	@Test
	void invalidKeyValuesFails() {
		var properties = LogProperties.builder()
			.fromProperties("logging.appender.list.encoder.ttll.keyValues=yes")
			.build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new TTLLFormatterBuilder("list").fromProperties(properties));
		String expected = """
				Validation failed for io.jstach.rainbowgum.format.TTLLFormatterBuilder:
				Error for property. key: 'logging.appender.list.encoder.ttll.keyValues' from PROPERTIES_STRING[logging.appender.list.encoder.ttll.keyValues], \
				'yes' is not a valid value for io.jstach.rainbowgum.format.TTLL.KeyValuesFormat. \
				Valid values: 'none', 'percent', 'json', 'json5', 'true', 'false', 'default'""";
		assertEquals(expected, e.getMessage());
	}

	@Test
	void invalidTimestampPatternFails() {
		var properties = LogProperties.builder()
			.fromProperties("logging.appender.list.encoder.ttll.timestamp=bogus{")
			.build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new TTLLFormatterBuilder("list").fromProperties(properties));
		String expected = """
				Validation failed for io.jstach.rainbowgum.format.TTLLFormatterBuilder:
				Error for property. key: 'logging.appender.list.encoder.ttll.timestamp' from PROPERTIES_STRING[logging.appender.list.encoder.ttll.timestamp], \
				'bogus{' is neither a timestamp format (ttll, iso, none, true, false, default) nor a valid DateTimeFormatter pattern: \
				Unknown pattern letter: b""";
		assertEquals(expected, e.getMessage());
	}

	static final String E = "\033[";

	static final String R = E + "0;39m";

	private static String coloredLine(String levelCode, String level, String keyValues) {
		return E + "36m12:00:00.123" + R + " " + E + "2;39m[main]" + R + " " + E + levelCode + "m" + level + R + " " + E
				+ "35mcom.example.App" + R + keyValues + " - hello world\n";
	}

	private static String springLine(String levelCode, String level, String keyValues) {
		return E + "2;39m12:00:00.123" + R + " " + E + "2;39m[main]" + R + " " + E + levelCode + "m" + level + R + " "
				+ E + "36mcom.example.App" + R + keyValues + " - hello world\n";
	}

	@Test
	void rainbowgumTheme() {
		String actual = log("ttll", "logging.appender.list.encoder.ttll.color=force\n", event(requestKeyValues()));
		String keyValues = " " + E + "2;39m{requestId=42&user=Ada%20Lovelace}" + R;
		assertEquals(coloredLine("1;34", "INFO ", keyValues), actual);
	}

	@Test
	void rainbowgumThemeWithoutKeyValuesLeavesBracesOut() {
		String actual = log("ttll", "logging.appender.list.encoder.ttll.color=force\n", event(KeyValues.of()));
		assertEquals(coloredLine("1;34", "INFO ", ""), actual);
	}

	@ParameterizedTest
	@CsvSource({ "ERROR,1;31,ERROR", "WARNING,1;31,'WARN '", "INFO,1;34,'INFO '", "DEBUG,39,DEBUG", "TRACE,39,TRACE" })
	void levelsAreHighlightedLikeThePatternEncoder(Level level, String code, String text) {
		var event = LogEvent.of(TIME, "main", 7, level, "com.example.App", "hello world", KeyValues.of(), null);
		var output = new ListLogOutput();
		String all = """
				logging.level=TRACE
				logging.appenders=list
				logging.appender.list.output.uri=list
				logging.appender.list.encoder.uri=ttll
				logging.appender.list.encoder.ttll.color=force
				""";
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(all).build()).build();
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(event);
		}
		assertEquals(coloredLine(code, text, ""), output.toString());
	}

	@ParameterizedTest
	@CsvSource({ "ERROR,31,ERROR", "WARNING,33,'WARN '", "INFO,32,'INFO '", "DEBUG,32,DEBUG", "TRACE,32,TRACE" })
	void springThemeColorsLevelsLikeSpringBoot(Level level, String code, String text) {
		var event = LogEvent.of(TIME, "main", 7, level, "com.example.App", "hello world", KeyValues.of(), null);
		assertEquals(springLine(code, text, ""), format(
				new TTLLFormatterBuilder("test").color(TTLL.ColorMode.FORCE).theme(TTLL.ColorTheme.SPRING).build(),
				event));
	}

	@Test
	void springThemeByProperty() {
		String actual = log("ttll",
				"logging.appender.list.encoder.ttll.color=force\nlogging.appender.list.encoder.ttll.theme=spring\n",
				event(requestKeyValues()));
		String keyValues = " " + E + "2;39m{requestId=42&user=Ada%20Lovelace}" + R;
		assertEquals(springLine("32", "INFO ", keyValues), actual);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "ERROR|1;38;2;224;108;117|ERROR", "WARNING|1;38;2;229;192;123|'WARN '",
			"INFO|1;38;2;97;175;239|'INFO '", "DEBUG|38;2;171;178;191|DEBUG", "TRACE|38;2;171;178;191|TRACE" })
	void oneDarkThemeUsesTrueColor(Level level, String code, String text) {
		var event = LogEvent.of(TIME, "main", 7, level, "com.example.App", "hello world", KeyValues.of(), null);
		String expected = E + "38;2;86;182;194m12:00:00.123" + R + " " + E + "38;2;150;152;150m[main]" + R + " " + E
				+ code + "m" + text + R + " " + E + "38;2;198;120;221mcom.example.App" + R + " - hello world\n";
		assertEquals(expected, format(
				new TTLLFormatterBuilder("test").color(TTLL.ColorMode.FORCE).theme(TTLL.ColorTheme.ONE_DARK).build(),
				event));
	}

	@Test
	void oneDarkThemeByProperty() {
		String actual = log("ttll",
				"logging.appender.list.encoder.ttll.color=force\nlogging.appender.list.encoder.ttll.theme=one_dark\n",
				event(requestKeyValues()));
		String expected = E + "38;2;86;182;194m12:00:00.123" + R + " " + E + "38;2;150;152;150m[main]" + R + " " + E
				+ "1;38;2;97;175;239mINFO " + R + " " + E + "38;2;198;120;221mcom.example.App" + R + " " + E
				+ "38;2;150;152;150m{requestId=42&user=Ada%20Lovelace}" + R + " - hello world\n";
		assertEquals(expected, actual);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "ERROR|1;38;2;255;107;104|ERROR", "WARNING|1;38;2;187;181;41|'WARN '",
			"INFO|1;38;2;106;135;89|'INFO '", "DEBUG|38;2;169;183;198|DEBUG", "TRACE|38;2;169;183;198|TRACE" })
	void darculaThemeUsesTrueColor(Level level, String code, String text) {
		var event = LogEvent.of(TIME, "main", 7, level, "com.example.App", "hello world", KeyValues.of(), null);
		String expected = E + "38;2;104;151;187m12:00:00.123" + R + " " + E + "38;2;128;128;128m[main]" + R + " " + E
				+ code + "m" + text + R + " " + E + "38;2;204;120;50mcom.example.App" + R + " - hello world\n";
		assertEquals(expected, format(
				new TTLLFormatterBuilder("test").color(TTLL.ColorMode.FORCE).theme(TTLL.ColorTheme.DARCULA).build(),
				event));
	}

	@Test
	void darculaThemeByProperty() {
		String actual = log("ttll",
				"logging.appender.list.encoder.ttll.color=force\nlogging.appender.list.encoder.ttll.theme=darcula\n",
				event(requestKeyValues()));
		String expected = E + "38;2;104;151;187m12:00:00.123" + R + " " + E + "38;2;128;128;128m[main]" + R + " " + E
				+ "1;38;2;106;135;89mINFO " + R + " " + E + "38;2;204;120;50mcom.example.App" + R + " " + E
				+ "38;2;128;128;128m{requestId=42&user=Ada%20Lovelace}" + R + " - hello world\n";
		assertEquals(expected, actual);
	}

	@ParameterizedTest
	@CsvSource(nullValues = "null", value = { //
			"OFF,null,false,OFF", "OFF,SPRING,true,OFF", //
			"DEFAULT,null,false,OFF", "DEFAULT,null,true,RAINBOWGUM", "DEFAULT,SPRING,false,OFF",
			"DEFAULT,SPRING,true,SPRING", //
			"DETECT,null,false,OFF", "DETECT,null,true,OFF", "DETECT,SPRING,false,OFF", "DETECT,SPRING,true,SPRING", //
			"FORCE,null,false,RAINBOWGUM", "FORCE,SPRING,false,SPRING", "FORCE,DARCULA,true,DARCULA" })
	void colorModeDecidesWhetherAndThemeDecidesWhich(TTLL.ColorMode mode, @Nullable String themeName, boolean ansi,
			String expected) {
		TTLL.@Nullable ColorTheme theme = themeName == null ? null : TTLLFormatter.convertTheme(themeName);
		var palette = switch (expected) {
			case "RAINBOWGUM" -> Palette.RAINBOWGUM;
			case "SPRING" -> Palette.SPRING;
			case "DARCULA" -> Palette.DARCULA;
			default -> Palette.OFF;
		};
		assertSame(palette, Palette.of(mode, theme, () -> ansi));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"|ttll color=default theme=rainbowgum colored=false", //
			"logging.appender.list.encoder.ttll.color=force|ttll color=force theme=rainbowgum colored=true", //
			"logging.appender.list.encoder.ttll.color=force;logging.appender.list.encoder.ttll.theme=spring|ttll color=force theme=spring colored=true", //
			"logging.appender.list.encoder.ttll.theme=darcula|ttll color=default theme=darcula colored=false", //
			"logging.appender.list.encoder.ttll.color=detect|ttll color=detect theme=none colored=false", //
			"logging.appender.list.encoder.ttll.color=off|ttll color=off theme=none colored=false" })
	void reportsColorSettingsAndWhetherTheyColored(@Nullable String properties, String expected) {
		var props = LogProperties.builder()
			.fromProperties(properties == null ? "" : properties.replace(';', '\n'))
			.build();
		var formatter = new TTLLFormatterBuilder("list").fromProperties(props).build();
		assertEquals(expected, LogReporter.Reportable.toString(formatter, "not reportable"));
	}

	@Test
	void encoderReportIncludesTheDescription() {
		var formatter = new TTLLFormatterBuilder("list").color(TTLL.ColorMode.FORCE)
			.theme(TTLL.ColorTheme.ONE_DARK)
			.build();
		var encoder = LogEncoder.of(formatter).provide("list", LogConfig.builder().build());
		assertEquals(
				"contentType=text/plain; charset=UTF-8, description=\"ttll color=force theme=one_dark colored=true\"",
				LogReporter.Reportable.toString(encoder, "not reportable"));
	}

	@Test
	void themeAloneStillDetectsAnsi() {
		// Tests run without a console, so ANSI is never detected.
		assertEquals("12:00:00.123 [main] INFO  com.example.App - hello world\n",
				log("ttll", "logging.appender.list.encoder.ttll.theme=spring\n", event(KeyValues.of())));
	}

	@Test
	void trueAndFalseAreAliases() {
		var event = event(requestKeyValues());
		assertEquals(log("ttll", "logging.appender.list.encoder.ttll.color=default\n", event),
				log("ttll", "logging.appender.list.encoder.ttll.color=true\n", event));
		assertEquals("12:00:00.123 [main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world\n",
				log("ttll", "logging.appender.list.encoder.ttll.color=false\n", event));
		assertEquals("12:00:00.123 [main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world\n",
				log("ttll", "logging.appender.list.encoder.ttll.color=off\n", event));
	}

	@Test
	void explicitColorWinsOverGlobalAnsiOff() {
		String actual = log("ttll", "logging.global.ansi.mode=off\nlogging.appender.list.encoder.ttll.color=force\n",
				event(KeyValues.of()));
		assertEquals(coloredLine("1;34", "INFO ", ""), actual);
	}

	@Test
	void nonConsoleDefaultEncoderHonorsColorProperty() {
		// ListLogOutput is not a console output, so its default TTLL is off unless set.
		assertEquals(coloredLine("1;34", "INFO ", ""),
				log("", "logging.appender.list.encoder.ttll.color=force\n", event(KeyValues.of())));
	}

	@Test
	void invalidColorFails() {
		var properties = LogProperties.builder()
			.fromProperties("logging.appender.list.encoder.ttll.color=pink")
			.build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new TTLLFormatterBuilder("list").fromProperties(properties));
		String expected = """
				Validation failed for io.jstach.rainbowgum.format.TTLLFormatterBuilder:
				Error for property. key: 'logging.appender.list.encoder.ttll.color' from PROPERTIES_STRING[logging.appender.list.encoder.ttll.color], \
				'pink' is not a valid value for io.jstach.rainbowgum.format.TTLL.ColorMode. \
				Valid values: 'off', 'default', 'detect', 'force', 'true', 'false'""";
		assertEquals(expected, e.getMessage());
	}

	@Test
	void invalidThemeFails() {
		var properties = LogProperties.builder()
			.fromProperties("logging.appender.list.encoder.ttll.theme=true")
			.build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new TTLLFormatterBuilder("list").fromProperties(properties).build());
		String expected = """
				Validation failed for io.jstach.rainbowgum.format.TTLLFormatterBuilder: \
				'true' is not a valid value for io.jstach.rainbowgum.format.TTLL.ColorTheme. \
				Valid values: 'rainbowgum', 'spring', 'one_dark', 'darcula', 'default'""";
		assertEquals(expected, e.getMessage());
	}

	static final TTLL.ColorTheme MINE = TTLL.ColorTheme.builder("mine")
		.from(TTLL.ColorTheme.SPRING)
		.logger("35")
		.level(Level.INFO, "1;34")
		.build();

	@ParameterizedTest
	@EnumSource(value = Level.class, names = { "ERROR", "WARNING", "INFO", "DEBUG", "TRACE" })
	void partialThemeColorsOnlyItsSelectedLevel(Level coloredLevel) {
		var theme = TTLL.ColorTheme.builder("partial").level(coloredLevel, "35").build();
		for (String scheme : java.util.List.of("ttll", "logfmt", "json5")) {
			String properties = "logging.level=trace\nlogging.appender.list.encoder." + scheme + ".color=force\n"
					+ "logging.appender.list.encoder." + scheme + ".theme=partial\n";
			for (Level level : java.util.List.of(Level.ERROR, Level.WARNING, Level.INFO, Level.DEBUG, Level.TRACE)) {
				var event = LogEvent.of(TIME, "main", 7, level, "com.example.App", "hello world", KeyValues.of(), null);
				String text = level == Level.WARNING ? "WARN" : level.name();
				String start = level == coloredLevel ? E + "35m" : "";
				String end = level == coloredLevel ? R : "";
				String expected = switch (scheme) {
					case "ttll" -> """
							12:00:00.123 [main] LEVEL com.example.App - hello world
							""".replace("LEVEL", start + String.format(Locale.ROOT, "%-5s", text) + end);
					case "logfmt" ->
						"""
								time=2026-10-05T12:00:00.123Z level=LEVEL logger=com.example.App threadId=7 threadName=main msg="hello world"
								"""
							.replace("LEVEL", start + text + end);
					case "json5" ->
						"""
								{time:"2026-10-05T12:00:00.123Z",level:LEVEL,logger:"com.example.App",threadId:"7",threadName:"main",msg:"hello world"}
								"""
							.replace("LEVEL", start + "\"" + text + "\"" + end + (text.length() < 5 ? " " : ""));
					default -> throw new AssertionError(scheme);
				};
				assertEquals(expected, log(scheme, properties, event, theme),
						scheme + ": " + coloredLevel + "/" + level);
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "timestamp", "thread", "logger", "keyValues", "error", "warn", "info", "debug", "trace", "none" })
	void reportsAnyConfiguredColorInAPartialTheme(String part) {
		var builder = TTLL.ColorTheme.builder("partial");
		switch (part) {
			case "timestamp" -> builder.timestamp("35");
			case "thread" -> builder.thread("35");
			case "logger" -> builder.logger("35");
			case "keyValues" -> builder.keyValues("35");
			case "error" -> builder.level(Level.ERROR, "35");
			case "warn" -> builder.level(Level.WARNING, "35");
			case "info" -> builder.level(Level.INFO, "35");
			case "debug" -> builder.level(Level.DEBUG, "35");
			case "trace" -> builder.level(Level.TRACE, "35");
			case "none" -> {
			}
			default -> throw new AssertionError(part);
		}
		var formatter = new TTLLFormatterBuilder("test").color(TTLL.ColorMode.FORCE).theme(builder.build()).build();
		assertEquals("ttll color=force theme=partial colored=" + !part.equals("none"),
				LogReporter.Reportable.toString(formatter, "not reportable"));
	}

	@Test
	void customThemeGivenToTheBuilder() {
		var formatter = new TTLLFormatterBuilder("test").color(TTLL.ColorMode.FORCE).theme(MINE).build();
		String expected = E + "2;39m12:00:00.123" + R + " " + E + "2;39m[main]" + R + " " + E + "1;34mINFO " + R + " "
				+ E + "35mcom.example.App" + R + " " + E + "2;39m{requestId=42&user=Ada%20Lovelace}" + R
				+ " - hello world\n";
		assertEquals(expected, format(formatter, event(requestKeyValues())));
	}

	@Test
	void registeredThemeChosenByProperty() {
		var output = new ListLogOutput();
		String all = """
				logging.appenders=list
				logging.appender.list.output.uri=list
				logging.appender.list.encoder.uri=ttll
				logging.appender.list.encoder.ttll.color=force
				logging.appender.list.encoder.ttll.theme=mine
				""";
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(all).build()).build();
		config.serviceRegistry().put(TTLL.ColorTheme.class, MINE.name(), MINE);
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(event(requestKeyValues()));
		}
		assertEquals(format(new TTLLFormatterBuilder("test").color(TTLL.ColorMode.FORCE).theme(MINE).build(),
				event(requestKeyValues())), output.toString());
	}

	/*
	 * Logfmt and JSON5 color with themes too, so they find registered themes the same
	 * way.
	 */
	@ParameterizedTest
	@CsvSource({ "logfmt", "json5" })
	void registeredThemeChosenByPropertyInOtherFormats(String scheme) {
		var output = new ListLogOutput();
		String all = """
				logging.appenders=list
				logging.appender.list.output.uri=list
				logging.appender.list.encoder.uri=SCHEME
				logging.appender.list.encoder.SCHEME.color=force
				logging.appender.list.encoder.SCHEME.theme=mine
				""".replace("SCHEME", scheme);
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(all).build()).build();
		config.serviceRegistry().put(TTLL.ColorTheme.class, MINE.name(), MINE);
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(event(requestKeyValues()));
		}
		var expected = scheme.equals("logfmt")
				? new LogfmtFormatterBuilder("test").color(TTLL.ColorMode.FORCE).theme(MINE).build()
				: new Json5FormatterBuilder("test").color(TTLL.ColorMode.FORCE).theme(MINE).build();
		assertEquals(format(expected, event(requestKeyValues())), output.toString());
	}

	@Test
	void unknownThemeNameListsRegisteredThemes() {
		var registry = io.jstach.rainbowgum.ServiceRegistry.of();
		registry.put(TTLL.ColorTheme.class, MINE.name(), MINE);
		var properties = LogProperties.builder()
			.fromProperties("logging.appender.list.encoder.ttll.theme=minee")
			.build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new TTLLFormatterBuilder("list").serviceRegistry(registry).fromProperties(properties).build());
		String expected = """
				Validation failed for io.jstach.rainbowgum.format.TTLLFormatterBuilder: \
				'minee' is not a valid value for io.jstach.rainbowgum.format.TTLL.ColorTheme. \
				Valid values: 'rainbowgum', 'spring', 'one_dark', 'darcula', 'default', 'mine'""";
		assertEquals(expected, e.getMessage());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"Mine|Color theme name 'Mine' should be lowercase letters, digits, and underscores, starting with a letter.",
			"one dark|Color theme name 'one dark' should be lowercase letters, digits, and underscores, starting with a letter.",
			"1st|Color theme name '1st' should be lowercase letters, digits, and underscores, starting with a letter.",
			"spring|Color theme name 'spring' is taken by a built in theme.",
			"default|Color theme name 'default' is taken by a built in theme." })
	void themeNamesThatAreNotAllowed(String name, String expected) {
		var e = assertThrows(IllegalArgumentException.class, () -> TTLL.ColorTheme.builder(name));
		assertEquals(expected, e.getMessage());
	}

	@Test
	void onlyLevelsWithColorsCanBeSet() {
		var e = assertThrows(IllegalArgumentException.class,
				() -> TTLL.ColorTheme.builder("mine").level(Level.ALL, "31"));
		assertEquals("Level ALL has no color. Use error, warning, info, debug, or trace.", e.getMessage());
	}

}
