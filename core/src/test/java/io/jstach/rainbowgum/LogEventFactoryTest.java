package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.System.Logger.Level;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class LogEventFactoryTest {

	@Test
	void ofBindsTheGivenLoggerNameToEveryEvent() {
		var factory = LogEventFactory.of("logger");
		var event = factory.event(Level.INFO, "Hello!", KeyValues.of(), null);
		assertEquals("logger", event.loggerName());
	}

	@Test
	void eventWithNoArgsMatchesTheCorrespondingStaticFactory() {
		var factory = LogEventFactory.of("logger");
		var event = factory.event(Level.INFO, "Hello!", KeyValues.of(), null);
		assertInstanceOf(DefaultLogEvent.class, event);
		assertEquals(Level.INFO, event.level());
		assertEquals("logger", event.loggerName());
		assertEquals("Hello!", event.message());
		assertNull(event.throwableOrNull());
	}

	@Test
	void eventWithOneArgFormatsTheMessage() {
		var factory = LogEventFactory.of("logger");
		var event = factory.event(Level.INFO, "hello {}", KeyValues.of(), "world");
		assertInstanceOf(OneArgLogEvent.class, event);
		StringBuilder sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("hello world", sb.toString());
	}

	@Test
	void eventWithOneArgDetectsTrailingThrowable() {
		var factory = LogEventFactory.of("logger");
		var throwable = new RuntimeException("boom");
		var event = factory.event(Level.INFO, "hello", KeyValues.of(), throwable);
		assertInstanceOf(DefaultLogEvent.class, event);
		assertEquals(throwable, event.throwableOrNull());
	}

	@Test
	void eventWithTwoArgsFormatsTheMessage() {
		var factory = LogEventFactory.of("logger");
		var event = factory.event(Level.INFO, "{} {}", KeyValues.of(), "hello", "world");
		assertInstanceOf(TwoArgLogEvent.class, event);
		StringBuilder sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("hello world", sb.toString());
	}

	@Test
	void eventWithTwoArgsDetectsTrailingThrowable() {
		var factory = LogEventFactory.of("logger");
		var throwable = new RuntimeException("boom");
		var event = factory.event(Level.INFO, "hello {}", KeyValues.of(), "world", throwable);
		assertInstanceOf(OneArgLogEvent.class, event);
		assertEquals(throwable, event.throwableOrNull());
	}

	@Test
	void eventArgsWithMoreThanTwoArgsUsesArrayArgLogEvent() {
		var factory = LogEventFactory.of("logger");
		var event = factory.eventArgs(Level.INFO, "{} {} {}", KeyValues.of(), new Object[] { "a", "b", "c" });
		assertInstanceOf(ArrayArgLogEvent.class, event);
		StringBuilder sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("a b c", sb.toString());
	}

	@Test
	void subclassOverridingMessageFormatterAffectsAllArgTakingMethods() {
		var factory = new LogEventFactory() {
			@Override
			protected String loggerName() {
				return "logger";
			}

			@Override
			protected LogMessageFormatter messageFormatter() {
				return LogMessageFormatter.StandardMessageFormatter.JUL;
			}
		};

		var event = factory.event(Level.INFO, "hello {0}", KeyValues.of(), "world");
		StringBuilder sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("hello world", sb.toString());
	}

	@Test
	void subclassOverridingResolversMakesEventsDeterministic() {
		var fixedInstant = Instant.EPOCH;
		var factory = new LogEventFactory() {
			@Override
			protected String loggerName() {
				return "logger";
			}

			@Override
			protected Instant timestamp() {
				return fixedInstant;
			}

			@Override
			protected String threadName() {
				return "fixed-thread";
			}

			@Override
			protected long threadId() {
				return 42L;
			}
		};

		var event = factory.event(Level.INFO, "hello", KeyValues.of(), null);
		assertEquals(fixedInstant, event.timestamp());
		assertEquals("fixed-thread", event.threadName());
		assertEquals(42L, event.threadId());

		var argEvent = factory.event(Level.INFO, "hello {}", KeyValues.of(), "world");
		assertEquals(fixedInstant, argEvent.timestamp());
		assertEquals("fixed-thread", argEvent.threadName());
		assertEquals(42L, argEvent.threadId());
	}

}
