package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.helpers.BasicMarkerFactory;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class RainbowGumSLF4JServiceProviderTest {

	@Test
	void testGetLoggerFactoryBeforeInitializeThrows() {
		var provider = new RainbowGumSLF4JServiceProvider();
		var e = assertThrows(IllegalStateException.class, provider::getLoggerFactory);
		assertEquals("slf4j was not initialized correctly", e.getMessage());
	}

	@Test
	void testGetLoggerFactoryAfterInitializeReturnsFactory() {
		var provider = new RainbowGumSLF4JServiceProvider();
		provider.initialize(RainbowGum.builder().build());
		assertInstanceOf(RainbowGumLoggerFactory.class, provider.getLoggerFactory());
	}

	/*
	 * The MDC contributor the module's configurator registers exposes and clears the MDC
	 * used by org.slf4j.MDC, even after an isolated provider is initialized.
	 */
	@Test
	void testKeyValuesContributorExposesAndClearsMdc() {
		var config = LogConfig.builder().serviceLoader().build();
		var mdc = org.slf4j.MDC.getMDCAdapter();
		try (var gum = RainbowGum.builder(config).set()) {
			var provider = new RainbowGumSLF4JServiceProvider();
			provider.initialize(gum);
			provider.getMDCAdapter().put("requestId", "isolated");
			var contributor = KeyValuesContributor.global();
			mdc.put("requestId", "abc");
			assertEquals("abc", contributor.keyValues().getValueOrNull("requestId"));
			contributor.clear();
			assertNull(mdc.get("requestId"));
			assertTrue(contributor.keyValues().isEmpty());
			assertEquals("isolated", provider.getMDCAdapter().get("requestId"));
		}
		finally {
			mdc.clear();
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void globalCleanupPreventsMdcLeakingToNextRequest(boolean disabled) {
		var properties = LogProperties.builder()
			.fromProperties(disabled ? "logging.keyvalues.disabled=slf4j" : "")
			.build();
		var config = LogConfig.builder().properties(properties).serviceLoader().build();
		var output = new ListLogOutput();
		try (var gum = RainbowGum.builder(config)
			.route(route -> route.appender("list", appender -> appender.output(output)))
			.set()) {
			var logger = org.slf4j.LoggerFactory.getLogger(getClass().getName() + "." + disabled);
			org.slf4j.MDC.put("requestId", "previous-request");
			logger.info("previous request");
			assertEquals(1, output.events().size());
			assertEquals("previous-request",
					output.events().getFirst().getKey().keyValues().getValueOrNull("requestId"));
			KeyValuesContributor.global().clear();
			logger.info("next request");
			assertNull(org.slf4j.MDC.get("requestId"));
			assertEquals(2, output.events().size());
			assertNull(output.events().getLast().getKey().keyValues().getValueOrNull("requestId"));
		}
		finally {
			org.slf4j.MDC.clear();
		}
	}

	@Test
	void testMarkerFactoryMdcAdapterAndApiVersion() {
		var provider = new RainbowGumSLF4JServiceProvider();
		assertInstanceOf(BasicMarkerFactory.class, provider.getMarkerFactory());
		assertInstanceOf(RainbowGumMDCAdapter.class, provider.getMDCAdapter());
		assertNotNull(provider.getMDCAdapter());
		assertEquals("2.0", provider.getRequestedApiVersion());
	}

	@Test
	void testLoggingMdcTypeThreadLocalByDefaultStoresAndReturnsValue() {
		var provider = new RainbowGumSLF4JServiceProvider();
		provider.initialize(RainbowGum.builder().build());
		var mdc = provider.getMDCAdapter();
		mdc.put("key", "value");
		assertEquals("value", mdc.get("key"));
	}

	@Test
	void testLoggingMdcTypeNoopPropertyMakesPutAndGetNoops() {
		var props = LogProperties.builder().fromProperties("logging.mdc.type=NOOP").build();
		var config = LogConfig.builder().properties(props).build();
		var provider = new RainbowGumSLF4JServiceProvider();
		provider.initialize(RainbowGum.builder(config).build());
		var mdc = provider.getMDCAdapter();
		mdc.put("key", "value");
		assertNull(mdc.get("key"));
		assertNull(mdc.getCopyOfContextMap());
	}

	@Test
	void testLoggingMdcTypeBadValueFailsLoudlyInsteadOfSilentlyResolvingToDefault() {
		var props = LogProperties.builder().fromProperties("logging.mdc.type=BOGUS").build();
		var config = LogConfig.builder().properties(props).build();
		var provider = new RainbowGumSLF4JServiceProvider();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> provider.initialize(RainbowGum.builder(config).build()));
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.slf4j.RainbowGumSLF4JServiceProvider:
						Error for property. key: 'logging.mdc.type' from PROPERTIES_STRING[logging.mdc.type], 'BOGUS' is not a valid value for io.jstach.rainbowgum.slf4j.RainbowGumSLF4JServiceProvider.MDCType. Valid values: 'thread_local', 'noop'""",
				e.getMessage());
	}

	@Test
	void testLoggingGlobalThreadlocalDisabledPropertyAlsoMakesPutAndGetNoops() {
		var props = LogProperties.builder().fromProperties("logging.global.threadlocalDisabled=TRUE").build();
		var config = LogConfig.builder().properties(props).build();
		var provider = new RainbowGumSLF4JServiceProvider();
		provider.initialize(RainbowGum.builder(config).build());
		var mdc = provider.getMDCAdapter();
		mdc.put("key", "value");
		assertNull(mdc.get("key"));
	}

	/*
	 * A pure ServiceLoader marker with no behavior of its own - closing this out just for
	 * the constructor, since "will unlikely be called" per its own javadoc left it at 0%
	 * coverage.
	 */
	@Test
	void testEagerLoadMarkerIsInstantiable() {
		assertInstanceOf(io.jstach.rainbowgum.spi.RainbowGumServiceProvider.RainbowGumEagerLoad.class,
				new SLF4JRainbowGumEagerLoad());
	}

}
