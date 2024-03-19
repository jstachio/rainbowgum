package io.jstach.rainbowgum.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;

class RainbowGumServiceProviderTest {

	@Test
	void testFindProviders() {
		// TODO Ideally we mock a class loader here
		ServiceLoader<RainbowGumServiceProvider> loader = ServiceLoader.load(RainbowGumServiceProvider.class);
		var stream = RainbowGumServiceProvider.findProviders(loader, Configurator.class);
		assertNotNull(stream);
	}

	@Test
	void testProvideConfig() {
		// TODO Ideally we mock a class loader here
		ServiceLoader<RainbowGumServiceProvider> loader = ServiceLoader.load(RainbowGumServiceProvider.class);
		System.setProperty(RainbowGumServiceProviderTest.class.getName(), "test");
		var config = RainbowGumServiceProvider.provideConfig(loader);
		assertNotNull(config);
		String value = config.properties().valueOrNull(RainbowGumServiceProviderTest.class.getName());
		assertEquals("test", value);

		// checker will not allow this.
		// for now it really doesn't hurt anything.
		// System.clearProperty(RainbowGumServiceProviderTest.class.getName());

	}

	@Test
	void testProvide() {
		/*
		 * There is not much to test here as it would require blackbox testing to test the
		 * service loader.
		 */
		var gum = RainbowGumServiceProvider.provide();
		assertNotNull(gum);
	}

	@Test
	void testRunConfiguratorsFail() {
		var a = new FakeConfigurator("AConfigurator", 1);
		var b = new FakeConfigurator("BConfigurator", 100);

		Stream<? extends Configurator> stream = List.<Configurator>of(a, b).stream();
		try {
			Configurator.runConfigurators(stream, LogConfig.builder().build());
			fail();
		}
		catch (IllegalStateException e) {
			String message = e.getMessage();
			assertEquals(
					"Configurators could not find dependencies (returned false) after 4 passes. configurators = [BConfigurator]",
					message);
		}
		assertEquals(1, a.count.get());
		assertEquals(4, b.count.get());
	}

	@Test
	void testRunConfiguratorsSuccess() {
		var a = new FakeConfigurator("AConfigurator", 1);
		var b = new FakeConfigurator("BConfigurator", 3);

		Stream<? extends Configurator> stream = List.<Configurator>of(a, b).stream();
		Configurator.runConfigurators(stream, LogConfig.builder().build());

		assertEquals(1, a.count.get());
		assertEquals(3, b.count.get());
	}

	private static final class FakeConfigurator implements Configurator {

		private final String name;

		AtomicInteger count = new AtomicInteger();

		int succeedAfter = 1;

		public FakeConfigurator(String name, int succeedAfter) {
			super();
			this.name = name;
			this.succeedAfter = succeedAfter;
		}

		@Override
		public boolean configure(LogConfig config) {
			if (count.incrementAndGet() == succeedAfter) {
				return true;
			}
			return false;
		}

		@Override
		public String toString() {
			return name;
		}

	}

}
