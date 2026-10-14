package io.jstach.rainbowgum.log4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.message.MapMessage;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogConfig.Globals.KeyValuesJson;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.format.Json5FormatterBuilder;
import io.jstach.rainbowgum.format.KeyValuesFormatterBuilder;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * Log4j2 has no key values on its fluent LogBuilder: structured data comes as a
 * MapMessage, whose entries become key values.
 */
class MapMessageKeyValuesTest {

	enum MessageCase {

		MAP_MESSAGE_ENTRY {
			@Override
			MapMessage<?, ?> message() {
				return new MapMessage<>().with("message", "order shipped")
					.with("attempt", 3)
					.with("order", Map.of("id", 7));
			}
		},
		STRING_MAP_MESSAGE {
			@Override
			MapMessage<?, ?> message() {
				return new StringMapMessage().with("message", "order shipped").with("attempt", "3");
			}
		},
		STRUCTURED_DATA_MESSAGE {
			@Override
			MapMessage<?, ?> message() {
				return new StructuredDataMessage("id", "order shipped", "type").with("attempt", 3)
					.with("order", List.of(7));
			}
		};

		abstract MapMessage<?, ?> message();

	}

	@ParameterizedTest
	@EnumSource(KeyValuesJson.class)
	void mapMessageEntriesAreKeyValues(KeyValuesJson mode) {
		for (MessageCase test : MessageCase.values()) {
			assertEquals(expected(test, mode), log(test, mode), test + " " + mode);
		}
	}

	private static String expected(MessageCase test, KeyValuesJson mode) {
		return switch (test) {
			case MAP_MESSAGE_ENTRY -> switch (mode) {
				case ON -> "\"msg\":\"order shipped\",\"attempt\":3,\"order\":{\"id\":7}}\n";
				case OFF -> "\"msg\":\"order shipped\",\"attempt\":\"3\",\"order\":\"{id=7}\"}\n";
			};
			case STRING_MAP_MESSAGE -> "\"msg\":\"order shipped\",\"attempt\":\"3\"}\n";
			case STRUCTURED_DATA_MESSAGE -> switch (mode) {
				case ON -> "\"msg\":\"order shipped\",\"attempt\":3,\"order\":[7]}\n";
				case OFF -> "\"msg\":\"order shipped\",\"attempt\":\"3\",\"order\":\"[7]\"}\n";
			};
		};
	}

	private static String log(MessageCase test, KeyValuesJson mode) {
		var properties = LogProperties.builder()
			.fromProperties("logging.keyvalues.json=" + mode.name().toLowerCase(Locale.ROOT))
			.build();
		var config = LogConfig.builder().properties(properties).build();
		var output = new ListLogOutput();
		var encoder = LogEncoder
			.of(new Json5FormatterBuilder("t").format(KeyValuesFormatterBuilder.Format.JSON).build());
		try (var gum = RainbowGum.builder(config)
			.route(r -> r.appender("list", a -> a.output(output).encoder(encoder)))
			.set()) {
			LogManager.getLogger("app.map").info(test.message());
		}
		String line = output.events().get(0).getValue();
		return line.substring(line.indexOf("\"msg\":"));
	}

}
