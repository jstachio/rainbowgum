package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.slf4j.spi.LocationAwareLogger;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class LocationAwareForwardingLoggerTest {

	ListLogOutput list = new ListLogOutput();

	@Test
	void testFqcnSearchSkipsTheBridgeFrameAndReportsTheRealCaller() {
		var logger = locationAwareLogger("bridge.caller");

		FakeBridge.log(logger, "hello");

		String expected = "INFO hello <caller>io.jstach.rainbowgum.slf4j.LocationAwareForwardingLoggerTest"
				+ ".testFqcnSearchSkipsTheBridgeFrameAndReportsTheRealCaller</caller>\n";
		assertEquals(expected, list.toString());
	}

	@Test
	void testFqcnNeverOnTheStackLogsWithoutCallerInfoInsteadOfThrowing() {
		var logger = locationAwareLogger("bridge.unknownfqcn");

		logger.log(null, "com.example.NotOnTheStack", LocationAwareLogger.INFO_INT, "hello", null, null);

		assertEquals("INFO hello\n", list.toString());
	}

	@Test
	void testLevelBelowThresholdIsSkippedBeforeAnyStackWalkOrEvent() {
		var logger = locationAwareLogger("bridge.gated");

		FakeBridge.logAt(logger, LocationAwareLogger.DEBUG_INT, "should not appear");

		assertEquals("", list.toString());
	}

	/*
	 * Mirrors RainbowGumLoggerFactoryTest's
	 * testReplaceableLoggerObtainedDuringBootstrapDispatchesThroughTheRealRouterAfterSwap:
	 * the logger is obtained while bound to a queued bootstrap RainbowGum, then a real
	 * one is set - the same logger instance must dispatch new calls through the real
	 * router instead of the abandoned queue. For LocationAwareForwardingLogger this
	 * specifically exercises HandlerSource.currentHandler() being read fresh on every
	 * log(...) call rather than a handler captured once at construction, which would
	 * still point at the queue after the swap.
	 */
	@Test
	void testRouterSwapIsPickedUpByTheLocationAwareLogPathNotJustThePlainOne() {
		RainbowGum.builder(LogConfig.builder().build()).unset();
		try {
			String preBootProperties = """
					logging.global.change=true
					logging.change=true
					logging.caller=true
					""";
			var bootstrapConfig = LogConfig.builder()
				.properties(LogProperties.builder().fromProperties(preBootProperties).build())
				.build();
			var bootstrapGum = RainbowGum.queued(bootstrapConfig);
			var factory = new RainbowGumLoggerFactory(bootstrapGum, new RainbowGumMDCAdapter());
			var logger = assertInstanceOf(LocationAwareLogger.class, factory.getLogger("bootstrap.bridge"));

			var realOutput = new ListLogOutput();
			try (var realGum = RainbowGum.builder(LogConfig.builder().build()).route(route -> {
				route.appender("list", a -> {
					a.formatter((output, event) -> {
						event.formattedMessage(output);
						output.append("\n");
					});
					a.output(realOutput);
				});
			}).set()) {
				FakeBridge.log(logger, "logged after real gum loads");
				assertEquals("logged after real gum loads\n", realOutput.toString());
			}
		}
		finally {
			RainbowGum.builder(LogConfig.builder().build()).unset();
		}
	}

	private LocationAwareLogger locationAwareLogger(String name) {
		String global = """
				logging.global.change=true
				logging.caller.%s=true
				logging.level.%s=INFO
				""".formatted(name, name);
		var props = LogProperties.builder().fromProperties(global).build();
		var rainbowgum = gum(props);
		var factory = new RainbowGumLoggerFactory(rainbowgum, new RainbowGumMDCAdapter());
		return assertInstanceOf(LocationAwareLogger.class, factory.getLogger(name));
	}

	private RainbowGum gum(LogProperties props) {
		LogConfig config = LogConfig.builder().properties(props).build();
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
		return gum.build();
	}

	/*
	 * Stands in for a real bridge (jcl-over-slf4j's SLF4JLocationAwareLog is the
	 * motivating case) - a class deliberately one frame up from the LocationAwareLogger
	 * call, whose name is passed as fqcn so the search has something concrete to skip
	 * past on its way to the real caller.
	 */
	static final class FakeBridge {

		static void log(LocationAwareLogger logger, String message) {
			logAt(logger, LocationAwareLogger.INFO_INT, message);
		}

		static void logAt(LocationAwareLogger logger, int level, String message) {
			logger.log(null, FakeBridge.class.getName(), level, message, null, null);
		}

	}

}
