package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperties.MutableLogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class RainbowGumLoggerFactoryTest {

	ListLogOutput list = new ListLogOutput();

	@Test
	void testGetLogger() {
		var list = new ListLogOutput();
		var gum = RainbowGum.builder().route(route -> {
			route.level(System.Logger.Level.WARNING, "ignore");
			route.level(System.Logger.Level.INFO);
			route.appender("list", a -> {
				a.formatter((output, event) -> {
					event.formattedMessage(output);
					output.append(" {");
					LogFormatter.builder().keyValues().build().format(output, event);
					output.append("}");
					output.append("\n");
				});
				a.output(list);
			});
		});
		var rainbowgum = gum.build();
		var lr = rainbowgum.router().levelResolver();
		System.out.println(lr);

		var mdc = new RainbowGumMDCAdapter();
		RainbowGumLoggerFactory factory = new RainbowGumLoggerFactory(rainbowgum, mdc);
		Consumer<Logger> consumer = (logger) -> {
			mdc.put("status", "alive");
			logger.info("Eric");
			mdc.put("status", "dead");
			logger.debug("Kenny");
			logger.warn("Stan");
		};
		consumer.accept(factory.getLogger("crap"));
		String actual = list.toString();
		String expected = """
				Eric {status=alive}
				Stan {status=dead}
				""";
		assertEquals(expected, actual);
		list.events().clear();

		consumer.accept(factory.getLogger("ignore"));
		actual = list.toString();
		expected = "Stan {status=dead}\n";
		assertEquals(expected, actual);
		list.events().clear();
	}

	@Test
	void testChangeableLogger() {
		String global = """
				logging.global.change=true
				logging.change.mychange=true
				""";
		Map<String, String> m = new LinkedHashMap<>();
		m.put("logging.level.mychange", "ERROR");
		m.put("logging.level.mychange.two", "INFO");

		LogProperties props = LogProperties.builder() //
			.fromFunction(m::get) //
			.with(LogProperties.builder().fromProperties(global).build())
			.build();
		var rainbowgum = gum(props);

		assertTrue(rainbowgum.config().changePublisher().isEnabled("mychange"));

		RainbowGumLoggerFactory factory = new RainbowGumLoggerFactory(rainbowgum, new RainbowGumMDCAdapter());
		var logger = factory.getLogger("mychange");
		assertInstanceOf(LevelChangeable.class, logger);
		assertTrue(logger.isErrorEnabled());
		assertFalse(logger.isDebugEnabled());
		assertFalse(factory.getLogger("mychange.one").isDebugEnabled());

		logger.info("before change info");

		m.put("logging.level.mychange", "DEBUG");
		rainbowgum.config().changePublisher().publish();
		logger.info("after change info");
		assertTrue(logger.isDebugEnabled());
		assertFalse(logger.isTraceEnabled());

		logger = factory.getLogger("mychange.one");
		assertTrue(logger.isDebugEnabled());
		logger = factory.getLogger("mychange.two");
		assertFalse(logger.isDebugEnabled());
		m.put("logging.level.mychange.two", "TRACE");
		rainbowgum.config().changePublisher().publish();
		assertTrue(logger.isTraceEnabled());
		logger.trace("two is now trace enabled");
		String actual = list.toString();

		String expected = """
				INFO after change info <caller>io.jstach.rainbowgum.slf4j.RainbowGumLoggerFactoryTest.testChangeableLogger</caller>
				TRACE two is now trace enabled <caller>io.jstach.rainbowgum.slf4j.RainbowGumLoggerFactoryTest.testChangeableLogger</caller>
				""";
		assertEquals(expected, actual);

		assertInstanceOf(LevelChangeable.class, logger);
		logger = factory.getLogger("static");
		assertInstanceOf(LevelLogger.class, logger);
	}

	@Test
	void testCallerInfoLogger() {
		String global = """
				logging.global.change=true
				logging.change.mychange=caller
				""";
		Map<String, String> m = new LinkedHashMap<>();
		m.put("logging.level.mychange", "ERROR");
		m.put("logging.level.mychange.two", "INFO");

		LogProperties props = LogProperties.builder() //
			.fromFunction(m::get) //
			.with(LogProperties.builder().fromProperties(global).build())
			.build();
		var rainbowgum = gum(props);

		assertTrue(rainbowgum.config().changePublisher().isEnabled("mychange"));

		RainbowGumLoggerFactory factory = new RainbowGumLoggerFactory(rainbowgum, new RainbowGumMDCAdapter());
		var logger = factory.getLogger("mychange");
		assertTrue(logger.isErrorEnabled());
		assertFalse(logger.isDebugEnabled());
		assertFalse(factory.getLogger("mychange.one").isDebugEnabled());

		logger.error("after change info");

		String actual = list.toString();

		String expected = """
				ERROR after change info <caller>io.jstach.rainbowgum.slf4j.RainbowGumLoggerFactoryTest.testCallerInfoLogger</caller>
				""";
		assertEquals(expected, actual);

		assertInstanceOf(LevelLogger.class, logger);

	}

	@Test
	void testChangeableLoggerAll() {
		MutableLogProperties props = MutableLogProperties.builder().copyProperties("""
				logging.global.change=true
				logging.change=true
				logging.level=WARNING
				""").build();
		var rainbowgum = gum(props);
		RainbowGumLoggerFactory factory = new RainbowGumLoggerFactory(rainbowgum, new RainbowGumMDCAdapter());
		var logger = factory.getLogger("anything");
		assertInstanceOf(LevelChangeable.class, logger);
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
		var rainbowgum = gum.build();
		return rainbowgum;
	}

}
