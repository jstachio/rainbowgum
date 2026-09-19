package io.jstach.rainbowgum.scopedkeyvalues.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.scopedkeyvalues.ScopedKeyValues;
import io.jstach.rainbowgum.slf4j.RainbowGumSLF4JServiceProvider;

/*
 * End to end, through the real public SLF4J entry point (not the package-private
 * RainbowGumLoggerFactory rainbowgum-slf4j's own tests use, since this is a different
 * module) - proves that this module's ScopedKeyValuesConfigurator, discovered via the
 * normal ServiceLoader path (LogConfig.Builder#serviceLoader(), not on by default - see
 * that method's own javadoc; RainbowGum.defaults()'s real bootstrap path always enables
 * it, this test just has to opt in explicitly the same way), is enough for a plain,
 * un-fluent SLF4J log call to pick up ScopedKeyValues underneath MDC with zero other
 * wiring - exactly as advertised in ScopedKeyValues' and this module's own javadoc. Also
 * proves the plain java.util.ServiceLoader.load(ScopedKeyValuesProvider.class) lookup
 * the ScopedKeyValues facade does (in a different module) actually finds
 * ScopedKeyValuesProviderImpl here - that lookup is unconditional, unlike
 * LogConfig.Builder#serviceLoader() above.
 */
class ScopedKeyValuesConfiguratorTest {

	@Test
	void scopedKeyValuesAreMergedUnderneathMdcForAPlainLogCall() {
		var list = new ListLogOutput();
		var config = LogConfig.builder().serviceLoader().build();
		var gum = RainbowGum.builder(config).route(route -> route.appender("list", a -> a.output(list))).build();

		var provider = new RainbowGumSLF4JServiceProvider();
		provider.initialize(gum);
		var logger = provider.getLoggerFactory().getLogger("test");
		// the MDCAdapter bound to *this* test's provider instance, not the
		// JVM-global org.slf4j.MDC facade (which is bound to whatever provider SLF4J's
		// own bootstrap discovered first, an entirely separate instance).
		var mdc = provider.getMDCAdapter();

		try (var g = gum.start()) {
			mdc.put("requestScopedMdc", "mdc-value");
			try {
				ScopedKeyValues.builder().add("requestId", "abc123").run(() -> logger.info("hello"));
			}
			finally {
				mdc.remove("requestScopedMdc");
			}
		}

		assertEquals(1, list.events().size());
		var keyValues = list.events().get(0).getKey().keyValues();
		assertEquals("abc123", keyValues.getValueOrNull("requestId"));
		assertEquals("mdc-value", keyValues.getValueOrNull("requestScopedMdc"));
	}

	@Test
	void plainLogCallUnaffectedWhenOutsideAnyScopedKeyValuesPush() {
		var list = new ListLogOutput();
		var config = LogConfig.builder().serviceLoader().build();
		var gum = RainbowGum.builder(config).route(route -> route.appender("list", a -> a.output(list))).build();

		var provider = new RainbowGumSLF4JServiceProvider();
		provider.initialize(gum);
		var logger = provider.getLoggerFactory().getLogger("test");

		try (var g = gum.start()) {
			logger.info("hello");
		}

		assertEquals(1, list.events().size());
		assertNull(list.events().get(0).getKey().keyValues().getValueOrNull("requestId"));
	}

}
