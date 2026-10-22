package io.jstach.rainbowgum.test.nested;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.PollingStrategies;
import io.jstach.rainbowgum.LogProperties;

class HelidonLogPropertiesTest extends AbstractNestedLogPropertiesTest {

	@Override
	String format() {
		return "Helidon";
	}

	static LogProperties yaml(String yaml) {
		return HelidonLogProperties.of(Config.just(yaml, MediaTypes.APPLICATION_X_YAML));
	}

	@Override
	LogProperties levels() {
		return yaml("""
				logging:
				  level:
				    root: warn
				    "com.example": debug
				    "com.example.sub": error
				    "tc~1floci/floci-gcp@sha256:405c128b~1~1~1": trace
				    org.dotted.Unquoted: info
				""");
	}

	@Override
	LogProperties rootLevelOnly() {
		return yaml("""
				logging:
				  level: error
				""");
	}

	@Override
	LogProperties changes() {
		return yaml("""
				logging:
				  change:
				    mode: "on"
				    logger:
				      root: level
				      "com.example": "false"
				  caller:
				    logger:
				      "com.example": "true"
				""");
	}

	@Override
	LogProperties groups() {
		return yaml("""
				logging:
				  groups: [jdbc]
				  global:
				    ansi:
				      mode: "off"
				  group:
				    jdbc: [org.postgresql, com.example.odd_name, root]
				  level:
				    root: info
				    jdbc: debug
				""");
	}

	@Override
	LogProperties maps() {
		return yaml("""
				logging:
				  appender:
				    console:
				      encoder:
				        gelf:
				          headers:
				            environment: prod
				            region: us east
				    file:
				      encoder:
				        gelf:
				          headers:
				            keys: [environment]
				            environment: dev
				""");
	}

	@Override
	LogProperties typo() {
		return yaml("""
				logging:
				  global:
				    ansi:
				      mod: "on"
				  level:
				    root: warn
				    "com.example": debug
				""");
	}

	/*
	 * Unlike TOML and HOCON, a Helidon node can have a value and members, so flat keys
	 * work too.
	 */
	@Test
	void prefixCanHaveAValueAndLoggers() {
		var properties = HelidonLogProperties.of(Config
			.just(ConfigSources.create(Map.of("logging.level", "warn", "logging.level.com.example", "debug"))));
		var levels = config(properties).levelResolver();
		assertEquals(Level.WARNING, levels.resolveLevel("other"));
		assertEquals(Level.DEBUG, levels.resolveLevel("com.example.Foo"));
	}

	@Test
	void changedFileIsReadWithoutRebuildingTheProperties(@TempDir Path dir) throws Exception {
		var file = dir.resolve("logging.yaml");
		String yaml = """
				logging:
				  change:
				    mode: "on"
				  level:
				    root: warn
				    "com.example": debug
				""";
		Files.writeString(file, yaml);
		var helidon = Config
			.just(ConfigSources.file(file).pollingStrategy(PollingStrategies.regular(Duration.ofMillis(50))));
		var config = config(HelidonLogProperties.of(helidon));
		var levels = config.levelResolver();
		assertEquals(Level.DEBUG, levels.resolveLevel("com.example.Foo"));
		assertEquals(Level.WARNING, levels.resolveLevel("com.other"));

		var published = new CountDownLatch(1);
		helidon.onChange(c -> {
			config.changePublisher().publish();
			published.countDown();
		});
		Files.writeString(file, yaml.replace("debug", "error") + """
				    "com.other": trace
				""");
		assertTrue(published.await(10, TimeUnit.SECONDS));

		assertEquals(Level.ERROR, levels.resolveLevel("com.example.Foo"));
		assertEquals(Level.TRACE, levels.resolveLevel("com.other"));
		// Why every lookup starts from the latest version.
		assertEquals("debug", helidon.get("logging.level.com.example").asString().get());
	}

}
