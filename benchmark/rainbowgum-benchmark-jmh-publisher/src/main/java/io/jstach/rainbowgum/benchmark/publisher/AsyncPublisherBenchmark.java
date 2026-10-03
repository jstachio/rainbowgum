package io.jstach.rainbowgum.benchmark.publisher;

import java.lang.System.Logger;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.publisher.OpusAsyncPublisher;

/**
 * Logging throughput through a real route with an asynchronous publisher, comparing the
 * original {@code BlockingQueueAsyncLogPublisher} (the registry's default async
 * publisher) with {@link OpusAsyncPublisher}. The output discards the bytes so the
 * publisher's hand off and contention dominate. Run with several thread counts
 * ({@code -t 1}, {@code -t 4}, {@code -t 16}); the score is events logged per second.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
public class AsyncPublisherBenchmark {

	/**
	 * Publisher implementation.
	 */
	@Param({ "blockingQueue", "opus" })
	public String publisher;

	private RainbowGum gum;

	private LogEvent event;

	/**
	 * Starts a RainbowGum with the chosen publisher.
	 */
	@Setup(Level.Trial)
	public void setup() {
		PublisherFactory factory = switch (publisher) {
			case "blockingQueue" -> PublisherFactory.ofAsync(1024);
			case "opus" -> OpusAsyncPublisher.builder().bufferSize(1024).build();
			default -> throw new IllegalArgumentException(publisher);
		};
		gum = RainbowGum.builder(LogConfig.builder().build()).route(r -> {
			r.appender("null",
					a -> a.output(new DiscardOutput()).formatter(LogFormatter.builder().message().newline().build()));
			r.publisher(factory);
		}).build().start();
		event = LogEvent.of(Instant.ofEpochSecond(1_700_000_000L), "main", 1, Logger.Level.INFO,
				"com.example.orders.OrderController", "Handled request path=/api/orders/123 status=200 durationMs=12",
				KeyValues.of(), null);
	}

	/**
	 * Logs one event.
	 */
	@Benchmark
	public void log() {
		gum.log(event);
	}

	/**
	 * Closes the RainbowGum.
	 */
	@TearDown(Level.Trial)
	public void tearDown() {
		gum.close();
	}

	static final class DiscardOutput implements LogOutput {

		long bytes;

		@Override
		public void write(LogEvent event, byte[] b, int off, int len, ContentType contentType) {
			bytes += len;
		}

		@Override
		public void flush() {
		}

		@Override
		public URI uri() {
			return URI.create("discard:///");
		}

		@Override
		public OutputType type() {
			return OutputType.MEMORY;
		}

		@Override
		public void close() {
		}

	}

}
