package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/*
 * A sensitive property is a secret its component marked as one, so it is redacted by key
 * rather than by how its name looks, and it cannot come from a URI query.
 */
class SensitivePropertyTest {

	private static final String PREFIX = "logging.appender.console.output.mapped.";

	private static final String URI_KEY = "logging.appender.console.output.uri";

	@Test
	void valueFromTheUriQueryIsRefused() {
		var properties = LogProperties.of(URI.create("mapped:///?team=sample-secret"), PREFIX,
				LogProperties.builder().fromProperties("").build(), URI_KEY);
		var result = properties.forKey(PREFIX + "team").sensitive().ofString();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> result.validateNow(SensitivePropertyTest.class));
		assertEquals("""
				Validation failed for io.jstach.rainbowgum.SensitivePropertyTest:
				Error for property. key: 'logging.appender.console.output.mapped.team' is sensitive, so it cannot be \
				set in the query of the URI of 'logging.appender.console.output.uri'. Set \
				'logging.appender.console.output.mapped.team' as its own property instead.""", e.getMessage());
	}

	@Test
	void valueFromAPropertyIsUsed() {
		var properties = LogProperties.of(URI.create("mapped:///"), PREFIX,
				LogProperties.builder().fromProperties(PREFIX + "team=sample-secret").build(), URI_KEY);
		assertEquals("sample-secret",
				properties.forKey(PREFIX + "team").sensitive().ofString().validateNow(SensitivePropertyTest.class));
	}

	@Test
	void uriQueryIsRefusedEvenWhenAPropertyWins() {
		var properties = LogProperties.of(URI.create("mapped:///?headers.X-Team=sample-secret"), PREFIX,
				LogProperties.builder().fromProperties(PREFIX + "headers=X-Team=core").build(), URI_KEY);
		var result = properties.forKey(PREFIX + "headers").sensitive().ofMap();
		assertThrows(LogProperty.ValidationException.class, () -> result.validateNow(SensitivePropertyTest.class));
	}

	@Test
	void notSensitiveValueFromTheUriQueryIsUsed() {
		var properties = LogProperties.of(URI.create("mapped:///?team=core"), PREFIX,
				LogProperties.builder().fromProperties("").build(), URI_KEY);
		assertEquals("core", properties.forKey(PREFIX + "team").ofString().validateNow(SensitivePropertyTest.class));
	}

	@Test
	void fallbackKeyIsSensitiveToo() {
		var properties = LogProperties.of(URI.create("mapped:///?old=sample-secret"), PREFIX,
				LogProperties.builder().fromProperties("").build(), URI_KEY);
		var result = properties.forKey(PREFIX + "team").sensitive().or(PREFIX + "old").ofString();
		assertThrows(LogProperty.ValidationException.class, () -> result.validateNow(SensitivePropertyTest.class));
	}

	@Test
	void conversionErrorDoesNotShowTheValue() {
		var properties = LogProperties.builder().fromProperties(PREFIX + "team=sample-secret").build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> properties.forKey(PREFIX + "team").sensitive().ofInt().validateNow(SensitivePropertyTest.class));
		String message = String.valueOf(e.getMessage());
		assertFalse(message.contains("sample-secret"), message);
		assertTrue(message.contains("the value is not shown because it is sensitive"), message);
		assertFalse(stackTrace(e).contains("sample-secret"), stackTrace(e));
		assertEquals("Property[" + PREFIX + "team]=<REDACTED>",
				properties.forKey(PREFIX + "team").sensitive().ofString().describe());
	}

	@Test
	void sensitiveConversionsKeepFramesButDiscardUnsafeThrowableDetails() {
		var original = new IllegalArgumentException("sample-secret", new Exception("cause-secret"));
		original.addSuppressed(new Exception("suppressed-secret"));
		var properties = LogProperties.builder().fromProperties(PREFIX + "team=sample-secret").build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> properties.forKey(PREFIX + "team").sensitive().ofString().map(value -> {
					throw original;
				}).validateNow(getClass()));
		String trace = stackTrace(e);
		assertFalse(trace.contains("sample-secret"), trace);
		assertFalse(trace.contains("cause-secret"), trace);
		assertFalse(trace.contains("suppressed-secret"), trace);
		assertTrue(trace.contains(IllegalArgumentException.class.getName()), trace);
		var cause = e.getCause();
		assertNotNull(cause);
		assertArrayEquals(original.getStackTrace(), cause.getStackTrace());
	}

	@Test
	void sensitiveFallbacksRemainSensitiveAfterMapping() {
		var property = LogProperties.StandardProperties.EMPTY.forKey(PREFIX + "team").sensitive();
		var fallback = property.ofString().map(String::strip).or(() -> "sample-secret");
		assertEquals("Fallback[" + PREFIX + "team]=<REDACTED>", fallback.describe());
		assertEquals(fallback.describe(), fallback.toString());
		assertEquals("sample-secret", fallback.validateNow(getClass()));
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> fallback.map(Integer::parseInt).validateNow(getClass()));
		assertFalse(stackTrace(e).contains("sample-secret"), stackTrace(e));
	}

	@Test
	void sensitiveProviderReferencesKeepTheirSensitivity() {
		var properties = LogProperties.builder().fromProperties(PREFIX + "team=custom:///sample-secret").build();
		var reference = properties.forKey(PREFIX + "team").sensitive().ofProviderRef();
		assertEquals("Property[" + PREFIX + "team]=<REDACTED>", reference.describe());
		assertEquals(reference.describe(), reference.toString());
		assertEquals(URI.create("custom:///sample-secret"), reference.validateNow(getClass()).uri());
		var e = assertThrows(LogProperty.ValidationException.class, () -> reference.map(ref -> {
			throw new IllegalArgumentException(ref.uri().toString());
		}).validateNow(getClass()));
		assertFalse(stackTrace(e).contains("sample-secret"), stackTrace(e));
	}

	@Test
	void failedSensitiveMapReadStillRedactsReportValues() {
		var check = new UnusedKeyCheck(List.of());
		LogProperties properties = new LogProperties() {
			@Override
			public String valueOrNull(String key) {
				return "sample-secret";
			}

			@Override
			public Map<String, String> mapOrNull(String key) {
				throw new IllegalArgumentException("sample-secret");
			}
		};
		var recording = check.wrap(properties);
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> recording.forKey(PREFIX + "team").sensitive().ofMap().validateNow(getClass()));
		assertFalse(stackTrace(e).contains("sample-secret"), stackTrace(e));
		assertEquals("<REDACTED>", check.read(recording).getFirst().value());
	}

	private static String stackTrace(Throwable throwable) {
		var out = new StringWriter();
		throwable.printStackTrace(new PrintWriter(out));
		return out.toString();
	}

	@Test
	void readPropertiesRedactsTheKeyAndItsMapMembers() {
		var check = new UnusedKeyCheck(List.of());
		var config = check.wrap(LogProperties.builder().fromProperties("""
				logging.appender.console.output.mapped.headers.keys=X-Team
				logging.appender.console.output.mapped.headers.X-Team=sample-secret
				""").build());
		var headers = config.forKey(PREFIX + "headers").sensitive().ofMap().validateNow(SensitivePropertyTest.class);
		assertEquals(Map.of("X-Team", "sample-secret"), headers);
		var read = check.read(config);
		assertEquals(List.of(PREFIX + "headers", PREFIX + "headers.X-Team", PREFIX + "headers.keys"),
				read.stream().map(UnusedKeyCheck.Read::key).toList());
		for (var r : read) {
			assertFalse(String.valueOf(r.value()).contains("sample-secret"), r.toString());
		}
	}

}
