package io.jstach.rainbowgum.simple.props;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogReporter;

/*
 * Levels from a file that is polled for changes. Only logging.level keys are answered,
 * ahead of every other source; any other key in the file is ignored with a warning.
 */
final class LevelFileProperties implements LogProperties.Listable, LogReporter.Reportable, AutoCloseable {

	static final String FILE_NAME = "level.properties";

	static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

	/*
	 * Above system properties (400), so a level in the file wins over every other source.
	 */
	static final int ORDER = 500;

	private static final LogEventFactory EVENTS = LogEventFactory.of(SimpleProperties.class.getName());

	private final Path path;

	private final Duration pollInterval;

	private volatile SimpleLogProperties current;

	private volatile @Nullable Thread watcher;

	LevelFileProperties(Path path, Duration pollInterval) {
		this.path = path.toAbsolutePath();
		this.pollInterval = pollInterval;
		this.current = read(this.path);
	}

	Path path() {
		return path;
	}

	static boolean isLevelKey(String key) {
		return key.equals(LogProperties.ROOT_PREFIX + "level") || key.startsWith(LogProperties.ROOT_PREFIX + "level.");
	}

	@Override
	public @Nullable String valueOrNull(String key) {
		return isLevelKey(key) ? current.valueOrNull(key) : null;
	}

	@Override
	public String description(String key) {
		return current.description(key);
	}

	@Override
	public List<String> keys() {
		return current.keys();
	}

	@Override
	public int order() {
		return ORDER;
	}

	@Override
	public void report(Appendable out) throws IOException {
		out.append("LEVEL_FILE[").append(path.toString()).append("]");
	}

	/*
	 * Keys in the file this ignores, each as a warning message.
	 */
	List<String> ignoredKeyMessages() {
		return current.keys()
			.stream()
			.filter(k -> !isLevelKey(k))
			.map(k -> "Ignoring property key '" + k + "' from " + current.description(k)
					+ ": the level file only sets logging.level keys.")
			.toList();
	}

	/*
	 * Rereads the file, which is small, rather than trusting its modification time, so a
	 * quick edit of the same length is not missed. Returns whether any level changed, so
	 * rewriting the file with the same levels is not a change.
	 */
	boolean poll() {
		var read = read(path);
		if (levels(read).equals(levels(current))) {
			return false;
		}
		current = read;
		return true;
	}

	/*
	 * Starts polling: on a change, an info alert, warnings for ignored keys, and a change
	 * published so levels are resolved again.
	 */
	void watch(LogConfig config) {
		if (watcher != null) {
			return;
		}
		var alerts = config.alerts();
		for (var message : ignoredKeyMessages()) {
			alerts.alert(EVENTS.eventNoArg(Level.WARNING, message, null));
		}
		watcher = Thread.ofPlatform().daemon().name("rainbowgum-level-file").start(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					Thread.sleep(pollInterval);
					if (poll()) {
						alerts.info(SimpleProperties.class, "Level file changed: " + path);
						for (var message : ignoredKeyMessages()) {
							alerts.alert(EVENTS.eventNoArg(Level.WARNING, message, null));
						}
						config.changePublisher().publish();
					}
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				catch (RuntimeException e) {
					alerts.alert(EVENTS.eventNoArg(Level.ERROR, "Failed to read level file: " + path, e));
				}
			}
		});
	}

	@Override
	public void close() {
		var w = watcher;
		if (w != null) {
			w.interrupt();
			watcher = null;
		}
	}

	private static Map<String, String> levels(SimpleLogProperties properties) {
		var levels = new TreeMap<String, String>();
		for (var key : properties.keys()) {
			if (isLevelKey(key)) {
				var value = properties.valueOrNull(key);
				if (value != null) {
					levels.put(key, value);
				}
			}
		}
		return levels;
	}

	private static SimpleLogProperties read(Path path) {
		try {
			String content = Files.readString(path, StandardCharsets.UTF_8);
			return SimpleLogProperties.read(new StringReader(content), path.toString());
		}
		catch (NoSuchFileException e) {
			return emptyFor(path);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static SimpleLogProperties emptyFor(Path path) {
		try {
			return SimpleLogProperties.read(new StringReader(""), path.toString());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
