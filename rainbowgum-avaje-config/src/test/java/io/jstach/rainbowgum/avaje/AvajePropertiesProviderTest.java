package io.jstach.rainbowgum.avaje;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import io.avaje.config.Configuration;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.RainbowGum;

class AvajePropertiesProviderTest {

	@Test
	void testBadProperty() {
		Supplier<Configuration> supplier = () -> Configuration.builder()
			.resourceLoader(ClassLoader::getSystemResourceAsStream)
			.load("bad-prop.properties")
			.build();

		AvajePropertiesProvider pp = new AvajePropertiesProvider(supplier);
		LogConfig config = LogConfig.builder().propertiesProvider(pp).configurator(pp).build();
		RainbowGum.set(() -> RainbowGum.builder(config).build());
		var e = assertThrows(LogProperty.PropertyConvertException.class, () -> {
			try (var gum = RainbowGum.of()) {

			}
		});
		String expected = """
				Error for property. key: 'logging.appender.stuff.output' from AVAJE(initial)[logging.appender.stuff.output], java.lang.RuntimeException Output type for URI: 'blah:///' not found.
				Tried: 'logging.appender.stuff.output' from AVAJE(initial)[logging.appender.stuff.output]""";
		String actual = e.getMessage();
		assertEquals(expected, actual);
	}

}
