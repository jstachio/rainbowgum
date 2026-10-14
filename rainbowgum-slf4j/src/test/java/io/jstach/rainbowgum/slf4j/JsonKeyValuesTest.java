package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogConfig.Globals.KeyValuesJson;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.format.Json5FormatterBuilder;
import io.jstach.rainbowgum.format.KeyValuesFormatterBuilder;
import io.jstach.rainbowgum.format.TTLLFormatterBuilder;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * Object key values from the fluent API through the real SLF4J binding: with
 * logging.keyvalues.json on, JSON encoders write them as JSON and plain text encoders are
 * unchanged.
 */
class JsonKeyValuesTest {

	@ParameterizedTest
	@EnumSource(KeyValuesJson.class)
	void objectKeyValuesFollowTheProperty(KeyValuesJson mode) {
		var properties = LogProperties.builder()
			.fromProperties("logging.keyvalues.json=" + mode.name().toLowerCase(java.util.Locale.ROOT))
			.build();
		var config = LogConfig.builder().properties(properties).serviceLoader().build();
		var json = new ListLogOutput();
		var ttll = new ListLogOutput();
		var jsonEncoder = LogEncoder
			.of(new Json5FormatterBuilder("t").format(KeyValuesFormatterBuilder.Format.JSON).build());
		var ttllEncoder = LogEncoder.of(new TTLLFormatterBuilder("t").build());
		try (var gum = RainbowGum.builder(config)
			.route(r -> r.appender("json", a -> a.output(json).encoder(jsonEncoder))
				.appender("ttll", a -> a.output(ttll).encoder(ttllEncoder)))
			.set()) {
			var user = new LinkedHashMap<String, Object>();
			user.put("id", 1);
			user.put("roles", List.of("admin"));
			var logger = org.slf4j.LoggerFactory.getLogger("app." + mode);
			logger.atInfo()
				.addKeyValue("requestId", "42")
				.addKeyValue("attempt", 3)
				.addKeyValue("user", user)
				.addKeyValue("missing", (Object) null)
				.log("hello");
		}
		String keyValues = switch (mode) {
			case ON -> "\"requestId\":\"42\",\"attempt\":3,\"user\":{\"id\":1,\"roles\":[\"admin\"]},\"missing\":null";
			case OFF -> "\"requestId\":\"42\",\"attempt\":\"3\",\"user\":\"{id=1, roles=[admin]}\",\"missing\":null";
		};
		String line = json.events().get(0).getValue();
		assertEquals(keyValues + "}\n", line.substring(line.indexOf("\"msg\":\"hello\",") + 14));
		String ttllLine = ttll.events().get(0).getValue();
		assertEquals(
				"app." + mode
						+ " {requestId=42&attempt=3&user=%7Bid%3D1%2C%20roles%3D%5Badmin%5D%7D&missing} - hello\n",
				ttllLine.substring(ttllLine.indexOf("app.")));
	}

}
