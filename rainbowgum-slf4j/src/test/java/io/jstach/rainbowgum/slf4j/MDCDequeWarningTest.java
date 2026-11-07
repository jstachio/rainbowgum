package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.System.Logger.Level;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.RainbowGum;

/*
 * Binds Rainbow Gum globally and resets the adapter's JVM-wide warning flag.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class MDCDequeWarningTest {

	@Test
	void dequeApiDoesNothingAndWarnsOnce() {
		ArrayMDCAdapter.resetDequeWarning();
		var config = LogConfig.builder().build();
		try (var gum = RainbowGum.builder(config).set()) {
			var mdc = new RainbowGumMDCAdapter();
			mdc.pushByKey("key", "value");
			assertNull(mdc.popByKey("key"));
			assertNull(mdc.getCopyOfDequeByKey("key"));
			mdc.clearDequeByKey("key");
			var warnings = config.alerts()
				.dump()
				.stream()
				.filter(a -> a.message().contains("deque by key"))
				.map(a -> a.level())
				.toList();
			assertEquals(List.of(Level.WARNING), warnings);
		}
		finally {
			ArrayMDCAdapter.resetDequeWarning();
		}
	}

}
