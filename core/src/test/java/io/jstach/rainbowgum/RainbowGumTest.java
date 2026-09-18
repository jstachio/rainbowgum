package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.System.Logger.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogPublisher.PublisherFactory;

/*
 * Touches GlobalLogRouter.INSTANCE and RainbowGum's static current-instance holder, the
 * same JVM-wide state RainbowGumEntryPointTest uses - @Isolated keeps this from racing it
 * (or anything else) under the "fast" profile's parallel test execution; SAME_THREAD keeps
 * this class's own methods from racing each other.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class RainbowGumTest {

	@Test
	void testAsyncPublisher() throws Exception {
		var formatter = LogFormatter.builder() //
			.timeStamp() //
			.space() //
			.text("[") //
			.threadName() //
			.text("] ") //
			.level() //
			.space() //
			.loggerName() //
			.text(" ") //
			.message() //
			.newline() //
			.build();

		var sysout = LogAppender.builder("sysout") //
			.output(LogOutput.ofStandardOut())
			.formatter(formatter)
			.build();

		{
			var route = GlobalLogRouter.INSTANCE.route("stuff", Level.WARNING);
			if (route.isEnabled()) {
				route.log(LogEventFactory.of("stuff")
					.eventNoArg(Level.WARNING, "first", KeyValues.of(), (Throwable) null));
			}
		}
		try (var gum = RainbowGum.builder().route(r -> {
			r.publisher(PublisherFactory.async().build());
			r.appender(sysout);
			r.level(Level.WARNING, "stuff");
		}).set()) {

			assertEquals(1, ShutdownManager.shutdownHooks().size());

			var router = gum.router();
			gum.log(LogEventFactory.of("stuff").eventNoArg(Level.INFO, "Stuff", KeyValues.of(), (Throwable) null));
			gum.log(LogEventFactory.of("stuff").eventNoArg(Level.ERROR, "bad", KeyValues.of(), (Throwable) null));

			gum.log(LogEventFactory.of("stuff")
				.eventOneArg(Level.WARNING, "builder info - {}", KeyValues.of(), "hello"));

			boolean enabled = router.route("stuff", Level.INFO).isEnabled();
			assertFalse(enabled);

			Thread.sleep(50);
		}

		assertEquals(0, ShutdownManager.shutdownHooks().size());

	}

}
