package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * Binds Rainbow Gum globally and sets the queue size system property.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class GlobalQueueTest {

	final @Nullable String originalSize = System.getProperty(LogProperties.GLOBAL_QUEUE_SIZE_PROPERTY);

	@AfterEach
	void restoreSize() {
		if (originalSize == null) {
			System.clearProperty("logging.global.queue.size");
		}
		else {
			System.setProperty(LogProperties.GLOBAL_QUEUE_SIZE_PROPERTY, originalSize);
		}
	}

	@Test
	void queuedEventsAreFrozen() {
		var queue = QueueEventsRouter.of();
		var argument = new StringBuilder("original");
		queue.log(event("value {}", argument));
		argument.setLength(0);
		argument.append("changed");
		var output = new ListLogOutput();
		try (var gum = gum(LogConfig.builder().build(), output)) {
			gum.start();
			queue.drain((InternalRootRouter) gum.router());
		}
		assertTrue(output.toString().contains("value original"), output.toString());
	}

	@Test
	void queueKeepsAtMostItsSizeAndCountsDrops() {
		System.setProperty(LogProperties.GLOBAL_QUEUE_SIZE_PROPERTY, "2");
		var queue = QueueEventsRouter.of();
		for (int i = 0; i < 5; i++) {
			queue.log(event("event " + i, null));
		}
		assertEquals(3, queue.dropped());
		var output = new ListLogOutput();
		try (var gum = gum(LogConfig.builder().build(), output)) {
			gum.start();
			queue.drain((InternalRootRouter) gum.router());
		}
		assertEquals(2, output.events().size());
	}

	@Test
	void eventLoggedToADrainedQueueReachesTheBoundRouter() {
		var output = new ListLogOutput();
		try (var gum = RainbowGum.builder(LogConfig.builder().build())
			.route(r -> r.appender("list", a -> a.output(output)))
			.set()) {
			var queue = QueueEventsRouter.of();
			queue.drain((InternalRootRouter) gum.router());
			// As a logger still holding the queue, or a log call that read it just before
			// the swap.
			queue.log(event("late", null));
		}
		assertTrue(output.toString().contains("late"), output.toString());
	}

	@Test
	void bindingReportsEventsDroppedWhileNothingWasBound() {
		System.setProperty(LogProperties.GLOBAL_QUEUE_SIZE_PROPERTY, "1");
		// Closing installs a fresh queue, which reads the size property.
		RainbowGum.builder(LogConfig.builder().build()).set().close();
		for (int i = 0; i < 3; i++) {
			GlobalLogRouter.INSTANCE.log(event("while unbound " + i, null));
		}
		var config = LogConfig.builder().build();
		var output = new ListLogOutput();
		try (var gum = RainbowGum.builder(config).route(r -> r.appender("list", a -> a.output(output))).set()) {
			var alert = config.alerts()
				.dump()
				.stream()
				.filter(a -> a.message().startsWith("Dropped "))
				.findFirst()
				.orElseThrow();
			assertEquals(Level.WARNING, alert.level());
			assertEquals("Dropped 2 events logged while no Rainbow Gum was bound because the queue was full. See "
					+ LogProperties.GLOBAL_QUEUE_SIZE_PROPERTY + ".", alert.message());
		}
		assertTrue(output.toString().contains("while unbound 0"), output.toString());
	}

	private static RainbowGum gum(LogConfig config, ListLogOutput output) {
		return RainbowGum.builder(config).route(r -> r.appender("list", a -> a.output(output))).build();
	}

	private static LogEvent event(String message, @Nullable Object arg) {
		return LogEvent.ofOneArg(Instant.EPOCH, "main", 1, Level.INFO, "test", message, KeyValues.of(),
				StandardMessageFormatter.SLF4J, arg);
	}

}
