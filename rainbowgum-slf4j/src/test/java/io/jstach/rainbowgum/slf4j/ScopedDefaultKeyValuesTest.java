package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAwareEventBuilder;

/*
 * Covers the fix for the "MDC everywhere" problem: a LogEventFactory registered in
 * ServiceRegistry under RainbowGumSLF4JServiceProvider#SCOPED_KEY_VALUES_SERVICE_NAME is
 * supposed to have its own defaultKeyValues() merged as the lowest-precedence source
 * underneath MDC, for *every* shape of log call - plain, fluent (with and without its
 * own addKeyValue(...)), and caller-info-enabled. Before this fix, only the plain-call
 * path actually consulted LogEventFactory#defaultKeyValues() at all; the other three
 * independently read MDC directly and would have silently ignored a registered factory.
 */
class ScopedDefaultKeyValuesTest {

	/*
	 * "env" is present in both scoped and MDC - lowest-precedence-loses is the whole
	 * point being tested; "scopedOnly" is present only in scoped, to confirm it actually
	 * comes through at all, not just "doesn't clobber MDC".
	 */
	private static final LogEventFactory SCOPED = new LogEventFactory() {
		@Override
		public String loggerName() {
			throw new UnsupportedOperationException();
		}

		@Override
		public KeyValues defaultKeyValues() {
			return KeyValues.of(java.util.Map.of("env", "scoped-env", "scopedOnly", "present"));
		}
	};

	private static void assertMerged(KeyValues kvs) {
		assertEquals("mdc-env", kvs.getValueOrNull("env"), "MDC must win the collision, not scoped");
		assertEquals("present", kvs.getValueOrNull("scopedOnly"), "scoped-only key must still come through");
	}

	private static void assertMdcOnly(KeyValues kvs) {
		assertEquals("mdc-env", kvs.getValueOrNull("env"));
		assertNull(kvs.getValueOrNull("scopedOnly"), "nothing registered - scoped-only key must not appear");
	}

	private static RainbowGumMDCAdapter mdcWithEnv() {
		var mdc = new RainbowGumMDCAdapter();
		mdc.put("env", "mdc-env");
		return mdc;
	}

	@Test
	void plainHandlerMergesWithScopedDefaultsRegistered() {
		var handler = LogEventHandler.of("test", e -> {
		}, mdcWithEnv(), SCOPED);
		assertMerged(handler.defaultKeyValues());
	}

	@Test
	void plainHandlerIsUnchangedWithNothingRegistered() {
		var handler = LogEventHandler.of("test", e -> {
		}, mdcWithEnv(), NoopLogEventFactory.INSTANCE);
		assertMdcOnly(handler.defaultKeyValues());
	}

	@Test
	void callerInfoHandlerMergesWithScopedDefaultsRegistered() {
		var handler = LogEventHandler.ofCallerInfo("test", e -> {
		}, mdcWithEnv(), 0, SCOPED);
		assertMerged(handler.defaultKeyValues());
		assertMerged(handler.copyDefaultKeyValues());
	}

	@Test
	void fluentBuilderWithoutAddKeyValueMergesWithScopedDefaultsRegistered() {
		LogEvent[] captured = new LogEvent[1];
		var handler = LogEventHandler.of("test", e -> captured[0] = e, mdcWithEnv(), SCOPED);
		handler.eventBuilder(org.slf4j.event.Level.INFO).log("hello");
		assertMerged(captured[0].keyValues());
	}

	@Test
	void fluentBuilderWithAddKeyValueStillMergesUnderneath() {
		LogEvent[] captured = new LogEvent[1];
		var handler = LogEventHandler.of("test", e -> captured[0] = e, mdcWithEnv(), SCOPED);
		handler.eventBuilder(org.slf4j.event.Level.INFO).addKeyValue("extra", "fromBuilder").log("hello");
		var kvs = captured[0].keyValues();
		assertMerged(kvs);
		assertEquals("fromBuilder", kvs.getValueOrNull("extra"), "the fluent builder's own addKeyValue must survive");
	}

	@Test
	void fluentBuilderKeyValuesAccessorMergesBeforeAnyAddKeyValueCall() {
		var handler = LogEventHandler.of("test", e -> {
		}, mdcWithEnv(), SCOPED);
		var builder = (DepthAwareEventBuilder) handler.eventBuilder(org.slf4j.event.Level.INFO);
		assertMerged(builder.keyValues());
	}

	/*
	 * End to end, through RainbowGumLoggerFactory - proves the actual ServiceRegistry
	 * lookup wiring (not just that the merge logic is correct in isolation once a
	 * LogEventFactory is handed to it directly, which the tests above already cover).
	 */
	@Test
	void rainbowGumLoggerFactoryPicksUpScopedFactoryRegisteredInServiceRegistry() {
		var list = new ListLogOutput();
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config).route(route -> route.appender("list", a -> a.output(list))).build();
		config.serviceRegistry()
			.put(LogEventFactory.class, RainbowGumSLF4JServiceProvider.SCOPED_KEY_VALUES_SERVICE_NAME, SCOPED);
		var mdc = mdcWithEnv();
		try (var g = gum.start()) {
			var factory = new RainbowGumLoggerFactory(g, mdc);
			factory.getLogger("test").info("hello");
		}
		assertEquals(1, list.events().size());
		assertMerged(list.events().get(0).getKey().keyValues());
	}

	@Test
	void rainbowGumLoggerFactoryUnaffectedWhenNothingRegistered() {
		var list = new ListLogOutput();
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config).route(route -> route.appender("list", a -> a.output(list))).build();
		var mdc = mdcWithEnv();
		try (var g = gum.start()) {
			var factory = new RainbowGumLoggerFactory(g, mdc);
			factory.getLogger("test").info("hello");
		}
		assertEquals(1, list.events().size());
		assertMdcOnly(list.events().get(0).getKey().keyValues());
	}

}
