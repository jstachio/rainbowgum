package io.jstach.rainbowgum.simple.props;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
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
 * ahead of every other source; any other key in the file is ignored with a warning. Keys
 * may leave off the logging prefix, as in logging.properties.
 *
 * Whoever can write the file controls levels, and so what reaches the logs, so like ssh's
 * StrictModes the file is refused while it or its directory can be written by group or
 * others, or is owned by someone other than the user running the application or root.
 * The check is skipped where POSIX permissions are not available.
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

	/*
	 * Why the file is refused, or null while it is read.
	 */
	private volatile @Nullable String refused;

	LevelFileProperties(Path path, Duration pollInterval) {
		this.path = path.toAbsolutePath();
		this.pollInterval = pollInterval;
		this.refused = refusal(this.path);
		this.current = refused == null ? read(this.path) : emptyFor(this.path);
	}

	enum Poll {

		UNCHANGED, CHANGED, REFUSED

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
	 * quick edit of the same length is not missed. Rewriting the file with the same
	 * levels is not a change. A file that becomes refused keeps the levels last read from
	 * it and is REFUSED once per reason, so polling does not repeat the alert.
	 */
	Poll poll() {
		String reason = refusal(path);
		if (reason != null) {
			boolean changed = !reason.equals(refused);
			refused = reason;
			return changed ? Poll.REFUSED : Poll.UNCHANGED;
		}
		refused = null;
		var read = read(path);
		if (levels(read).equals(levels(current))) {
			return Poll.UNCHANGED;
		}
		current = read;
		return Poll.CHANGED;
	}

	@Nullable String refused() {
		return refused;
	}

	/*
	 * Why the file or its directory could be changed by someone other than the user
	 * running the application or root, or null. A missing file only has its directory
	 * checked, since the file can appear later.
	 */
	static @Nullable String refusal(Path file) {
		var dir = file.getParent();
		String reason = dir == null ? null : refusal(file, dir, "its directory " + dir);
		if (reason == null && Files.exists(file)) {
			reason = refusal(file, file, "it");
		}
		return reason;
	}

	private static @Nullable String refusal(Path file, Path checked, String what) {
		PosixFileAttributes attributes;
		try {
			attributes = Files.readAttributes(checked, PosixFileAttributes.class);
		}
		catch (UnsupportedOperationException | NoSuchFileException e) {
			return null;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		var permissions = attributes.permissions();
		if (permissions.contains(PosixFilePermission.GROUP_WRITE)
				|| permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
			return "Refusing level file " + file + ": " + what
					+ " is writable by group or others. Remove that access, for example with chmod go-w.";
		}
		String owner = attributes.owner().getName();
		String user = System.getProperty("user.name");
		if (!owner.equals("root") && user != null && !user.equals("?") && !owner.equals(user)) {
			return "Refusing level file " + file + ": " + what + " is owned by '" + owner + "', not by '" + user
					+ "' running the application or root.";
		}
		return null;
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
		String reason = refused;
		if (reason != null) {
			alerts.alert(EVENTS.eventNoArg(Level.ERROR, reason, null));
		}
		for (var message : ignoredKeyMessages()) {
			alerts.alert(EVENTS.eventNoArg(Level.WARNING, message, null));
		}
		watcher = Thread.ofPlatform().daemon().name("rainbowgum-level-file").start(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					Thread.sleep(pollInterval);
					switch (poll()) {
						case CHANGED -> {
							alerts.info(SimpleProperties.class, "Level file changed: " + path);
							for (var message : ignoredKeyMessages()) {
								alerts.alert(EVENTS.eventNoArg(Level.WARNING, message, null));
							}
							config.changePublisher().publish();
						}
						case REFUSED -> {
							String r = refused;
							if (r != null) {
								alerts.alert(EVENTS.eventNoArg(Level.ERROR, r, null));
							}
						}
						case UNCHANGED -> {
						}
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
			return SimpleLogProperties.read(new StringReader(content), path.toString()).withShortKeys();
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
