package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
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

	/*
	 * Every other OffLogger in this suite is constructed manually (new
	 * LevelLogger.OffLogger(name)) - this is the only place the real
	 * RainbowGumLoggerFactory.getLogger() OFF-level branch itself runs, which only
	 * happens when ChangeType.LEVEL is not allowed for the name (the default here, since
	 * no logging.change property is set).
	 */
	@Test
	void testGetLoggerAtOffLevelUsesOffLogger() {
		var gum = RainbowGum.builder().route(route -> {
			route.level(System.Logger.Level.OFF, "silent");
			route.appender("list", a -> a.output(list));
		}).build();
		var factory = new RainbowGumLoggerFactory(gum, new RainbowGumMDCAdapter());
		var logger = factory.getLogger("silent");
		assertInstanceOf(LevelLogger.OffLogger.class, logger);
	}

	/*
	 * getLogger()'s "if (allowedChanges.contains(ChangeType.LEVEL))" branch does a plain
	 * (non-atomic w.r.t. the map) get()-then-putIfAbsent() when a name has never been
	 * seen before; the loser of a genuine race gets the winner's instance back (the
	 * oldInstance == null ? decorated : oldInstance ternary) instead of its own. A
	 * CyclicBarrier releases several threads into getLogger() for the same
	 * never-before-seen name at the same instant; there is real work (level resolution,
	 * handler construction, decorator composition) between the get() check and the
	 * putIfAbsent() call, so the race window is wide enough that this reliably lands more
	 * than one thread inside it rather than relying on luck.
	 */
	@Test
	void testGetLoggerConcurrentlyForANewNameReturnsTheSameInstanceToEveryThread() throws InterruptedException {
		String global = """
				logging.global.change=true
				logging.change=true
				""";
		var rainbowgum = gum(LogProperties.builder().fromProperties(global).build());
		var factory = new RainbowGumLoggerFactory(rainbowgum, new RainbowGumMDCAdapter());

		int threadCount = 8;
		var barrier = new java.util.concurrent.CyclicBarrier(threadCount);
		Logger[] results = new Logger[threadCount];
		Thread[] threads = new Thread[threadCount];
		for (int i = 0; i < threadCount; i++) {
			int index = i;
			threads[i] = new Thread(() -> {
				try {
					barrier.await();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
				results[index] = factory.getLogger("racy-name");
			});
		}
		for (var t : threads) {
			t.start();
		}
		for (var t : threads) {
			t.join();
		}

		for (int i = 1; i < threadCount; i++) {
			assertSame(results[0], results[i],
					"every thread racing to create the same never-before-seen logger name must end up with the exact same instance");
		}
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

	/*
	 * Reproduces the Spring Boot scenario: SLF4J's ServiceLoader-triggered initialize()
	 * calls RainbowGum.of() once, very early, and captures whatever gum is bound at that
	 * moment (Spring's own pre-boot bootstrap gum) into RainbowGumLoggerFactory forever -
	 * or at least it used to, before rainbowGum became volatile and subscribed to
	 * RainbowGum.onGlobalChange. Later, RainbowGum.builder(...).set() (as Spring's "real"
	 * LoggingSystem does once fully configured) must be picked up by the *same* factory
	 * instance for names looked up afterward, without anyone needing to construct a new
	 * RainbowGumLoggerFactory.
	 */
	@Test
	void testFactoryPicksUpANewGlobalRainbowGumForNamesLookedUpAfterTheSwap() {
		// RainbowGumHolder is package-private to core and thus not visible here - an
		// empty-config builder's unset() is the public equivalent used elsewhere
		// (RainbowGumEntryPointTest) to force the global holder back to nothing, in
		// case a prior test in this module's suite left it dirty.
		RainbowGum.builder(LogConfig.builder().build()).unset();
		try {
			var bootstrapOutput = new ListLogOutput();
			var bootstrapGum = gum(bootstrapOutput, LogProperties.StandardProperties.EMPTY);
			var factory = new RainbowGumLoggerFactory(bootstrapGum, new RainbowGumMDCAdapter());
			factory.getLogger("before.swap").info("via bootstrap gum");
			assertEquals("INFO via bootstrap gum\n", bootstrapOutput.toString());
			bootstrapOutput.events().clear();

			var realOutput = new ListLogOutput();
			try (var realGum = RainbowGum.builder(LogConfig.builder().build()).route(route -> {
				route.appender("list", a -> {
					a.formatter((output, event) -> {
						output.append(event.level()).append(" ");
						event.formattedMessage(output);
						output.append("\n");
					});
					a.output(realOutput);
				});
			}).set()) {
				// A name never looked up before the swap - this is the one factory
				// instance created above, not a new one.
				factory.getLogger("after.swap").info("via real gum");
				assertEquals("", bootstrapOutput.toString(),
						"the swap must not retroactively affect the bootstrap gum's own output");
				assertEquals("INFO via real gum\n", realOutput.toString(),
						"a logger name looked up after the swap must route through the newly-global gum");
			}
		}
		finally {
			RainbowGum.builder(LogConfig.builder().build()).unset();
		}
	}

	/*
	 * Reproduces the other half of the Spring Boot scenario: RainbowGum.queued(config)
	 * (what PreBootRainbowGumProvider actually hands SLF4J) uses LogRouter.global()
	 * itself as its router, so a ReplaceableLogger obtained during this phase captures a
	 * route resolved against whatever GlobalLogRouter's delegate currently is - the
	 * initial QueueEventsRouter placeholder, which only buffers events rather than
	 * writing them anywhere. RainbowGum.builder(...).set() below triggers
	 * InternalRootRouter.setRouter(...), which transfers the queue's subscribers
	 * (including this logger's subscribe() consumer) to the real router and publishes to
	 * them - the same *logger instance* obtained above must then dispatch new log calls
	 * through the real router, not keep writing into the now-abandoned queue.
	 */
	@Test
	void testReplaceableLoggerObtainedDuringBootstrapDispatchesThroughTheRealRouterAfterSwap() {
		RainbowGum.builder(LogConfig.builder().build()).unset();
		try {
			String preBootProperties = """
					logging.global.change=true
					logging.change=level
					""";
			var bootstrapConfig = LogConfig.builder()
				.properties(LogProperties.builder().fromProperties(preBootProperties).build())
				.build();
			var bootstrapGum = RainbowGum.queued(bootstrapConfig);
			var factory = new RainbowGumLoggerFactory(bootstrapGum, new RainbowGumMDCAdapter());
			var logger = factory.getLogger("bootstrap.logger");
			assertInstanceOf(LevelChangeable.class, logger,
					"logging.change=level must make this a replaceable logger, same as Spring's pre-boot properties");

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
				logger.info("logged after real gum loads");
				assertEquals("logged after real gum loads\n", realOutput.toString());
			}
		}
		finally {
			RainbowGum.builder(LogConfig.builder().build()).unset();
		}
	}

	private RainbowGum gum(ListLogOutput output, LogProperties props) {
		LogConfig config = LogConfig.builder().properties(props).build();
		var gum = RainbowGum.builder(config).route(route -> {
			route.appender("list", a -> {
				a.formatter((o, event) -> {
					o.append(event.level()).append(" ");
					event.formattedMessage(o);
					o.append("\n");
				});
				a.output(output);
			});
		});
		return gum.build();
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
