package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.spi.LoggingEventBuilder;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.slf4j.spi.AbstractFilteringLogger;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAwareEventBuilder;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAwareLogger;

class LoggerDecoratorTest {

	ListLogOutput list = new ListLogOutput();

	@ParameterizedTest
	@EnumSource(DecoratorTest.class)
	void test(DecoratorTest test) {

		LogConfig config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.global.change=true
				logging.change=caller
				""").build()).configurator(new MyLoggerDecoratorService()).build();
		RainbowGum gum = gum(config);
		RainbowGumMDCAdapter mdc = new RainbowGumMDCAdapter();
		try (var g = gum.start()) {
			var factory = RainbowGumLoggerFactory.of(gum, mdc);
			var logger = factory.getLogger("test");
			logger.info("hello");
		}
		String actual = list.toString();
		String expected = test.expected;
		assertEquals(expected, actual);

	}

	/*
	 * CompositeLoggerDecorator.decorate() (which composes multiple registered
	 * LoggerDecoratorServices) has two branches no other test reaches: a decorator that
	 * returns the logger it was given unchanged (rather than always wrapping, the way
	 * MyLoggerDecoratorService above does), and the early-return when a decorator wraps
	 * in something that no longer implements DepthAwareLogger, stopping the chain before
	 * the next decorator ever runs. Tested directly against CompositeLoggerDecorator
	 * rather than through a full RainbowGum bootstrap, to avoid entangling this with
	 * caller-info depth accounting (irrelevant here).
	 */
	static boolean neverCalledInvoked;

	record PlainWrapper(Logger delegate) implements ForwardingLogger {

	}

	static class NoOpDecoratorService extends LoggerDecoratorService {

		@Override
		public String name() {
			return "A_NoOp";
		}

		@Override
		public int order() {
			return 0;
		}

		@Override
		public Logger decorate(RainbowGum rainbowGum, DepthAwareLogger previousLogger, int depth) {
			return previousLogger;
		}

	}

	static class BreakChainDecoratorService extends LoggerDecoratorService {

		@Override
		public String name() {
			return "B_Break";
		}

		@Override
		public int order() {
			return 1;
		}

		@Override
		public Logger decorate(RainbowGum rainbowGum, DepthAwareLogger previousLogger, int depth) {
			return new PlainWrapper(previousLogger);
		}

	}

	static class NeverCalledDecoratorService extends LoggerDecoratorService {

		@Override
		public String name() {
			return "C_Never";
		}

		@Override
		public int order() {
			return 2;
		}

		@Override
		public Logger decorate(RainbowGum rainbowGum, DepthAwareLogger previousLogger, int depth) {
			neverCalledInvoked = true;
			return previousLogger;
		}

	}

	@Test
	void testCompositeDecoratorSkipsUnchangedThenStopsAtNonDepthAwareWrapper() {
		neverCalledInvoked = false;
		var services = new LoggerDecoratorService[] { new NoOpDecoratorService(), new BreakChainDecoratorService(),
				new NeverCalledDecoratorService() };
		var composite = new RainbowGumLoggerFactory.LoggerDecorator.CompositeLoggerDecorator(services);

		var handler = LogEventHandler.of("test", e -> {
		}, new RainbowGumMDCAdapter());
		DepthAwareLogger base = LevelLogger.of(org.slf4j.event.Level.INFO, handler);
		RainbowGum rainbowGum = RainbowGum.builder().build();

		Logger result = composite.decorate(rainbowGum, base);

		assertInstanceOf(PlainWrapper.class, result);
		assertSame(base, ((PlainWrapper) result).delegate());
		assertFalse(neverCalledInvoked,
				"third decorator must never run once the chain is broken by a non-DepthAware wrapper");
	}

	static class MyLoggerDecoratorService extends LoggerDecoratorService {

		@Override
		public String name() {
			return "MyWrapper";
		}

		@Override
		public Logger decorate(RainbowGum rainbowGum, DepthAwareLogger previousLogger, int depth) {
			return new MyLogger(previousLogger);
		}

	}

	/**
	 * Demonstrates a filtering decorator that prefixes every message. Note there is no
	 * DEPTH constant to get wrong here: {@link AbstractFilteringLogger} owns and tests
	 * its own caller-info depth accounting.
	 */
	static class MyLogger extends AbstractFilteringLogger {

		static final String PREFIX = "MY_PREFIX ";

		MyLogger(DepthAwareLogger delegate) {
			super(delegate);
		}

		@Override
		protected boolean decorate(LoggingEventBuilder builder, @Nullable Marker marker) {
			String message = DepthAwareEventBuilder.message(builder);
			builder.setMessage(PREFIX + (message == null ? "" : message));
			return true;
		}

	}

	enum DecoratorTest {

		SIMPLE("""
				INFO MY_PREFIX hello <caller>io.jstach.rainbowgum.slf4j.LoggerDecoratorTest.test</caller>
				""");

		private DecoratorTest(String expected) {
			this.expected = expected;
		}

		private final String expected;

	}

	private RainbowGum gum(LogConfig config) {
		var gum = RainbowGum.builder(config).route(route -> {
			route.appender("list", a -> {
				a.formatter((output, event) -> {
					output.append(event.level()).append(" ");
					event.formattedMessage(output);
					Caller caller = event.callerOrNull();
					if (caller != null) {
						output.append(" <caller>");
						output.append(caller.className());
						output.append(".");
						output.append(caller.methodName());
						output.append("</caller>");
					}
					output.append("\n");

				});
				a.output(list);
			});
		});
		var rainbowgum = gum.build();
		return rainbowgum;
	}

}
