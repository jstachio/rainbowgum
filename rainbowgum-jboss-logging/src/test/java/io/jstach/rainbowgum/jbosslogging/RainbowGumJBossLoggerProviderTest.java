package io.jstach.rainbowgum.jbosslogging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * Unlike the org.jboss.logging.Slf4jLoggerProvider path (see
 * rainbowgum-test-jboss-logging), this is a native LoggerProvider registered via
 * META-INF/services (see RainbowGumJBossLoggerProviderTest#testProviderIsPickedUpWithNoSystemPropertyRequired)
 * - no -Dorg.jboss.logging.provider=slf4j required, since LoggerProviders.findProvider()
 * checks ServiceLoader before ever falling back to a bridge or plain JDK logging.
 */
class RainbowGumJBossLoggerProviderTest {

	ListLogOutput list = new ListLogOutput();

	@Test
	void testProviderIsPickedUpWithNoSystemPropertyRequired() {
		Logger log = Logger.getLogger("provider.selection");
		assertInstanceOf(RainbowGumJBossLogger.class, log,
				"the real org.jboss.logging.Logger.getLogger(name) static entry point must resolve to our native provider with zero extra configuration");
	}

	@Test
	void testCallerInfoAndMdcRoundTripWithNoSystemPropertyRequired() {
		String name = "jboss.native.caller";
		String properties = """
				logging.level.%s=INFO
				""".formatted(name);
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		RainbowGum.builder(config).route(route -> {
			route.appender("list", a -> {
				a.formatter((output, event) -> {
					output.append(event.level()).append(" ");
					event.formattedMessage(output);
					output.append(" kv=").append(event.keyValues().getValueOrNull("tenant"));
					Caller caller = event.callerOrNull();
					if (caller != null) {
						output.append(" <caller>");
						output.append(caller.className());
						output.append(".");
						output.append(caller.methodName());
						output.append(":");
						output.append(caller.lineNumber());
						output.append("</caller>");
					}
					output.append("\n");
				});
				a.output(list);
			});
		}).set();

		try {
			Logger log = Logger.getLogger(name);
			MDC.put("tenant", "acme");
			try {
				log.info("hello");
			}
			finally {
				MDC.clear();
			}

			String expected = "INFO hello kv=acme <caller>io.jstach.rainbowgum.jbosslogging.RainbowGumJBossLoggerProviderTest"
					+ ".testCallerInfoAndMdcRoundTripWithNoSystemPropertyRequired:68</caller>\n";
			assertEquals(expected, list.toString());
		}
		finally {
			RainbowGum.builder(LogConfig.builder().build()).unset();
		}
	}

	@Test
	void testDebugIsGatedByRoute() {
		String name = "jboss.native.gated";
		String properties = """
				logging.level.%s=INFO
				""".formatted(name);
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		RainbowGum.builder(config).route(route -> {
			route.appender("list", a -> {
				a.formatter((output, event) -> {
					event.formattedMessage(output);
					output.append("\n");
				});
				a.output(list);
			});
		}).set();

		try {
			Logger log = Logger.getLogger(name);
			assertFalse(log.isDebugEnabled());
			log.debug("should not appear");
			assertEquals("", list.toString());
		}
		finally {
			RainbowGum.builder(LogConfig.builder().build()).unset();
		}
	}

}
