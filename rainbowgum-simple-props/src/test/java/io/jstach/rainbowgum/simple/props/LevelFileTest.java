package io.jstach.rainbowgum.simple.props;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.System.Logger.Level;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty.ValidationException;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.ServiceRegistry;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.simple.props.LevelFileProperties.Poll;

/*
 * Sets a real system property, so it must not run alongside other tests.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class LevelFileTest {

	private static SimpleProperties simple(String resource, Path file) {
		return SimpleProperties.builder()
			.resource(resource)
			.envLookup(k -> null)
			.levelFilePath(file, Duration.ofMillis(20))
			.build();
	}

	private static LogConfig config(SimpleProperties simple) {
		var registry = ServiceRegistry.of();
		registry.putIfAbsent(SimpleProperties.class, () -> simple);
		var config = LogConfig.builder()
			.serviceRegistry(registry)
			.properties(LogProperties.of(simple.properties()))
			.configurator(new SimplePropertiesProvider())
			.build();
		config.outputRegistry().register("list", ref -> LogProvider.of(new ListLogOutput()));
		return config;
	}

	private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

	/*
	 * Writes the file only its owner can change, as the level file must be, whatever the
	 * umask.
	 */
	private static void write(Path file, String content) throws IOException {
		Files.writeString(file, content);
		chmod(file, "rw-------");
	}

	private static void chmod(Path path, String permissions) throws IOException {
		if (POSIX) {
			Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
		}
	}

	private static void await(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}
	}

	private static List<String> alerts(LogConfig config, Level level) {
		return config.alerts()
			.dump()
			.stream()
			.filter(e -> e.level() == level && e.loggerName().equals(SimpleProperties.class.getName()))
			.map(e -> e.message())
			.toList();
	}

	@Test
	void fileLevelsWinOverEverySourceAndChangesApply(@TempDir Path dir) throws Exception {
		var file = dir.resolve("level.properties");
		write(file, "logging.level.com.example=DEBUG\n");
		System.setProperty("logging.level.com.example", "ERROR");
		try (var gum = RainbowGum.builder(config(simple("classpath:/level-file.properties", file))).build().start()) {
			var config = gum.config();
			assertEquals(Level.DEBUG, config.levelResolver().resolveLevel("com.example.App"));
			write(file, "logging.level.com.example=TRACE\n");
			await(() -> config.levelResolver().resolveLevel("com.example.App") == Level.TRACE);
			assertEquals(Level.TRACE, config.levelResolver().resolveLevel("com.example.App"));
			assertEquals(List.of("Level file changed: " + file.toAbsolutePath()),
					alerts(config, Level.INFO).stream().filter(m -> m.startsWith("Level file changed")).toList());
		}
		finally {
			System.clearProperty("logging.level.com.example");
		}
	}

	@Test
	void aMissingFileIsPickedUpWhenItAppears(@TempDir Path dir) throws Exception {
		var file = dir.resolve("level.properties");
		try (var gum = RainbowGum.builder(config(simple("classpath:/level-file.properties", file))).build().start()) {
			var config = gum.config();
			assertEquals(Level.WARNING, config.levelResolver().resolveLevel("com.example.App"));
			write(file, "logging.level.com.example=DEBUG\n");
			await(() -> config.levelResolver().resolveLevel("com.example.App") == Level.DEBUG);
			assertEquals(Level.DEBUG, config.levelResolver().resolveLevel("com.example.App"));
		}
	}

	@Test
	void rewritingTheSameLevelsIsNotAChange(@TempDir Path dir) throws Exception {
		var file = dir.resolve("level.properties");
		write(file, "logging.level.com.example=DEBUG\n");
		var levels = new LevelFileProperties(file, Duration.ofSeconds(1));
		write(file, "# a comment\nlogging.level.com.example = DEBUG\nlogging.other=x\n");
		assertEquals(Poll.UNCHANGED, levels.poll());
		write(file, "logging.level.com.example=TRACE\n");
		assertEquals(Poll.CHANGED, levels.poll());
		assertEquals(Poll.UNCHANGED, levels.poll());
	}

	@Test
	void keysMayLeaveOffTheLoggingPrefix(@TempDir Path dir) throws Exception {
		var file = dir.resolve("level.properties");
		write(file, "level=ERROR\nlevel.com.example=DEBUG\n");
		try (var gum = RainbowGum.builder(config(simple("classpath:/level-file.properties", file))).build().start()) {
			var config = gum.config();
			assertEquals(Level.ERROR, config.levelResolver().resolveLevel("org.other.App"));
			assertEquals(Level.DEBUG, config.levelResolver().resolveLevel("com.example.App"));
		}
	}

	@Test
	void aShortKeyIsTheSameLevelAsItsFullKey(@TempDir Path dir) throws Exception {
		var file = dir.resolve("level.properties");
		write(file, "logging.level.com.example=DEBUG\n");
		var levels = new LevelFileProperties(file, Duration.ofSeconds(1));
		write(file, "level.com.example=DEBUG\n");
		assertEquals(Poll.UNCHANGED, levels.poll());
	}

	@Test
	void shortKeysOtherThanLevelsAreIgnoredWithAWarning(@TempDir Path dir) throws Exception {
		var file = dir.resolve("level.properties");
		write(file, "level.com.example=DEBUG\nappenders=other\n");
		try (var gum = RainbowGum.builder(config(simple("classpath:/level-file.properties", file))).build().start()) {
			var config = gum.config();
			assertEquals(
					List.of("Ignoring property key 'logging.appenders' from SIMPLE_PROPS[" + file.toAbsolutePath()
							+ ":2][appenders]: the level file only sets logging.level keys."),
					alerts(config, Level.WARNING));
		}
	}

	@Test
	void keysOtherThanLevelsAreIgnoredWithAWarning(@TempDir Path dir) throws Exception {
		var file = dir.resolve("level.properties");
		write(file, "logging.level.com.example=DEBUG\nlogging.appenders=other\n");
		try (var gum = RainbowGum.builder(config(simple("classpath:/level-file.properties", file))).build().start()) {
			var config = gum.config();
			assertEquals(
					List.of("Ignoring property key 'logging.appenders' from SIMPLE_PROPS[" + file.toAbsolutePath()
							+ ":2][logging.appenders]: the level file only sets logging.level keys."),
					alerts(config, Level.WARNING));
			assertEquals(List.of("list"), config.properties().listOrNull("logging.appenders"));
		}
	}

	@Test
	void watchingTurnsOnLevelChanges(@TempDir Path dir) throws Exception {
		var simple = simple("classpath:/level-file.properties", dir.resolve("level.properties"));
		assertEquals("true", LogProperties.of(simple.properties()).valueOrNull(LogProperties.GLOBAL_CHANGE_PROPERTY));
	}

	@Test
	void offByDefault(@TempDir Path dir) {
		var simple = simple("classpath:/logging.properties", dir.resolve("level.properties"));
		assertNull(simple.levelFile());
		assertNull(LogProperties.of(simple.properties()).valueOrNull(LogProperties.GLOBAL_CHANGE_PROPERTY));
	}

	@Test
	void invalidLevelFileSettingFails(@TempDir Path dir) {
		var e = assertThrows(ValidationException.class,
				() -> simple("classpath:/level-file-invalid.properties", dir.resolve("level.properties")));
		String expected = """
				Validation failed for io.jstach.rainbowgum.simple.props.SimpleProperties:
				Error for property. key: 'logging.simpleprops.levelFile' from SIMPLE_PROPS[classpath:/level-file-invalid.properties:1][logging.simpleprops.levelFile], \
				'loud' is not a valid value for io.jstach.rainbowgum.simple.props.SimpleProperties.LevelFileType. \
				Valid values: 'off', 'watch', 'true', 'false'
				Tried:
				    'logging.simpleprops.levelFile' from:
				        SYSTEM_PROPERTIES[logging.simpleprops.levelFile],
				        ENV[RAINBOWGUM_simpleprops_levelFile],
				        SIMPLE_PROPS[classpath:/level-file-invalid.properties:1][logging.simpleprops.levelFile]""";
		assertEquals(expected, e.getMessage());
	}

	@Test
	@SuppressWarnings("clear.system.property") // only clears the property this test set
	void systemPropertyTurnsWatchingOnOverTheFile(@TempDir Path dir) {
		System.setProperty(SimpleProperties.LEVEL_FILE_PROPERTY, "watch");
		try {
			var simple = simple("classpath:/logging.properties", dir.resolve("level.properties"));
			assertTrue(simple.levelFile() != null);
		}
		finally {
			System.clearProperty(SimpleProperties.LEVEL_FILE_PROPERTY);
		}
	}

	@Test
	@SuppressWarnings("clear.system.property") // only clears the property this test set
	void levelFilePathCanBeSet(@TempDir Path dir) throws Exception {
		var file = dir.resolve("custom-levels.properties");
		write(file, "level.com.example=DEBUG\n");
		System.setProperty(SimpleProperties.LEVEL_FILE_PATH_PROPERTY, file.toString());
		try (var gum = RainbowGum.builder(config(simple("classpath:/level-file.properties", dir.resolve("unused"))))
			.build()
			.start()) {
			assertEquals(Level.DEBUG, gum.config().levelResolver().resolveLevel("com.example.App"));
		}
		finally {
			System.clearProperty(SimpleProperties.LEVEL_FILE_PATH_PROPERTY);
		}
	}

	@Test
	@SuppressWarnings("clear.system.property") // only clears the property this test set
	void aRelativeLevelFilePathIsInTheWorkingDirectory(@TempDir Path dir) {
		System.setProperty(SimpleProperties.LEVEL_FILE_PATH_PROPERTY, "../config/level.properties");
		try {
			var levelFile = simple("classpath:/level-file.properties", dir.resolve("unused")).levelFile();
			assertEquals(Path.of("../config/level.properties").toAbsolutePath(),
					levelFile == null ? null : levelFile.path());
		}
		finally {
			System.clearProperty(SimpleProperties.LEVEL_FILE_PATH_PROPERTY);
		}
	}

	@Test
	void aFileOthersCanWriteIsRefusedUntilFixed(@TempDir Path dir) throws Exception {
		assumeTrue(POSIX);
		var file = dir.resolve("level.properties");
		write(file, "level.com.example=DEBUG\n");
		chmod(file, "rw-rw-r--");
		try (var gum = RainbowGum.builder(config(simple("classpath:/level-file.properties", file))).build().start()) {
			var config = gum.config();
			assertEquals(Level.WARNING, config.levelResolver().resolveLevel("com.example.App"));
			assertEquals(
					List.of("Refusing level file " + file.toAbsolutePath()
							+ ": it is writable by group or others. Remove that access, for example with chmod go-w."),
					alerts(config, Level.ERROR));
			chmod(file, "rw-------");
			await(() -> config.levelResolver().resolveLevel("com.example.App") == Level.DEBUG);
			assertEquals(Level.DEBUG, config.levelResolver().resolveLevel("com.example.App"));
		}
	}

	@Test
	void aDirectoryOthersCanWriteIsRefused(@TempDir Path dir) throws Exception {
		assumeTrue(POSIX);
		var shared = Files.createDirectory(dir.resolve("shared"));
		var file = shared.resolve("level.properties");
		write(file, "level.com.example=DEBUG\n");
		chmod(shared, "rwxrwxrwx");
		try (var gum = RainbowGum.builder(config(simple("classpath:/level-file.properties", file))).build().start()) {
			var config = gum.config();
			assertEquals(Level.WARNING, config.levelResolver().resolveLevel("com.example.App"));
			assertEquals(
					List.of("Refusing level file " + file.toAbsolutePath() + ": its directory "
							+ shared.toAbsolutePath()
							+ " is writable by group or others. Remove that access, for example with chmod go-w."),
					alerts(config, Level.ERROR));
		}
	}

	@Test
	void aFileThatBecomesWritableByOthersKeepsItsLastLevels(@TempDir Path dir) throws Exception {
		assumeTrue(POSIX);
		var file = dir.resolve("level.properties");
		write(file, "level.com.example=DEBUG\n");
		var levels = new LevelFileProperties(file, Duration.ofSeconds(1));
		Files.writeString(file, "level.com.example=TRACE\n");
		chmod(file, "rw-rw-rw-");
		assertEquals(Poll.REFUSED, levels.poll());
		assertEquals(Poll.UNCHANGED, levels.poll());
		assertEquals("DEBUG", levels.valueOrNull("logging.level.com.example"));
	}

}
