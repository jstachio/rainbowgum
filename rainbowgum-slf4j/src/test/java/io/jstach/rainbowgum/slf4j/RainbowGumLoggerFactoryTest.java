package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.MDC;

import io.jstach.rainbowgum.LogFormatter.KeyValuesFormatter;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class RainbowGumLoggerFactoryTest {

	@Test
	void testGetLogger() {
		var list = new ListLogOutput();
		var gum = RainbowGum.builder().route(route -> {
			route.level(System.Logger.Level.WARNING, "ignore");
			route.level(System.Logger.Level.INFO);
			route.appender(a -> {
				a.formatter((output, event) -> {
					event.formattedMessage(output);
					output.append(" {");
					KeyValuesFormatter.of().format(output, event);
					output.append("}");
					output.append("\n");
				});
				a.output(list);
			});
		});
		var rainbowgum = gum.build();
		var lr = rainbowgum.router().levelResolver();
		System.out.println(lr);

		RainbowGumLoggerFactory factory = new RainbowGumLoggerFactory(rainbowgum);
		Consumer<Logger> consumer = (logger) -> {
			MDC.put("status", "alive");
			logger.info("Eric");
			MDC.put("status", "dead");
			logger.debug("Kenny");
			logger.warn("Stan");
		};
		consumer.accept(factory.getLogger("crap"));
		String actual = list.toString();
		String expected = """
				Eric {status=alive}
				Stan {status=dead}
				""";
		assertEquals(expected, actual);
		list.events().clear();

		consumer.accept(factory.getLogger("ignore"));
		actual = list.toString();
		expected = "Stan {status=dead}\n";
		assertEquals(expected, actual);
		list.events().clear();
	}

}
