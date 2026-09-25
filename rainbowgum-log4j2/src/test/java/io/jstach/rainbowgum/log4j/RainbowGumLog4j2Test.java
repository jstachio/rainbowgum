package io.jstach.rainbowgum.log4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * Registered via META-INF/services/org.apache.logging.log4j.spi.Provider (see
 * RainbowGumLog4jProvider's javadoc for why the older log4j-provider.properties
 * mechanism doesn't work), picked up automatically by LogManager.getLogger(...) with no
 * system property required, unlike JBoss Logging's own SLF4J bridge path which needs
 * -Dorg.jboss.logging.provider=slf4j.
 */
class RainbowGumLog4j2Test {

	ListLogOutput list = new ListLogOutput();

	@Test
	void testProviderIsPickedUpWithNoSystemPropertyRequired() {
		Logger log = LogManager.getLogger("provider.selection");
		assertInstanceOf(RainbowGumLogger.class, log,
				"LogManager.getLogger(name) must resolve to our native provider with zero extra configuration");
	}

	@Test
	void testCallerInfoAndMdcRoundTripWithNoSystemPropertyRequired() {
		String name = "log4j2.native.caller";
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
			Logger log = LogManager.getLogger(name);
			ThreadContext.put("tenant", "acme");
			try {
				log.info("hello");
			}
			finally {
				ThreadContext.clearMap();
			}

			String expected = "INFO hello kv=acme <caller>io.jstach.rainbowgum.log4j.RainbowGumLog4j2Test"
					+ ".testCallerInfoAndMdcRoundTripWithNoSystemPropertyRequired:69</caller>\n";
			assertEquals(expected, list.toString());
		}
		finally {
			RainbowGum.builder(LogConfig.builder().build()).unset();
		}
	}

	@Test
	void testDebugIsGatedByRoute() {
		String name = "log4j2.native.gated";
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
			Logger log = LogManager.getLogger(name);
			assertFalse(log.isDebugEnabled());
			log.debug("should not appear");
			assertEquals("", list.toString());
		}
		finally {
			RainbowGum.builder(LogConfig.builder().build()).unset();
		}
	}

	@Test
	void testParameterizedMessageFormatting() {
		String name = "log4j2.native.params";
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
			Logger log = LogManager.getLogger(name);
			log.info("hello {} from {}", "world", "log4j2");
			assertEquals("hello world from log4j2\n", list.toString());
		}
		finally {
			RainbowGum.builder(LogConfig.builder().build()).unset();
		}
	}

}
