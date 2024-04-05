package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.AbstractLogger;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAware;

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
			var factory = new RainbowGumLoggerFactory(gum, mdc);
			var logger = factory.getLogger("test");
			logger.makeLoggingEventBuilder(Level.INFO).setMessage("hello").log();
		}
		String actual = list.toString();
		String expected = test.expected;
		assertEquals(expected, actual);

	}

	static class MyLoggerDecoratorService extends LoggerDecoratorService {

		@Override
		public String name() {
			return "MyWrapper";
		}

		@Override
		public Logger decorate(RainbowGum rainbowGum, Logger previousLogger) {
			return new MyLogger(previousLogger);
		}

	}

	static class MyLogger extends AbstractLogger implements ForwardingLogger, DepthAware {

		private static final long serialVersionUID = 1L;

		private final Logger logger;

		String prefix = "MY_PREFIX ";

		private static int DEPTH = 7;

		private int depth = 0;

		public MyLogger(Logger logger) {
			super();
			this.logger = logger;
		}

		@Override
		public Logger delegate() {
			return logger;
		}

		@Override
		protected String getFullyQualifiedCallerName() {
			throw new UnsupportedOperationException();
		}

		@Override
		protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern,
				@Nullable Object @Nullable [] arguments, @Nullable Throwable throwable) {
			handle(level, messagePattern, arguments, throwable);
		}

		protected void handle(Level level, String messagePattern, @Nullable Object @Nullable [] arguments,
				@Nullable Throwable throwable) {
			var builder = logger.makeLoggingEventBuilder(level);
			if (builder instanceof DepthAware da) {
				da.setDepth(depth + DEPTH, -1);
			}
			builder.setMessage(prefix + messagePattern);
			if (arguments != null) {
				for (var arg : arguments) {
					builder.addArgument(arg);
				}
			}
			if (throwable != null) {
				builder.setCause(throwable);
			}
			builder.log();
		}

		@Override
		public void setDepth(int index, int endIndex) {
			this.depth = index;
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
