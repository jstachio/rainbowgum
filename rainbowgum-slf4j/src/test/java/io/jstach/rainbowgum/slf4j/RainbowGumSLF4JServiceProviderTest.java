package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.slf4j.helpers.BasicMarkerFactory;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;

/*
 * Never referenced by any test - this is the real org.slf4j.spi.SLF4JServiceProvider
 * implementation SLF4J's own ServiceLoader discovers. Its parameterless initialize()
 * has real global side effects (System properties, full RainbowGum.of() service
 * discovery), which is exactly why the class provides initialize(RainbowGum) as a
 * dedicated test seam (see its own javadoc) - tested through that instead.
 */
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

	@Test
	void testMarkerFactoryMdcAdapterAndApiVersion() {
		var provider = new RainbowGumSLF4JServiceProvider();
		assertInstanceOf(BasicMarkerFactory.class, provider.getMarkerFactory());
		assertInstanceOf(RainbowGumMDCAdapter.class, provider.getMDCAdapter());
		assertNotNull(provider.getMDCAdapter());
		assertEquals("2.0", provider.getRequestedApiVersion());
	}

	@Test
	void testLoggingMdcEnabledByDefaultStoresAndReturnsValue() {
		var provider = new RainbowGumSLF4JServiceProvider();
		provider.initialize(RainbowGum.builder().build());
		var mdc = provider.getMDCAdapter();
		mdc.put("key", "value");
		assertEquals("value", mdc.get("key"));
	}

	@Test
	void testLoggingMdcDisabledPropertyMakesPutAndGetNoops() {
		var props = LogProperties.builder().fromProperties("logging.mdc=DISABLED").build();
		var config = LogConfig.builder().properties(props).build();
		var provider = new RainbowGumSLF4JServiceProvider();
		provider.initialize(RainbowGum.builder(config).build());
		var mdc = provider.getMDCAdapter();
		mdc.put("key", "value");
		assertNull(mdc.get("key"));
		assertNull(mdc.getCopyOfContextMap());
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
