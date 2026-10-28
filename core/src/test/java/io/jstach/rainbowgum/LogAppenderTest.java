package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link LogAppender#builder(String)} is one of the two hand-written builders (the other
 * being {@link LogRouter.Router#builder(String, LogConfig)}, see {@code RouterTest}) that
 * can otherwise skip every keyed property lookup entirely when every field is set
 * explicitly, so unlike an annotation-processor-generated builder it needs its own eager,
 * direct test proving a bad name is rejected at construction time, not just via
 * {@code ConfigFailureTest}'s property-driven cases.
 */
class LogAppenderTest {

	@Test
	void testBuilderRejectsBadNameBeforeAnyFieldIsSet() {
		var e = assertThrows(LogProperty.ValidationException.class, () -> LogAppender.builder("bad name"));
		assertEquals(
				"Validation failed for io.jstach.rainbowgum.LogAppender: \"logging.appender.{name}.\" cannot be interpolated: parameter 'name' value 'bad name' must be alphanumeric (hyphen/underscore allowed)",
				e.getMessage());
	}

	@Test
	void outputIsClosedWhenTheEncoderFails() {
		var closed = new ArrayList<String>();
		var config = config(closed, """
				logging.appenders=a
				logging.appender.a.output.uri=tracked:///a
				logging.appender.a.encoder.uri=missing-encoder
				""");
		var e = assertThrows(RuntimeException.class, () -> RainbowGum.builder(config).build());
		assertEquals(
				"""
						Error for property. key: 'logging.appender.a.encoder.uri' from PROPERTIES_STRING[logging.appender.a.encoder.uri], \
						NotFoundException No encoder found. Scheme not registered. scheme: 'missing-encoder', URI: 'missing-encoder:///'. \
						Registered encoder schemes: 'json5', 'logfmt', 'ttll'
						  ↳ Failure providing Appender: 'a' from property: Property[logging.appenders]=[a].
						  ↳ Failure providing Appenders for route: 'default'.""",
				e.getMessage());
		assertEquals(List.of("a"), closed);
	}

	@Test
	void appendersAlreadyMadeAreClosedWhenALaterOneFails() {
		var closed = new ArrayList<String>();
		var config = config(closed, """
				logging.appenders=a,b
				logging.appender.a.output.uri=tracked:///a
				logging.appender.b.output.uri=tracked:///b
				logging.appender.b.encoder.uri=missing-encoder
				""");
		assertThrows(RuntimeException.class, () -> RainbowGum.builder(config).build());
		assertEquals(List.of("b", "a"), closed);
	}

	@ParameterizedTest
	@ValueSource(strings = { "flags", "type" })
	void invalidFileAppenderSettingsDoNotOpenTheOutput(String setting) {
		var opened = new ArrayList<String>();
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.appenders=file
				logging.appender.file.output.uri=tracked:///file
				logging.appender.file.%s=invalid
				""".formatted(setting)).build()).build();
		config.outputRegistry().register("tracked", ref -> (n, c) -> {
			opened.add(n);
			return new TrackedOutput(n, new ArrayList<>());
		});
		assertThrows(RuntimeException.class, () -> RainbowGum.builder(config).build());
		assertEquals(List.of(), opened);
	}

	@Test
	void cleanupFailuresArePreservedAndDoNotPreventClosingOtherAppenders() {
		var closed = new ArrayList<String>();
		var leaf = new IllegalArgumentException("encoder failed");
		var failure = new IllegalStateException("encoder wrapper", leaf);
		var outputCloseFailure = new IllegalStateException("output close failed");
		var appenderCloseFailure = new AssertionError("appender close failed");
		var a = LogAppender.builder("a").output(new TrackedOutput("a", closed, () -> {
			throw appenderCloseFailure;
		})).build().describe("appender a");
		var b = LogAppender.builder("b").output(new TrackedOutput("b", closed)).build();
		var c = LogAppender.builder("c").output(new TrackedOutput("c", closed, () -> {
			throw outputCloseFailure;
		})).encoder((n, config) -> {
			throw failure;
		}).build().describe("appender c");
		var appenders = new LogAppender.Appenders("default", LogConfig.builder().build(), List.of(a, b, c));

		var thrown = assertThrows(LogProvider.ProvisionException.class, appenders::asSingle);
		assertSame(leaf, assertInstanceOf(IllegalArgumentException.class, thrown.getCause()));
		assertArrayEquals(new Throwable[] { outputCloseFailure, appenderCloseFailure }, thrown.getSuppressed());
		assertEquals(List.of("c", "a", "b"), closed);
	}

	@Test
	void cleanupCannotSuppressTheOriginalFailureWithItself() {
		var failure = new IllegalStateException("encoder and output failure");
		var output = new TrackedOutput("a", new ArrayList<>(), () -> {
			throw failure;
		});
		var provider = LogAppender.builder("a").output(output).encoder((n, c) -> {
			throw failure;
		}).build();
		assertSame(failure,
				assertThrows(IllegalStateException.class, () -> provider.provide("a", LogConfig.builder().build())));
	}

	private static LogConfig config(List<String> closed, String properties) {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		config.outputRegistry()
			.register("tracked", ref -> LogProvider.of(new TrackedOutput(ref.uri().getPath().substring(1), closed)));
		return config;
	}

	record TrackedOutput(String name, List<String> closed, Runnable closeAction) implements LogOutput {

		TrackedOutput(String name, List<String> closed) {
			this(name, closed, () -> {
			});
		}

		@Override
		public URI uri() {
			return URI.create("tracked:///" + name);
		}

		@Override
		public OutputType type() {
			return OutputType.MEMORY;
		}

		@Override
		public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
			closed.add(name);
			closeAction.run();
		}

	}

}
