package io.jstach.rainbowgum.kitchensink;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.LogAppender.AppenderType;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.LogReporter;
import io.jstach.rainbowgum.LogReporter.Section;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.RainbowGumVersion;
import io.jstach.rainbowgum.file.FileOutputBuilder;
import io.jstach.rainbowgum.jfr.JfrLogOutput;
import io.jstach.rainbowgum.json.encoder.EcsEncoderBuilder;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.pattern.format.PatternEncoderBuilder;

/**
 * Pulls in as many separately optional modules as reasonably possible in one RainbowGum,
 * then golden strings {@link LogReporter}'s output against it: a properties provider
 * (rainbowgum-avaje-config), a pattern encoder (rainbowgum-pattern), a JSON encoder
 * (rainbowgum-json), a real file output (rainbowgum-file), a JFR output (rainbowgum-jfr,
 * self-providing its own encoder), and an async publisher that resolves to
 * rainbowgum-disruptor's DisruptorLogPublisher rather than the default
 * BlockingQueueAsyncLogPublisher, since rainbowgum-disruptor registers itself as the
 * default "async" scheme handler.
 * <p>
 * Deliberately plain classpath (see this module's own pom.xml): the point is testing what
 * a real application pulling in this many optional modules at once actually looks like,
 * without any of the module-path friction that came up trying to do the same thing inside
 * the (modular) rainbowgum aggregator module.
 */
class LogReporterKitchenSinkTest {

	private static final String DIAGNOSTICS_LOGGER_NAME = "io.jstach.rainbowgum.kitchensink.diagnostics";

	@Test
	void testKitchenSink(@TempDir Path tempDir) {
		/*
		 * AvajePropertiesProvider's own Supplier-taking constructor is package private
		 * (test-only, see AvajePropertiesProviderTest), so this relies on the same
		 * ServiceLoader discovery a real application would: its public no-arg constructor
		 * delegates to avaje-config's own default Config.asConfiguration(), which loads
		 * src/test/resources/application.properties by avaje-config's own convention.
		 */
		LogConfig config = LogConfig.builder().serviceLoader().build();

		Path logFile = tempDir.resolve("app.log");
		var listOutput = new ListLogOutput();
		var diagnosticsOutput = new ListLogOutput();

		try (var gum = RainbowGum.builder(config).route("console", r -> {
			r.level(Level.INFO);
			r.appender("console", a -> {
				a.output(LogOutput.ofStandardOut());
				a.encoder(new PatternEncoderBuilder("console").pattern("[%thread] %-5level %logger{36} - %msg%n")
					.fromProperties(config.properties())
					.build());
			});
		}).route("structured", r -> {
			// Resolves to DisruptorLogPublisher, not BlockingQueueAsyncLogPublisher:
			// rainbowgum-disruptor registers itself as the default "async" scheme
			// handler (see DisruptorConfigurator), and this module has it on the
			// classpath with LogConfig.builder().serviceLoader() enabled above.
			r.publisher(PublisherFactory.async().build());
			r.appender("json", a -> {
				a.output(listOutput);
				a.encoder(new EcsEncoderBuilder("json").serviceName("kitchensink").build());
			});
		}).route("file", r -> {
			r.appender("file", a -> {
				a.output(new FileOutputBuilder("file").fileName(logFile.toString()).build());
				a.encoder(
						new PatternEncoderBuilder("file").pattern("%d{ISO8601} [%thread] %-5level %logger{50} - %msg%n")
							.fromProperties(config.properties())
							.build());
				a.appenderType(AppenderType.REUSE_BUFFER);
			});
		}).route("jfr", r -> {
			r.appender("jfr", a -> {
				// JfrLogOutput implements LogOutput.ProvidesEncoder: it supplies its
				// own encoder, so none is set here.
				a.output(new JfrLogOutput());
			});
		}).route("diagnostics", r -> {
			/*
			 * Demonstrates METRICS/ALERTS actually having content in a real report: a
			 * small encoder buffer that has to grow for a large message then gets trimmed
			 * back down (BUFFER_TRIMMED_METRIC), a custom output that triggers a
			 * genuinely reentrant log call which REENTRY_DROP below drops and counts
			 * rather than recursing forever (EVENTS_DROPPED_METRIC), and an output that
			 * fails outright (an alert, plus EVENTS_FAILED_METRIC). Scoped to DEBUG for
			 * this one logger name specifically, not a blanket route level: some of the
			 * other optional modules pulled into this test do their own DEBUG level
			 * self-logging, which a blanket level would also route here (and count
			 * towards the very metrics this route exists to demonstrate), rather than
			 * falling through to the other routes' default (INFO) the way it should.
			 */
			r.level(Level.DEBUG, DIAGNOSTICS_LOGGER_NAME);
			r.appender("diagnostics", a -> {
				a.output(diagnosticsOutput);
				a.encoder(LogFormatter.builder().message().encoder().initialBufferSize(16).maxBufferSize(64).build());
				a.flag(AppenderFlag.REENTRY_DROP);
			});
		}).set()) {

			Logger diagnosticsLogger = LoggerFactory.getLogger(DIAGNOSTICS_LOGGER_NAME);
			diagnosticsOutput.setConsumer((e, s) -> {
				if (e.message().equals("trigger")) {
					diagnosticsLogger.debug("nested");
				}
				else if (e.message().equals("boom")) {
					throw new RuntimeException("boom");
				}
			});
			diagnosticsLogger.debug("X".repeat(5000));
			diagnosticsLogger.debug("trigger");
			diagnosticsLogger.debug("boom");

			var reporter = LogReporter.builder()
				.sections(EnumSet.of(Section.VERSION, Section.COMPONENTS, Section.METRICS, Section.ALERTS))
				/*
				 * The one alert recorded below carries a real Instant.now() timestamp:
				 * genuinely non-deterministic, so this swaps in a formatter that omits it
				 * entirely instead of pattern-matching it back out of the rendered report
				 * after the fact.
				 */
				.alertFormatter(LogFormatter.builder().level().text(" ").loggerName().text(" - ").message().build())
				.build();
			String actual = reporter.report(gum);
			System.out.println(actual);

			String expected = """
					Rainbow Gum VERSION_PLACEHOLDER

					Global Properties:
					  logging.global.change = (unset)
					  logging.global.queue.level = (unset)
					  logging.global.queue.error = (unset)
					  logging.global.ansi.disable = (unset)
					  logging.global.appender.reentrantLock = (unset)
					  logging.global.threadlocalDisabled = (unset)
					  logging.global.optimize = true

					Router: structured
					  Publisher: DisruptorLogPublisher (asynchronous)
					    Appender: json
					      Type: LockThreadLocalBufferLogAppender
					      Flags: []
					      Output: ListLogOutput (type=MEMORY)
					      Encoder: EcsEncoder
					Router: console
					  Publisher: DefaultSyncLogPublisher (synchronous)
					    Appender: console
					      Type: LockThreadLocalBufferLogAppender
					      Flags: []
					      Output: StdOutOutput (type=CONSOLE_OUT, uri=stdout:///)
					      Encoder: FormatterEncoder (contentType=text/plain; charset=UTF-8, description="[%thread] %-5level %logger{36} - %msg%n")
					Router: file
					  Publisher: DefaultSyncLogPublisher (synchronous)
					    Appender: file
					      Type: ReuseBufferLogAppender
					      Flags: []
					      Output: ReopenableFileOutput (type=FILE, uri=FILE_URI_PLACEHOLDER)
					      Encoder: FormatterEncoder (contentType=text/plain; charset=UTF-8, description="%d{ISO8601} [%thread] %-5level %logger{50} - %msg%n")
					Router: jfr
					  Publisher: DefaultSyncLogPublisher (synchronous)
					    Appender: jfr
					      Type: LockThreadLocalBufferLogAppender
					      Flags: []
					      Output: JfrLogOutput (type=MEMORY, uri=jfr:///)
					      Encoder: FormatterEncoder (contentType=text/plain; charset=UTF-8)
					Router: diagnostics
					  Publisher: DefaultSyncLogPublisher (synchronous)
					    Appender: diagnostics
					      Type: LockThreadLocalBufferLogAppender
					      Flags: [REENTRY_DROP]
					      Output: ListLogOutput (type=MEMORY)
					      Encoder: FormatterEncoder (contentType=text/plain; charset=UTF-8)
					Metrics:
					  events.failed = 1 (ERROR)
					  io.jstach.rainbowgum.LockThreadLocalBufferLogAppender = 1 (ERROR)
					  events.dropped = 1 (ERROR)
					  buffer.trimmed = 1 (WARNING)

					Alerts (total=1, capacity=128):
					  ERROR io.jstach.rainbowgum.LockThreadLocalBufferLogAppender - appender 'diagnostics' failed to append event
					"""
				.replace("FILE_URI_PLACEHOLDER", "file:" + logFile)
				.replace("VERSION_PLACEHOLDER", RainbowGumVersion.VERSION);
			assertEquals(expected, actual);
		}
	}

}
