package io.jstach.rainbowgum.benchmark.freeze;

import java.net.URI;
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

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.RainbowGum;

/**
 * Events logged per second through a route with an async publisher and appenders that
 * format the message to an output discarding the bytes.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
public class AsyncRouteBenchmark {

	/**
	 * Arguments logged.
	 */
	@Param
	public Events events;

	/**
	 * Appenders on the route.
	 */
	@Param({ "1", "3" })
	public int appenders;

	private RainbowGum gum;

	/**
	 * Starts a RainbowGum with one async route.
	 */
	@Setup(Level.Trial)
	public void setup() {
		gum = RainbowGum.builder(LogConfig.builder().build()).route(r -> {
			for (int i = 0; i < appenders; i++) {
				r.appender("discard" + i, a -> a.output(new DiscardOutput())
					.formatter(LogFormatter.builder().message().newline().build()));
			}
			r.publisher(PublisherFactory.ofAsync(1024));
		}).build().start();
	}

	/**
	 * Logs one event.
	 */
	@Benchmark
	public void log() {
		gum.log(events.create());
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
