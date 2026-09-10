package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.slf4j.spi.AbstractFilteringLogger;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAwareLogger;

class AbstractFilteringLoggerTest {

	ListLogOutput list = new ListLogOutput();

	@Test
	void callerIsCorrectAcrossArities() {
		Logger logger = newLogger(PassThroughLogger::new);
		logger.info("plain");
		logger.info("one arg {}", "a");
		logger.info("two args {} {}", "a", "b");
		logger.info("varargs {} {} {}", "a", "b", "c");
		logger.info("with throwable", new RuntimeException());
		Marker m = MarkerFactory.getMarker("M");
		logger.info(m, "marker plain");
		logger.info(m, "marker one arg {}", "a");
		logger.info(m, "marker two args {} {}", "a", "b");
		logger.info(m, "marker varargs {} {} {}", "a", "b", "c");
		logger.info(m, "marker with throwable", new RuntimeException());

		String caller = "io.jstach.rainbowgum.slf4j.AbstractFilteringLoggerTest.callerIsCorrectAcrossArities";
		String[] lines = list.toString().split("\n");
		assertEquals(10, lines.length);
		for (String line : lines) {
			assertEquals(caller, line.substring(line.indexOf("<caller>") + 8, line.indexOf("</caller>")));
		}
	}

	@Test
	void isEnabledFalseDropsEventCheaply() {
		Logger logger = newLogger(d -> new MinLevelLogger(d, Level.WARN));
		logger.info("should not appear");
		logger.warn("should appear");
		assertEquals(
				"""
						WARNING should appear <caller>io.jstach.rainbowgum.slf4j.AbstractFilteringLoggerTest.isEnabledFalseDropsEventCheaply</caller>
						""",
				list.toString());
	}

	@Test
	void markerIsPassedToDecorate() {
		Logger logger = newLogger(MarkerCapturingLogger::new);
		logger.info("no marker");
		logger.info(MarkerFactory.getMarker("M"), "with marker");
		String[] lines = list.toString().split("\n");
		assertEquals(2, lines.length);
		assertTrue(!lines[0].contains("_marker="), lines[0]);
		assertTrue(lines[1].contains("with marker") && lines[1].contains("_marker=M"), lines[1]);
	}

	@Test
	void selfLogHelperHasCorrectDepth() {
		Logger logger = newLogger(SelfLoggingLogger::new);
		logger.info("self logged");
		String actual = list.toString();
		assertTrue(actual.contains(
				"<caller>io.jstach.rainbowgum.slf4j.AbstractFilteringLoggerTest.selfLogHelperHasCorrectDepth</caller>"),
				actual);
	}

	private Logger newLogger(Function<DepthAwareLogger, AbstractFilteringLogger> factory) {
		LogConfig config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.global.change=true
				logging.change=caller
				""").build()).build();
		RainbowGum gum = gum(config);
		RainbowGumMDCAdapter mdc = new RainbowGumMDCAdapter();
		gum.start();
		var rawFactory = RainbowGumLoggerFactory.of(gum, mdc);
		var previous = (DepthAwareLogger) rawFactory.getLogger("test");
		return factory.apply(previous);
	}

	private RainbowGum gum(LogConfig config) {
		var gum = RainbowGum.builder(config).route(route -> {
			route.appender("list", a -> {
				a.formatter((output, event) -> {
					output.append(event.level()).append(" ");
					event.formattedMessage(output);
					Caller caller = event.callerOrNull();
					if (caller != null) {
						output.append(" <caller>");
						output.append(caller.className());
						output.append(".");
						output.append(caller.methodName());
						output.append("</caller>");
					}
					String marker = event.keyValues().getValueOrNull("_marker");
					if (marker != null) {
						output.append(" _marker=").append(marker);
					}
					output.append("\n");
				});
				a.output(list);
			});
		});
		return gum.build();
	}

	static class PassThroughLogger extends AbstractFilteringLogger {

		PassThroughLogger(DepthAwareLogger delegate) {
			super(delegate);
		}

	}

	static class MinLevelLogger extends AbstractFilteringLogger {

		private final Level minLevel;

		MinLevelLogger(DepthAwareLogger delegate, Level minLevel) {
			super(delegate);
			this.minLevel = minLevel;
		}

		@Override
		protected boolean isEnabled(Level level, @Nullable Marker marker) {
			return level.toInt() >= minLevel.toInt();
		}

	}

	static class SelfLoggingLogger extends AbstractFilteringLogger {

		SelfLoggingLogger(DepthAwareLogger delegate) {
			super(delegate);
		}

		@Override
		protected boolean decorate(LoggingEventBuilder builder, @Nullable Marker marker) {
			log(builder);
			return false;
		}

	}

	static class MarkerCapturingLogger extends AbstractFilteringLogger {

		MarkerCapturingLogger(DepthAwareLogger delegate) {
			super(delegate);
		}

		@Override
		protected boolean decorate(LoggingEventBuilder builder, @Nullable Marker marker) {
			if (marker != null) {
				builder.addKeyValue("_marker", marker.toString());
			}
			return true;
		}

	}

}
