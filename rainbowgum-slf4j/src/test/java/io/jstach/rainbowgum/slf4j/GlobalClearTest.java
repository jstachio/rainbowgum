package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor.Source.Standard;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService;

@Isolated
class GlobalClearTest {

	@Test
	void factoryUsesTheQueueWithoutOldContributorsOrDecoratorsAfterClose() {
		var oldDecorations = new AtomicInteger();
		var oldContributions = new AtomicInteger();
		var oldConfig = LogConfig.builder().configurator(decorator("old", oldDecorations)).build();
		KeyValuesContributor.register(oldConfig.serviceRegistry(), Standard.USER, () -> {
			oldContributions.incrementAndGet();
			return KeyValues.of(Map.of("tenant", "old"));
		});
		var oldOutput = new ListLogOutput();
		try (var first = gum(oldConfig, oldOutput)) {
			var mdc = new RainbowGumMDCAdapter();
			mdc.put("mdc", "retained");
			var factory = new RainbowGumLoggerFactory(first, mdc);
			factory.getLogger("before.clear").info("before");
			assertEquals(1, oldDecorations.get());
			assertEquals(1, oldContributions.get());
			first.close();
			assertNull(RainbowGum.getOrNull());
			var during = factory.getLogger("during.clear");
			during.info("during");
			assertEquals(1, oldOutput.events().size(), "a new logger must not write into the closed output");
			assertEquals(1, oldDecorations.get());
			assertEquals(1, oldContributions.get());

			var newDecorations = new AtomicInteger();
			var newConfig = LogConfig.builder().configurator(decorator("new", newDecorations)).build();
			KeyValuesContributor.register(newConfig.serviceRegistry(), Standard.USER,
					() -> KeyValues.of(Map.of("tenant", "new")));
			var output = new ListLogOutput();
			try (var second = gum(newConfig, output)) {
				assertEquals(1, output.events().size(), "the queued message must drain to the next binding");
				var queued = output.events().getFirst().getKey();
				assertEquals("during", queued.message());
				assertNull(queued.keyValues().getValueOrNull("tenant"));
				assertEquals("retained", queued.keyValues().getValueOrNull("mdc"));
				during.info("after");
				factory.getLogger("after.clear").info("new name");
				assertEquals(3, output.events().size());
				assertEquals("new", output.events().get(1).getKey().keyValues().getValueOrNull("tenant"));
				assertEquals("new", output.events().get(2).getKey().keyValues().getValueOrNull("tenant"));
				assertEquals(1, newDecorations.get());
				assertEquals(1, oldDecorations.get());
				assertEquals(1, oldContributions.get());
			}
			finally {
				mdc.clear();
			}
		}
	}

	private static RainbowGum gum(LogConfig config, ListLogOutput output) {
		return RainbowGum.builder(config).route(r -> r.appender("list", a -> a.output(output))).set();
	}

	private static LoggerDecoratorService decorator(String name, AtomicInteger calls) {
		return new LoggerDecoratorService() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public Logger decorate(RainbowGum gum, DepthAwareLogger logger, int depth) {
				calls.incrementAndGet();
				return logger;
			}
		};
	}

}
