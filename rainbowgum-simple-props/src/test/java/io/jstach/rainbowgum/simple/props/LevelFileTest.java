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
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogAlerts.FailLevel;
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

	enum StartupFailure {

		CONFIGURATOR("""
				Later configurator failed"""),
		ALERTS("""
				1 alert(s) at error or above were recorded while building the configuration and logging.alerts.fail=error:
				[ERROR] Later configurator reported an error""");

		final String message;

		StartupFailure(String message) {
			this.message = message;
		}

	}

	@ParameterizedTest
	@EnumSource(StartupFailure.class)
	void aFailedConfigurationBuildStopsItsWatcher(StartupFailure failure, @TempDir Path dir) throws Exception {
		var file = dir.resolve("level.properties");
		write(file, "level.com.example=DEBUG\n");
		var simple = simple("classpath:/level-file.properties", file);
		var started = new ArrayList<Thread>();
		var existingThreads = Thread.getAllStackTraces().keySet();
		try (var registry = ServiceRegistry.of()) {
			registry.putIfAbsent(SimpleProperties.class, () -> simple);
			var e = assertThrows(IllegalStateException.class,
					() -> LogConfig.builder()
						.serviceRegistry(registry)
						.properties(LogProperties.of(simple.properties()))
						.alertsFail(FailLevel.ERROR)
						.configurator(new SimplePropertiesProvider())
						.configurator((config, pass) -> {
							started.add(Thread.getAllStackTraces()
								.keySet()
								.stream()
								.filter(t -> !existingThreads.contains(t) && t.isAlive()
										&& t.getName().equals("rainbowgum-level-file"))
								.findFirst()
								.orElseThrow());
							if (failure == StartupFailure.CONFIGURATOR) {
								throw new IllegalStateException("Later configurator failed");
							}
							config.alerts()
								.error(getClass(), new IllegalStateException("Later configurator reported an error"));
							return true;
						})
						.build());
			assertEquals(failure.message, e.getMessage());
			assertEquals(1, started.size());
			await(() -> !started.get(0).isAlive());
			assertFalse(started.get(0).isAlive());
		}
	}

	@Test
	void aSymbolicLinkToATrustedFileLoadsLevels(@TempDir Path dir) throws Exception {
		assumeTrue(POSIX);
		var targetDir = Files.createDirectory(dir.resolve("trusted"));
		chmod(targetDir, "rwx------");
		var target = targetDir.resolve("level.properties");
		write(target, "level.com.example=DEBUG\n");
		var link = Files.createSymbolicLink(dir.resolve("level.properties"), target);
		try (var gum = RainbowGum.builder(config(simple("classpath:/level-file.properties", link))).build().start()) {
			assertEquals(Level.DEBUG, gum.config().levelResolver().resolveLevel("com.example.App"));
			assertEquals(List.of(), alerts(gum.config(), Level.ERROR));
		}
	}

	@Test
	void aSymbolicLinkIntoAWritableDirectoryIsRefusedUntilFixed(@TempDir Path dir) throws Exception {
		assumeTrue(POSIX);
		var targetDir = Files.createDirectory(dir.resolve("shared"));
		var target = targetDir.resolve("level.properties");
		write(target, "level.com.example=DEBUG\n");
		chmod(targetDir, "rwxrwxrwx");
		var link = Files.createSymbolicLink(dir.resolve("level.properties"), target);
		try (var gum = RainbowGum.builder(config(simple("classpath:/level-file.properties", link))).build().start()) {
			var config = gum.config();
			assertEquals(Level.WARNING, config.levelResolver().resolveLevel("com.example.App"));
			assertEquals(
					List.of("""
							Refusing level file LINK: its directory TARGET_DIR is writable by group or others. Remove that access, for example with chmod go-w."""
						.replace("LINK", link.toAbsolutePath().toString())
						.replace("TARGET_DIR", targetDir.toRealPath().toString())),
					alerts(config, Level.ERROR));
			chmod(targetDir, "rwx------");
			await(() -> config.levelResolver().resolveLevel("com.example.App") == Level.DEBUG);
			assertEquals(Level.DEBUG, config.levelResolver().resolveLevel("com.example.App"));
		}
	}

	@Test
	void aDanglingSymbolicLinkKeepsItsLastLevels(@TempDir Path dir) throws Exception {
		assumeTrue(POSIX);
		var target = dir.resolve("target.properties");
		write(target, "level.com.example=DEBUG\n");
		var link = Files.createSymbolicLink(dir.resolve("level.properties"), target);
		var levels = new LevelFileProperties(link, Duration.ofSeconds(1));
		Files.delete(target);
		assertEquals(Poll.REFUSED, levels.poll());
		assertEquals("""
				Refusing level file LINK: its symbolic link target does not exist.""".replace("LINK",
				link.toAbsolutePath().toString()), levels.refused());
		assertEquals("DEBUG", levels.valueOrNull("logging.level.com.example"));
		assertEquals(Poll.UNCHANGED, levels.poll());
	}

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

	/*
	 * Through SimplePropertiesProvider as a properties provider, the way the service
	 * loader uses it, so the level file is registered as the level override.
	 */
	private static LogConfig configFromProvider(SimpleProperties simple) {
		var registry = ServiceRegistry.of();
		registry.putIfAbsent(SimpleProperties.class, () -> simple);
		var provider = new SimplePropertiesProvider();
		var config = LogConfig.builder()
			.serviceRegistry(registry)
			.propertiesProvider(provider)
			.configurator(provider)
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
			// Facades only follow level changes for loggers that are changeable.
			assertTrue(gum.router().isChangeable("com.example.App"));
			write(file, "logging.level.com.example=TRACE\n");
			await(() -> gum.router().route("com.example.App", Level.TRACE).isEnabled());
			assertTrue(gum.router().route("com.example.App", Level.TRACE).isEnabled());
			assertEquals(Level.TRACE, config.levelResolver().resolveLevel("com.example.App"));
			assertEquals(List.of("Level file changed: " + file.toAbsolutePath()),
					alerts(config, Level.INFO).stream().filter(m -> m.startsWith("Level file changed")).toList());
		}
		finally {
			System.clearProperty("logging.level.com.example");
		}
	}

	/*
	 * Groups are defined in the application's properties; the level file only sets their
	 * levels. A group level in the file wins over a logger's own level from another
	 * source, and a changed group level applies.
	 */
	@Test
	void fileGroupLevelsWinOverEverySourceAndChangesApply(@TempDir Path dir) throws Exception {
		var file = dir.resolve("level.properties");
		write(file, "level.web=DEBUG\n");
		try (var gum = RainbowGum.builder(configFromProvider(simple("classpath:/level-file-groups.properties", file)))
			.build()
			.start()) {
			var config = gum.config();
			assertEquals(Level.DEBUG, config.levelResolver().resolveLevel("access-log"));
			assertEquals(Level.DEBUG, config.levelResolver().resolveLevel("com.example.Router$Handler"));
			assertEquals(Level.INFO, config.levelResolver().resolveLevel("com.example.Other"));
			write(file, "level.web=TRACE\n");
			await(() -> config.levelResolver().resolveLevel("access-log") == Level.TRACE);
			assertEquals(Level.TRACE, config.levelResolver().resolveLevel("access-log"));
			write(file, "");
			await(() -> config.levelResolver().resolveLevel("access-log") == Level.WARNING);
			assertEquals(Level.WARNING, config.levelResolver().resolveLevel("access-log"));
		}
	}

	@Test
	void invalidGroupEditKeepsEveryLevelUntilAValidEdit(@TempDir Path dir) throws Exception {
		var file = dir.resolve("level.properties");
		write(file, "level.web=DEBUG\n");
		try (var gum = RainbowGum.builder(configFromProvider(simple("classpath:/level-file-groups.properties", file)))
			.build()
			.start()) {
			var config = gum.config();
			assertTrue(gum.router().route("com.example.Other", Level.INFO).isEnabled());
			write(file, "level.web=invalid\nlevel.com.example.Other=ERROR\n");
			await(() -> !alerts(config, Level.ERROR).isEmpty());
			assertFalse(alerts(config, Level.ERROR).isEmpty());
			assertEquals(Level.DEBUG, config.levelResolver().resolveLevel("access-log"));
			assertEquals(Level.INFO, config.levelResolver().resolveLevel("com.example.Other"));
			assertTrue(gum.router().route("com.example.Other.New", Level.INFO).isEnabled());
			write(file, "level.web=TRACE\nlevel.com.example.Other=ERROR\n");
			await(() -> config.levelResolver().resolveLevel("access-log") == Level.TRACE);
			assertEquals(Level.TRACE, config.levelResolver().resolveLevel("access-log"));
			await(() -> !gum.router().route("com.example.Other", Level.INFO).isEnabled());
			assertFalse(gum.router().route("com.example.Other", Level.INFO).isEnabled());
			assertFalse(gum.router().route("com.example.Other.New", Level.INFO).isEnabled());
		}
	}

	@Test
	void invalidEditIsReportedOnceAndLeavesThePreviousSnapshot(@TempDir Path dir) throws Exception {
		var file = dir.resolve("level.properties");
		write(file, "level.web=DEBUG\n");
		var levels = new LevelFileProperties(file, Duration.ofSeconds(1));
		write(file, "level.web=invalid\n");
		var error = assertThrows(ValidationException.class, levels::poll);
		var message = error.getMessage();
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.simple.props.LevelFileProperties:
						Error for property. key: 'logging.level.web' from SIMPLE_PROPS[LEVEL_FILE:1][level.web], \
						'invalid' is not a valid value for java.lang.System.Logger.Level. \
						Valid values: 'all', 'trace', 'debug', 'info', 'warning', 'error', 'off', 'finest', 'fine', 'warn', 'severe'""",
				message == null ? null : message.replace(file.toAbsolutePath().toString(), "LEVEL_FILE"));
		assertEquals("DEBUG", levels.valueOrNull("logging.level.web"));
		assertEquals(Poll.UNCHANGED, levels.poll());
		write(file, "level.web=TRACE\n");
		assertEquals(Poll.CHANGED, levels.poll());
		assertEquals("TRACE", levels.valueOrNull("logging.level.web"));
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
		var properties = LogProperties.of(simple.properties());
		assertEquals("on", properties.valueOrNull(LogProperties.CHANGE_MODE_PROPERTY));
		assertEquals("level", properties.valueOrNull(LogProperties.CHANGE_LOGGER_PREFIX));
	}

	@Test
	void offByDefault(@TempDir Path dir) {
		var simple = simple("classpath:/logging.properties", dir.resolve("level.properties"));
		assertNull(simple.levelFile());
		assertNull(LogProperties.of(simple.properties()).valueOrNull(LogProperties.CHANGE_MODE_PROPERTY));
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
