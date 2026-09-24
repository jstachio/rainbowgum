package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogOutput.ProvidesEncoder;
import io.jstach.rainbowgum.LogOutput.ProvidesEncoder.Policy;

/**
 * Tests {@link LogAppender.Builder#build()}'s resolution of an output that supplies its
 * own encoder via {@link ProvidesEncoder}: {@link Policy#MANDATORY} claims sole ownership
 * and rejects any other encoder being configured, programmatically or via property, while
 * {@link Policy#DEFAULT} only ever supplies a fallback that either kind of explicit
 * encoder can override. An output that does not implement {@link ProvidesEncoder} at all
 * (the vast majority) is untouched by any of this - confirmed indirectly by every other
 * appender test in this module still passing.
 */
class AppenderProvidesEncoderTest {

	@Test
	void mandatoryOutputIsUsedWhenNothingElseIsConfigured() {
		var config = LogConfig.builder().build();
		var output = new StubOutput(Policy.MANDATORY);

		var provider = LogAppender.builder("a").output(output).build();
		var appender = assertInstanceOf(DirectLogAppender.class, provider.provide("a", config));

		assertSame(output.encoder(), appender.encoder());
	}

	@Test
	void mandatoryOutputRejectsExplicitProgrammaticEncoder() {
		var config = LogConfig.builder().build();
		var output = new StubOutput(Policy.MANDATORY);
		var explicit = LogEncoder.of(LogFormatter.builder().level().build()).provide("a", config);

		var provider = LogAppender.builder("a").output(output).encoder(explicit).build();

		var e = assertThrows(LogProperty.ValidationException.class, () -> provider.provide("a", config));
		assertEquals("Validation failed for io.jstach.rainbowgum.LogAppender: Appender 'a' output "
				+ "io.jstach.rainbowgum.AppenderProvidesEncoderTest$StubOutput provides a mandatory encoder "
				+ "and does not allow another encoder to be configured.", e.getMessage());
	}

	@Test
	void mandatoryOutputRejectsExplicitPropertyEncoder() {
		var props = LogProperties.builder().fromProperties("""
				logging.appender.a.encoder=ttll:///
				""").build();
		var config = LogConfig.builder().properties(props).build();
		var output = new StubOutput(Policy.MANDATORY);

		var provider = LogAppender.builder("a").output(output).build();

		var e = assertThrows(LogProperty.ValidationException.class, () -> provider.provide("a", config));
		assertEquals("Validation failed for io.jstach.rainbowgum.LogAppender: Appender 'a' output "
				+ "io.jstach.rainbowgum.AppenderProvidesEncoderTest$StubOutput provides a mandatory encoder "
				+ "and does not allow another encoder to be configured.", e.getMessage());
	}

	@Test
	void defaultOutputIsUsedWhenNothingElseIsConfigured() {
		var config = LogConfig.builder().build();
		var output = new StubOutput(Policy.DEFAULT);

		var provider = LogAppender.builder("a").output(output).build();
		var appender = assertInstanceOf(DirectLogAppender.class, provider.provide("a", config));

		assertSame(output.encoder(), appender.encoder());
	}

	@Test
	void defaultOutputIsOverriddenByExplicitProgrammaticEncoder() {
		var config = LogConfig.builder().build();
		var output = new StubOutput(Policy.DEFAULT);
		var explicit = LogEncoder.of(LogFormatter.builder().level().build()).provide("a", config);

		var provider = LogAppender.builder("a").output(output).encoder(explicit).build();
		var appender = assertInstanceOf(DirectLogAppender.class, provider.provide("a", config));

		assertSame(explicit, appender.encoder());
	}

	@Test
	void defaultOutputIsOverriddenByExplicitPropertyEncoder() {
		var props = LogProperties.builder().fromProperties("""
				logging.appender.a.encoder=ttll:///
				""").build();
		var config = LogConfig.builder().properties(props).build();
		var output = new StubOutput(Policy.DEFAULT);

		var provider = LogAppender.builder("a").output(output).build();
		var appender = assertInstanceOf(DirectLogAppender.class, provider.provide("a", config));

		assertNotSame(output.encoder(), appender.encoder());
	}

	static final class StubOutput implements LogOutput, ProvidesEncoder {

		private final Policy policy;

		private final LogEncoder encoder;

		StubOutput(Policy policy) {
			this.policy = policy;
			this.encoder = LogEncoder.of(LogFormatter.builder().message().build())
				.provide("stub", LogConfig.builder().build());
		}

		@Override
		public LogEncoder encoder() {
			return encoder;
		}

		@Override
		public Policy policy() {
			return policy;
		}

		@Override
		public URI uri() {
			return URI.create("test:///provides-encoder");
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
		}

	}

}
