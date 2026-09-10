package io.jstach.rainbowgum.benchmark.jmh;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.RainbowGum;

/**
 * Isolates the router dispatch decision (route()/isEnabled()/log()) from message
 * formatting, encoding, and actual I/O - both scenarios write to a no-op
 * {@link LogOutput} so the only thing being measured is
 * {@code RootRouter.route(name, level)} followed by a conditional
 * {@code Route.log(event)}, comparing a single configured route (the
 * {@code SingleRootRouter} fast path) against N routes with different level thresholds
 * (the {@code CompositeLogRouter} fan-out path every appender-call-site pattern like
 * {@code RainbowGumSystemLogger}/the changeable Tomcat and JCL logs actually uses).
 * <p>
 * Run with: {@code mvn -pl benchmark/rainbowgum-benchmark-jmh-router -am package} then
 * {@code java --add-opens java.base/java.util=ALL-UNNAMED -cp
 * "benchmark/rainbowgum-benchmark-jmh-router/target/classes:$(find ~/.m2 -name
 * 'jmh-core-*.jar' -o -name 'jopt-simple-*.jar' -o -name 'commons-math3-*.jar' | tr '\n'
 * ':')core/target/classes" org.openjdk.jmh.Main}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CompositeRouterBenchmark {

	private static final String LOGGER_NAME = "io.jstach.rainbowgum.benchmark.jmh.Component";

	private RainbowGum singleRouteGum;

	private RainbowGum compositeRouteGum;

	private LogEvent event;

	@Setup(org.openjdk.jmh.annotations.Level.Trial)
	public void setup() {
		this.event = LogEvent.of(Instant.now(), "main", Thread.currentThread().threadId(), Level.INFO, LOGGER_NAME,
				"benchmark message {}", KeyValues.of(), null);

		var singleConfig = LogConfig.builder().build();
		this.singleRouteGum = RainbowGum.builder(singleConfig)
			.route(r -> r.level(Level.INFO).appender("noop", a -> a.output(NoopOutput.INSTANCE)))
			.build()
			.start();

		var compositeConfig = LogConfig.builder().build();
		var builder = RainbowGum.builder(compositeConfig);
		/*
		 * Four routes with different thresholds - INFO events pass two of them and get
		 * rejected by two, exercising CompositeLogRouter's real per-child branch instead
		 * of an all-pass or all-fail degenerate case.
		 */
		builder.route("route-trace", r -> r.level(Level.TRACE).appender("noop1", a -> a.output(NoopOutput.INSTANCE)));
		builder.route("route-info", r -> r.level(Level.INFO).appender("noop2", a -> a.output(NoopOutput.INSTANCE)));
		builder.route("route-warn", r -> r.level(Level.WARNING).appender("noop3", a -> a.output(NoopOutput.INSTANCE)));
		builder.route("route-error", r -> r.level(Level.ERROR).appender("noop4", a -> a.output(NoopOutput.INSTANCE)));
		this.compositeRouteGum = builder.build().start();
	}

	@TearDown(org.openjdk.jmh.annotations.Level.Trial)
	public void tearDown() {
		singleRouteGum.close();
		compositeRouteGum.close();
	}

	@Benchmark
	public void singleRoute(Blackhole bh) {
		var route = singleRouteGum.router().route(LOGGER_NAME, Level.INFO);
		if (route.isEnabled()) {
			route.log(event);
		}
		bh.consume(route);
	}

	@Benchmark
	public void compositeRoute(Blackhole bh) {
		var route = compositeRouteGum.router().route(LOGGER_NAME, Level.INFO);
		if (route.isEnabled()) {
			route.log(event);
		}
		bh.consume(route);
	}

	enum NoopOutput implements LogOutput {

		INSTANCE;

		@Override
		public URI uri() {
			return URI.create("noop:///");
		}

		@Override
		public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
		}

		@Override
		public void flush() {
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
