package io.jstach.rainbowgum.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class StandardFiltersTest {

	static final LogFormatter FORMATTER = LogFormatter.builder()
		.level()
		.space()
		.loggerName()
		.space()
		.message()
		.newline()
		.build();

	record Result(String output, String metrics) {
	}

	private static Result run(String properties, List<LogEvent> events) {
		var output = new ListLogOutput();
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		try (var gum = RainbowGum.builder(config)
			.route(r -> r.appender("app", a -> a.output(output).formatter(FORMATTER)))
			.build()
			.start()) {
			for (var e : events) {
				gum.log(e);
			}
		}
		var metrics = new StringBuilder();
		config.metrics()
			.snapshot()
			.stream()
			.filter(m -> m.name().startsWith("events."))
			.sorted(Comparator.comparing(LogMetrics.Metric::name))
			.forEach(m -> metrics.append(m.name())
				.append(' ')
				.append(m.level())
				.append(' ')
				.append(m.value())
				.append('\n'));
		return new Result(output.toString(), metrics.toString());
	}

	private static String log(String properties, List<LogEvent> events) {
		return run(properties, events).output();
	}

	private static LogEvent event(long millis, Level level, String logger, String message) {
		return LogEvent.of(Instant.ofEpochMilli(millis), "main", 1, level, logger, message, KeyValues.of(), null);
	}

	private static LogEvent event(long millis, Level level, String message) {
		return event(millis, level, "app", message);
	}

	@Test
	void throttleKeepsABurstThenTheRate() {
		String actual = log("""
				logging.route.default.filters=throttle
				logging.route.default.filter.throttle.rate=1
				logging.route.default.filter.throttle.maxBurst=2
				""", List.of( //
				event(0, Level.INFO, "a"), //
				event(0, Level.INFO, "b"), //
				event(0, Level.INFO, "c"), //
				event(0, Level.WARNING, "d"), //
				event(0, Level.ERROR, "e"), //
				event(1000, Level.INFO, "f"), //
				event(1000, Level.INFO, "g"), //
				event(3000, Level.INFO, "h"), //
				event(3000, Level.INFO, "i"), //
				event(3000, Level.INFO, "j")));
		assertEquals("""
				INFO app a
				INFO app b
				ERROR app e
				INFO app f
				INFO app h
				INFO app i
				""", actual);
	}

	@Test
	void throttleLevelChoosesWhatIsThrottled() {
		String actual = log("""
				logging.route.default.filters=throttle
				logging.route.default.filter.throttle.rate=1
				logging.route.default.filter.throttle.maxBurst=1
				logging.route.default.filter.throttle.level=info
				""", List.of( //
				event(0, Level.INFO, "a"), //
				event(0, Level.INFO, "b"), //
				event(0, Level.WARNING, "c"), //
				event(0, Level.WARNING, "d")));
		assertEquals("""
				INFO app a
				WARN app c
				WARN app d
				""", actual);
	}

	@Test
	void dedupeDropsRepeatsOfTheSameMessage() {
		String actual = log("""
				logging.route.default.filters=dedupe
				logging.route.default.filter.dedupe.repeats=1
				""", List.of( //
				event(0, Level.INFO, "a"), //
				event(0, Level.INFO, "a"), //
				event(0, Level.INFO, "a"), //
				event(0, Level.INFO, "b"), //
				event(0, Level.ERROR, "a"), //
				event(0, Level.ERROR, "a")));
		assertEquals("""
				INFO app a
				INFO app a
				INFO app b
				ERROR app a
				ERROR app a
				""", actual);
	}

	@Test
	void dedupeComparesMessagesBeforeArguments() {
		var formatter = StandardMessageFormatter.SLF4J;
		String actual = log("""
				logging.route.default.filters=dedupe
				logging.route.default.filter.dedupe.repeats=0
				""", List.of( //
				LogEvent.ofOneArg(Instant.EPOCH, "main", 1, Level.INFO, "app", "user {}", KeyValues.of(), formatter,
						"alice"), //
				LogEvent.ofOneArg(Instant.EPOCH, "main", 1, Level.INFO, "app", "user {}", KeyValues.of(), formatter,
						"bob")));
		assertEquals("""
				INFO app user alice
				""", actual);
	}

	@ParameterizedTest
	@CsvSource({ "false,false", "false,true", "true,false", "true,true" })
	void dedupeUsesTemplatesWithAsyncRoutes(boolean filteredAsync, boolean asyncSibling) {
		var output = new ListLogOutput();
		var archive = new ListLogOutput();
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.route.filtered.filters=dedupe
				logging.route.filtered.filter.dedupe.repeats=0
				""").build()).build();
		var builder = RainbowGum.builder(config).route("filtered", r -> {
			r.appender("filtered", a -> a.output(output).formatter(FORMATTER));
			if (filteredAsync) {
				r.publisher(PublisherFactory.ofAsync(16));
			}
		});
		if (asyncSibling) {
			builder.route("archive", r -> r.publisher(PublisherFactory.ofAsync(16))
				.appender("archive", a -> a.output(archive).formatter(FORMATTER)));
		}
		try (var gum = builder.build().start()) {
			for (var user : List.of("alice", "bob")) {
				gum.log(LogEvent.ofOneArg(Instant.EPOCH, "main", 1, Level.INFO, "app", "user {}", KeyValues.of(),
						StandardMessageFormatter.SLF4J, user));
			}
		}
		assertEquals("INFO app user alice\n", output.toString());
		if (asyncSibling) {
			assertEquals("""
					INFO app user alice
					INFO app user bob
					""", archive.toString());
		}
		assertEquals(1L,
				config.metrics()
					.snapshot()
					.stream()
					.filter(m -> m.name().equals(LogMetrics.EVENTS_DEDUPLICATED_METRIC))
					.findFirst()
					.orElseThrow()
					.value());
	}

	@Test
	void dedupeForgetsTheLeastRecentMessage() {
		String actual = log("""
				logging.route.default.filters=dedupe
				logging.route.default.filter.dedupe.repeats=0
				logging.route.default.filter.dedupe.cacheSize=1
				""", List.of( //
				event(0, Level.INFO, "a"), //
				event(0, Level.INFO, "a"), //
				event(0, Level.INFO, "b"), //
				event(0, Level.INFO, "a")));
		assertEquals("""
				INFO app a
				INFO app b
				INFO app a
				""", actual);
	}

	@Test
	void remapMovesLevelsOfTheListedLoggers() {
		String actual = log("""
				logging.level=INFO
				logging.route.default.filters=remap
				logging.route.default.filter.remap.levels=warning:info,error:warning
				logging.route.default.filter.remap.loggers=com.noisy
				""", List.of( //
				event(0, Level.WARNING, "com.noisy", "a"), //
				event(0, Level.ERROR, "com.noisy.Client", "b"), //
				event(0, Level.WARNING, "com.noisyother", "c"), //
				event(0, Level.ERROR, "app", "d")));
		assertEquals("""
				INFO com.noisy a
				WARN com.noisy.Client b
				WARN com.noisyother c
				ERROR app d
				""", actual);
	}

	@Test
	void remapDropsEventsMovedToADisabledLevel() {
		String actual = log("""
				logging.level=INFO
				logging.route.default.filters=remap
				logging.route.default.filter.remap.levels=warning:debug
				""", List.of( //
				event(0, Level.WARNING, "a"), //
				event(0, Level.ERROR, "b")));
		assertEquals("""
				ERROR app b
				""", actual);
	}

	@Test
	void standardFiltersCombineInOrder() {
		String actual = log("""
				logging.route.default.filters=remap,dedupe
				logging.route.default.filter.remap.levels=error:warning
				logging.route.default.filter.dedupe.repeats=0
				""", List.of( //
				event(0, Level.ERROR, "a"), //
				event(0, Level.ERROR, "a")));
		assertEquals("""
				WARN app a
				""", actual);
	}

	@Test
	void droppedEventsAreCounted() {
		var result = run("""
				logging.level=INFO
				logging.route.default.filters=remap,dedupe,throttle
				logging.route.default.filter.remap.levels=warning:debug
				logging.route.default.filter.dedupe.repeats=0
				logging.route.default.filter.throttle.rate=1
				logging.route.default.filter.throttle.maxBurst=1
				""", List.of( //
				event(0, Level.WARNING, "a"), //
				event(0, Level.INFO, "b"), //
				event(0, Level.INFO, "b"), //
				event(0, Level.INFO, "c"), //
				event(0, Level.INFO, "d")));
		assertEquals("""
				INFO app b
				""", result.output());
		assertEquals("""
				events.deduplicated WARNING 1
				events.filtered INFO 4
				events.throttled WARNING 2
				""", result.metrics());
	}

	@Test
	void noFilterMetricsWithoutFilters() {
		var result = run("", List.of(event(0, Level.INFO, "a")));
		assertEquals("", result.metrics());
	}

	@Test
	void remapRequiresLevels() {
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> log("logging.route.default.filters=remap", List.of()));
		assertEquals("""
				Validation failed for io.jstach.rainbowgum.filter.RemapFilterBuilder:
				Property missing. key:
				    'logging.route.default.filter.remap.levels' from:
				        PROPERTIES_STRING[logging.route.default.filter.remap.levels]""", e.getMessage());
	}

	@Test
	void remapRejectsABadPair() {
		var e = assertThrows(LogProperty.ValidationException.class, () -> log("""
				logging.route.default.filters=remap
				logging.route.default.filter.remap.levels=warning
				""", List.of()));
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.filter.RemapFilterBuilder: levels must be from:to pairs but has 'warning'.""",
				e.getMessage());
	}

	@Test
	void throttleRejectsANonPositiveRate() {
		var e = assertThrows(LogProperty.ValidationException.class, () -> log("""
				logging.route.default.filters=throttle
				logging.route.default.filter.throttle.rate=0
				""", List.of()));
		assertEquals("""
				Validation failed for io.jstach.rainbowgum.filter.ThrottleFilterBuilder: rate must be positive.""",
				e.getMessage());
	}

}
