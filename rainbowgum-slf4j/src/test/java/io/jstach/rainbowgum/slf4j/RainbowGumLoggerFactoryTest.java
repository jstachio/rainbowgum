package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogFormatter.KeyValuesFormatter;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class RainbowGumLoggerFactoryTest {

	@Test
	void testGetLogger() {
		var list = new ListLogOutput();
		var gum = RainbowGum.builder().sync(b -> {
			b.appender(LogAppender.builder().formatter((output, event) -> {
				event.formattedMessage(output);
				output.append(" {");
				KeyValuesFormatter.of().format(output, event);
				output.append("}");
				output.append("\n");
			}).output(list).build());
		}).build();
		RainbowGumLoggerFactory factory = new RainbowGumLoggerFactory(gum);
		var logger = factory.getLogger("crap");
		MDC.put("status", "alive");
		logger.info("Eric");
		MDC.put("status", "dead");
		logger.debug("Kenny");
		logger.info("Stan");
		String actual = list.toString();
		String expected = """
				Eric {status=alive}
				Stan {status=dead}
				""";
		assertEquals(expected, actual);
	}

}
