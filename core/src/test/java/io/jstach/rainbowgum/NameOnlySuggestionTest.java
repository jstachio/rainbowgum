package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/*
 * A key that differs from a read key only in a component's name or type may be for a
 * component that was not chosen, so it does not fail startup by default.
 */
class NameOnlySuggestionTest {

	@ParameterizedTest
	@CsvSource({ "logging.appender.out2.output.uri, logging.appender.out1.output.uri, true",
			"logging.route.app2.appenders, logging.route.app1.appenders, true",
			"logging.pattern.config.json2.timestamp, logging.pattern.config.json.timestamp, true",
			"logging.appender.console.output.stderr.stream, logging.appender.console.output.stdout.stream, true",
			"logging.appender.console.encoder.jsn5.prettyPrint, logging.appender.console.encoder.json5.prettyPrint, true",
			"logging.route.default.publisher.asink.bufferSize, logging.route.default.publisher.async.bufferSize, true",
			"logging.appender.console.outptu.uri, logging.appender.console.output.uri, false",
			"logging.appender.Con-sole.output.uri, logging.appender.console.output.uri, false",
			"logging.appender.out2.output.url, logging.appender.out1.output.uri, false",
			"logging.apenders, logging.appenders, false" })
	void differsOnlyInName(String key, String suggestion, boolean expected) {
		assertEquals(expected, UnusedKeyCheck.differsOnlyInName(key, suggestion));
	}

	@Test
	void appenderLeftOutOfAppendersIsInformation() {
		System.setProperty("logging.appender.out2.output.uri", "stderr:///");
		try {
			var config = LogConfig.builder()
				.properties(LogProperties.of(List.of(LogProperties.StandardProperties.SYSTEM_PROPERTIES,
						LogProperties.builder().fromProperties("""
								logging.appenders=out1
								logging.appender.out1.output.uri=stdout:///
								""").build())))
				.build();
			try (var gum = RainbowGum.builder(config).build().start()) {
				var alerts = gum.config()
					.alerts()
					.dump()
					.stream()
					.filter(e -> e.message().contains("logging.appender.out2.output.uri"))
					.toList();
				assertEquals(1, alerts.size(), alerts.toString());
				assertEquals(Level.INFO, alerts.get(0).level());
				assertEquals(
						"""
								Property key 'logging.appender.out2.output.uri' from SYSTEM_PROPERTIES[logging.appender.out2.output.uri] \
								was set but not read during startup. Did you mean 'logging.appender.out1.output.uri'?""",
						alerts.get(0).message());
			}
		}
		finally {
			System.clearProperty("logging.appender.out2.output.uri");
		}
	}

}
