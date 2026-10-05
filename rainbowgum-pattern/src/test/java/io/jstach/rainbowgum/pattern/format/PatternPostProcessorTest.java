package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * Post processors are applied by core to every formatter backed encoder, so the pattern
 * encoder needs no setting of its own for them.
 */
class PatternPostProcessorTest {

	@Test
	void patternEncoderHonorsPostProcessors() {
		var output = new ListLogOutput();
		String properties = """
				logging.appenders=list
				logging.appender.list.output=list
				logging.appender.list.encoder=pattern
				logging.encoder.list.pattern=%level %msg%n
				logging.encoder.list.postProcessors=journald
				""";
		var config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new PatternConfigurator())
			.build();
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(LogEventFactory.of("test")
				.eventNoArg(Level.ERROR, "first\nsecond", KeyValues.of(), (Throwable) null));
		}
		assertEquals("<3>ERROR first\n<3>second\n", output.toString());
	}

}
