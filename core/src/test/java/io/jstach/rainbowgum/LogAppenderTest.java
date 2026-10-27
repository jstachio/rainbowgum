package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

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

	private static LogConfig config(List<String> closed, String properties) {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		config.outputRegistry()
			.register("tracked", ref -> LogProvider.of(new TrackedOutput(ref.uri().getPath().substring(1), closed)));
		return config;
	}

	record TrackedOutput(String name, List<String> closed) implements LogOutput {

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
		}

	}

}
