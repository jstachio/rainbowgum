package io.jstach.rainbowgum.disruptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lmax.disruptor.util.DaemonThreadFactory;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.output.ListLogOutput;

class DisruptorLogPublisherTest {

	private static final int BUFFER_SIZE = 8;

	@Test
	void closeDrainsPublishedEventsAndClosesAppenders() {
		var config = LogConfig.builder().build();
		var output = new ClosingListLogOutput();
		var publisher = publisher(config, output);
		publisher.start(config);
		for (int i = 0; i < 100; i++) {
			publisher.log(event("event" + i));
		}
		publisher.close();
		assertEquals(100, output.events().size());
		assertTrue(output.closed);
	}

	@Test
	void logAfterCloseDoesNotBlock() {
		var config = LogConfig.builder().build();
		var output = new ClosingListLogOutput();
		var publisher = publisher(config, output);
		publisher.start(config);
		publisher.close();
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			for (int i = 0; i < BUFFER_SIZE * 4; i++) {
				publisher.log(event("late" + i));
			}
		});
		assertEquals(0, output.events().size());
	}

	@Test
	void errorFromAnOutputDoesNotStopTheConsumer() {
		var config = LogConfig.builder().build();
		var output = new ClosingListLogOutput();
		output.setConsumer((e, s) -> {
			if (e.message().equals("boom")) {
				throw new Error("boom");
			}
		});
		var publisher = publisher(config, output);
		publisher.start(config);
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			publisher.log(event("boom"));
			for (int i = 0; i < BUFFER_SIZE * 4; i++) {
				publisher.log(event("after" + i));
			}
			publisher.close();
		});
		assertEquals(BUFFER_SIZE * 4,
				output.events().stream().filter(e -> e.getKey().message().startsWith("after")).count());
	}

	private static DisruptorLogPublisher publisher(LogConfig config, ListLogOutput output) {
		LogEncoder encoder = LogFormatter.builder().message().encoder().build().provide("test", config);
		LogAppender appender = LogAppender.builder("test")
			.output(output)
			.encoder(encoder)
			.build()
			.provide("test", config);
		return DisruptorLogPublisher.of(List.of(appender), DaemonThreadFactory.INSTANCE, BUFFER_SIZE, config.alerts());
	}

	private static LogEvent event(String message) {
		return LogEvent.of(Instant.EPOCH, "main", 1, Level.INFO, "test", message, KeyValues.of(), null);
	}

	static final class ClosingListLogOutput extends ListLogOutput {

		volatile boolean closed;

		@Override
		public void close() {
			// ListLogOutput.close() does nothing, and calling it crashes the Checker
			// Framework (doc/checker-framework-bugs.md).
			closed = true;
		}

	}

}
