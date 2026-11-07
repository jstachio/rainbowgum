package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.LogAppender.AppenderType;
import io.jstach.rainbowgum.LogOutput.WriteMethod;
import io.jstach.rainbowgum.output.ListLogOutput;

class AppenderReentrantBufferTest {

	@ParameterizedTest
	@EnumSource(AppenderType.class)
	void eachLevelOfNestedEncodingKeepsItsOwnBuffer(AppenderType type) {
		var config = LogConfig.builder().build();
		var output = new ListLogOutput();
		var encoder = LogEncoder.of(LogFormatter.builder().message().build()).provide("test", config);
		var appender = DirectLogAppender.of("test", output, encoder, type, Set.of(), config);
		Object middleArg = new Object() {
			@Override
			public String toString() {
				appender.append(event("leaf"));
				return "arg";
			}
		};
		Object outerArg = new Object() {
			@Override
			public String toString() {
				appender.append(TestLogEventFactory.of().eventOneArg(System.Logger.Level.INFO, "middle {}", middleArg));
				return "arg";
			}
		};
		appender.append(TestLogEventFactory.of().eventOneArg(System.Logger.Level.INFO, "outer {}", outerArg));
		assertEquals(List.of("leaf", "middle arg", "outer arg"),
				output.events().stream().map(e -> e.getValue()).toList());
	}

	@ParameterizedTest
	@EnumSource(AppenderType.class)
	void reentryDuringWriteDoesNotOverwriteTheUndrainedOuterBuffer(AppenderType type) {
		var config = LogConfig.builder().build();
		var reference = new AtomicReference<LogAppender>();
		var output = new ListLogOutput() {
			@Override
			public void write(LogEvent event, LogEncoder.Buffer buffer) {
				if (event.message().equals("outer")) {
					Objects.requireNonNull(reference.get()).append(event("inner"));
				}
				buffer.drain(this, event);
			}
		};
		var encoder = LogEncoder.of(LogFormatter.builder().message().build()).provide("test", config);
		var appender = DirectLogAppender.of("test", output, encoder, type, Set.of(), config);
		reference.set(appender);
		appender.append(event("outer"));
		assertEquals(List.of("inner", "outer"), output.events().stream().map(e -> e.getValue()).toList());
	}

	static Stream<Arguments> nestedAppends() {
		List<Arguments> cases = new ArrayList<>();
		for (var type : AppenderType.values()) {
			for (var outer : AppendMode.values()) {
				for (var inner : AppendMode.values()) {
					for (var writeMethod : WriteMethod.values()) {
						for (var flags : List.of(Set.<AppenderFlag>of(), Set.of(AppenderFlag.REENTRY_DROP),
								Set.of(AppenderFlag.REENTRY_LOG))) {
							cases.add(Arguments.of(type, outer, inner, writeMethod, flags));
						}
					}
				}
			}
		}
		return cases.stream();
	}

	@ParameterizedTest
	@MethodSource("nestedAppends")
	void nestedEventsDoNotOverwriteTheOuterBuffer(AppenderType type, AppendMode outer, AppendMode inner,
			WriteMethod writeMethod, Set<AppenderFlag> flags) {
		var config = LogConfig.builder().build();
		var output = new ListLogOutput() {
			@Override
			public LogEncoder.BufferHints bufferHints() {
				return writeMethod;
			}
		};
		var encoder = LogEncoder.of(LogFormatter.builder().message().build()).provide("test", config);
		var appender = DirectLogAppender.of("test", output, encoder, type, flags, config);
		Object arg = new Object() {
			@Override
			public String toString() {
				inner.append(appender, event("inner one"), event("inner two"));
				return "arg";
			}
		};
		outer.append(appender,
				TestLogEventFactory.of().eventOneArg(System.Logger.Level.INFO, "outer {}", KeyValues.of(), arg),
				event("tail"));
		boolean drop = !flags.isEmpty() && !(type == AppenderType.LOCK_NEW_BUFFER && outer == AppendMode.SINGLE);
		assertEquals(drop ? List.of("outer arg", "tail") : List.of("inner one", "inner two", "outer arg", "tail"),
				output.events().stream().map(e -> e.getValue()).toList());
		assertEquals(drop ? 2 : 0, metric(config, LogMetrics.EVENTS_DROPPED_METRIC));
		assertEquals(0, metric(config, LogMetrics.EVENTS_FAILED_METRIC));
	}

	static Stream<Arguments> bufferFailures() {
		return Stream.of(AppenderType.LOCK_THREAD_LOCAL_BUFFER, AppenderType.SYNCHRONIZED_THREAD_LOCAL_BUFFER)
			.flatMap(type -> Stream.of(AppendMode.values())
				.flatMap(mode -> Stream.of(Failure.CREATE, Failure.CLEAR)
					.map(failure -> Arguments.of(type, mode, failure))));
	}

	@ParameterizedTest
	@MethodSource("bufferFailures")
	void bufferSetupFailureIsReportedAndDoesNotPoisonLaterEvents(AppenderType type, AppendMode mode, Failure failure) {
		var config = LogConfig.builder().build();
		var output = new ListLogOutput();
		var encoder = new FailingEncoder(config);
		encoder.failure = failure;
		var appender = DirectLogAppender.of("test", output, encoder, type, Set.of(AppenderFlag.REENTRY_DROP), config);
		assertDoesNotThrow(() -> mode.append(appender, event("failed")));
		mode.append(appender, event("after"));
		assertEquals(List.of("after"), output.events().stream().map(e -> e.getValue()).toList());
		assertEquals(1, config.alerts().dump().size());
		assertEquals(mode == AppendMode.BATCH ? "appender 'test' failed to append batch of 1 event(s)"
				: "appender 'test' failed to append event", config.alerts().dump().getFirst().message());
		assertEquals(1, metric(config, LogMetrics.EVENTS_FAILED_METRIC));
		assertEquals(0, metric(config, LogMetrics.EVENTS_DROPPED_METRIC));
	}

	@ParameterizedTest
	@MethodSource("bufferFailures")
	void reentrantBufferFailurePreservesTheOuterEvent(AppenderType type, AppendMode mode, Failure failure) {
		var config = LogConfig.builder().build();
		var output = new ListLogOutput();
		var encoder = new FailingEncoder(config);
		var appender = DirectLogAppender.of("test", output, encoder, type, Set.of(), config);
		Object arg = new Object() {
			@Override
			public String toString() {
				encoder.failure = failure;
				mode.append(appender, event("inner"));
				return "arg";
			}
		};
		mode.append(appender,
				TestLogEventFactory.of().eventOneArg(System.Logger.Level.INFO, "outer {}", KeyValues.of(), arg));
		mode.append(appender, event("after"));
		assertEquals(List.of("outer arg", "after"), output.events().stream().map(e -> e.getValue()).toList());
		assertEquals(1, config.alerts().dump().size());
		assertEquals(1, metric(config, LogMetrics.EVENTS_FAILED_METRIC));
	}

	private static LogEvent event(String message) {
		return TestLogEventFactory.of().event(message);
	}

	private static long metric(LogConfig config, String name) {
		return config.metrics()
			.snapshot()
			.stream()
			.filter(m -> m.name().equals(name))
			.mapToLong(LogMetrics.Metric::value)
			.sum();
	}

	enum AppendMode {

		SINGLE, BATCH;

		void append(LogAppender appender, LogEvent... events) {
			if (this == BATCH) {
				appender.append(events, events.length);
			}
			else {
				for (var event : events) {
					appender.append(event);
				}
			}
		}

	}

	enum Failure {

		NONE, CREATE, CLEAR

	}

	static final class FailingEncoder implements LogEncoder {

		final LogEncoder delegate;

		Failure failure = Failure.NONE;

		FailingEncoder(LogConfig config) {
			delegate = LogEncoder.of(LogFormatter.builder().message().build()).provide("test", config);
		}

		void check(Failure phase) {
			if (failure == phase) {
				failure = Failure.NONE;
				throw new IllegalStateException("buffer " + phase);
			}
		}

		@Override
		public Buffer buffer(BufferHints hints) {
			check(Failure.CREATE);
			return new FailingBuffer(delegate.buffer(hints), this);
		}

		@Override
		public void encode(LogEvent event, Buffer buffer) {
			buffer.clear();
			delegate.encode(event, ((FailingBuffer) buffer).delegate());
		}

	}

	record FailingBuffer(LogEncoder.Buffer delegate, FailingEncoder encoder) implements LogEncoder.Buffer {
		@Override
		public void clear() {
			encoder.check(Failure.CLEAR);
			delegate.clear();
		}

		@Override
		public void drain(LogOutput output, LogEvent event) {
			delegate.drain(output, event);
		}
	}

}
