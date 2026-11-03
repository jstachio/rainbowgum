package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/*
 * The read properties report shows what the reads found, without the secrets in it.
 */
class ReadPropertiesTest {

	/*
	 * A source with native lists, like a YAML backed configuration system, has no single
	 * value for a list.
	 */
	private static final LogProperties NATIVE = new LogProperties() {

		@Override
		public @Nullable String valueOrNull(String key) {
			return null;
		}

		@Override
		public @Nullable List<String> listOrNull(String key) {
			return key.equals("logging.appenders") ? List.of("console") : null;
		}

		@Override
		public String description(String key) {
			return "NATIVE";
		}

	};

	@Test
	void nativeListIsShownFromTheLayerThatWon() {
		var check = new UnusedKeyCheck(List.of());
		var fallback = LogProperties.builder().fromProperties("logging.appenders=file").build();
		var properties = LogProperties.of(List.of(check.wrap(NATIVE), check.wrap(fallback)));
		assertEquals(List.of("console"),
				properties.forKey("logging.appenders").ofList().validateNow(ReadPropertiesTest.class));
		assertEquals(List.of(new UnusedKeyCheck.Read("logging.appenders", "[console]", "NATIVE")),
				check.read(properties));
	}

	@Test
	void encodedMapInAUriIsRedacted() {
		var check = new UnusedKeyCheck(List.of());
		String uriKey = "logging.appender.console.output.uri";
		String uri = "otlp://host/?headers=Authorization%3DBearer%20sample-secret";
		var config = check.wrap(LogProperties.builder().fromProperties(uriKey + "=" + uri).build());
		config.valueOrNull(uriKey);
		var properties = LogProperties.of(URI.create(uri), "logging.appender.console.output.otlp.", config, uriKey);
		assertEquals(Map.of("Authorization", "Bearer sample-secret"),
				properties.forKey("logging.appender.console.output.otlp.headers")
					.ofMap()
					.validateNow(ReadPropertiesTest.class));
		var read = check.read(config);
		assertEquals(
				List.of("logging.appender.console.output.otlp.headers = Authorization=<REDACTED>",
						"logging.appender.console.output.uri = otlp://host/?headers=<REDACTED>"),
				read.stream().map(r -> r.key() + " = " + r.value()).toList());
		for (var r : read) {
			assertFalse(String.valueOf(r.description()).contains("sample-secret"), r.toString());
		}
	}

}
