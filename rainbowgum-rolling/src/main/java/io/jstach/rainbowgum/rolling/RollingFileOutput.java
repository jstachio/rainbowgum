package io.jstach.rainbowgum.rolling;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.annotation.LogConfigurable;
import io.jstach.rainbowgum.annotation.LogConfigurable.DefaultParameter;
import io.jstach.rainbowgum.output.FileOutput;

/**
 * A {@link FileOutput} that rolls (renames the active file to a numbered archive and
 * starts a fresh one) once it grows past {@code maxFileSize}, retaining at most
 * {@code maxHistory} archives.
 * <p>
 * Deliberately size (not calendar/date) triggered and only supports a <code>%i</code>
 * (rotation index) token in {@link #DEFAULT_FILE_NAME_PATTERN} - see
 * {@code doc/overview.html}'s "Rolling Files" section for why: reopen-on-external-signal
 * (e.g. via <code>logrotate</code>) remains the recommended approach for anything beyond
 * "keep a small/desktop app from filling its disk", and that simpler need does not
 * benefit from Logback-style calendar based archive naming.
 * <p>
 * Registered under the {@value #ROLLING_SCHEME} URI scheme (see
 * {@code RollingConfigurator}), e.g. {@code rolling:///var/log/app.log?maxFileSize=...}.
 * Every property below lives under the same {@link LogProperties#OUTPUT_PREFIX} as
 * {@link FileOutput} itself - {@code uri}/{@code fileName}/{@code append}/
 * {@code prudent}/{@code bufferSize} all still apply and are passed straight through to
 * the underlying {@link FileOutput} this wraps.
 */
public interface RollingFileOutput extends FileOutput {

	/**
	 * URI scheme for rolling file outputs.
	 */
	static final String ROLLING_SCHEME = "rolling";

	/**
	 * Default max file size in bytes before a roll is triggered - 10MB, matching
	 * Logback/Spring Boot's own default.
	 */
	static final int DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024;

	/**
	 * Default number of archives to retain - 7, matching Logback/Spring Boot's own
	 * default.
	 */
	static final int DEFAULT_MAX_HISTORY = 7;

	/**
	 * Default total archive size cap in bytes. {@code 0} means unlimited.
	 */
	static final int DEFAULT_TOTAL_SIZE_CAP = 0;

	/**
	 * Default for whether archive pruning ({@code maxHistory}/{@code totalSizeCap}) also
	 * runs once at {@link #start(LogConfig)}, not just after each roll.
	 */
	static final boolean DEFAULT_CLEAN_HISTORY_ON_START = false;

	/**
	 * Default {@code fileNamePattern} - the literal text appended directly after the
	 * active file's own path (unlike Logback, never containing the base file name itself)
	 * with <code>%i</code> substituted for the rotation index, e.g. <code>app.log</code>
	 * with this pattern archives to <code>app.log.1</code>, <code>app.log.2</code>, etc.
	 * A pattern ending in <code>.gz</code> gzip compresses archives on rotation.
	 */
	static final String DEFAULT_FILE_NAME_PATTERN = ".%i";

	/**
	 * Creates a rolling file output provider from a URI reference - the entry point used
	 * by the {@value #ROLLING_SCHEME} scheme registration.
	 * @param ref uri reference, may have a query string of builder properties (same
	 * convention as {@link FileOutput#of(LogProviderRef)}).
	 * @return provider.
	 */
	public static LogProvider<io.jstach.rainbowgum.LogOutput> of(LogProviderRef ref) {
		return (name, config) -> provide(ref, name, config);
	}

	/**
	 * Creates a rolling file output provider from a lambda builder and uses the config
	 * properties from the returned log provider - mirrors
	 * {@link FileOutput#of(Consumer)}.
	 * @param consumer builder lambda.
	 * @return provider.
	 */
	public static LogProvider<RollingFileOutput> of(Consumer<RollingFileOutputBuilder> consumer) {
		return (name, config) -> {
			var builder = new RollingFileOutputBuilder(name);
			consumer.accept(builder);
			builder.fromProperties(config.properties());
			return builder.build().provide(name, config);
		};
	}

	private static FileOutput provide(LogProviderRef ref, String name, LogConfig config) {
		var b = new RollingFileOutputBuilder(name);
		var uri = ref.uri();
		var properties = config.properties();
		LogProperties combined;
		if (uri.getQuery() != null) {
			combined = LogProperties.of(uri, b.propertyPrefix(), properties, ref.keyOrNull());
			String s = uri.toString();
			int index = s.indexOf('?');
			uri = URI.create(s.substring(0, index));
		}
		else {
			combined = properties;
		}
		b.uri(uri);
		b.fromProperties(combined);
		return b.build().provide(name, config);
	}

	/**
	 * Creates a rolling file output provider.
	 * @param name name of output, not file name.
	 * @param uri file uri.
	 * @param fileName file name.
	 * @param maxFileSize max file size in bytes before a roll is triggered.
	 * @param maxHistory number of archives to retain.
	 * @param totalSizeCap total archive size cap in bytes; {@code <= 0} means unlimited.
	 * @param cleanHistoryOnStart whether pruning also runs once at start, not just after
	 * each roll.
	 * @param fileNamePattern archive naming pattern; must contain <code>%i</code> and
	 * must not contain <code>%d</code> (date based rotation is not supported).
	 * @return rolling file output provider.
	 */
	@LogConfigurable(name = "RollingFileOutputBuilder", prefix = LogProperties.OUTPUT_PREFIX)
	public static LogProvider<RollingFileOutput> of(@LogConfigurable.KeyParameter String name, @Nullable URI uri,
			@Nullable String fileName, @DefaultParameter("DEFAULT_MAX_FILE_SIZE") Integer maxFileSize,
			@DefaultParameter("DEFAULT_MAX_HISTORY") Integer maxHistory,
			@DefaultParameter("DEFAULT_TOTAL_SIZE_CAP") Integer totalSizeCap,
			@DefaultParameter("DEFAULT_CLEAN_HISTORY_ON_START") Boolean cleanHistoryOnStart,
			@DefaultParameter("DEFAULT_FILE_NAME_PATTERN") String fileNamePattern) {
		var parsedPattern = RollingPolicy.ParsedPattern.parse(fileNamePattern);
		File file;
		if (fileName != null) {
			file = new File(fileName);
		}
		else if (uri != null) {
			file = new File(uri.getPath());
		}
		else {
			throw new IllegalArgumentException("fileName and uri cannot both be unset.");
		}
		Path activeFile = file.toPath().toAbsolutePath();
		String fileNameForDelegate = file.getPath();
		int maxFileSize_ = maxFileSize;
		int maxHistory_ = maxHistory;
		int totalSizeCap_ = totalSizeCap;
		boolean cleanHistoryOnStart_ = cleanHistoryOnStart;
		return (n, config) -> {
			Supplier<FileOutput> supplier = () -> FileOutput.of(fb -> fb.fileName(fileNameForDelegate).append(true))
				.provide(n, config);
			return new DefaultRollingFileOutput(activeFile, parsedPattern, maxFileSize_, maxHistory_, totalSizeCap_,
					cleanHistoryOnStart_, supplier);
		};
	}

}
