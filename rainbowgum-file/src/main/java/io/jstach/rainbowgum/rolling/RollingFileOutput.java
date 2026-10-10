package io.jstach.rainbowgum.rolling;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.annotation.LogConfigurable;
import io.jstach.rainbowgum.annotation.LogConfigurable.ConvertParameter;
import io.jstach.rainbowgum.annotation.LogConfigurable.DefaultParameter;
import io.jstach.rainbowgum.file.DataSize;
import io.jstach.rainbowgum.file.FileOutput;
import io.jstach.rainbowgum.file.FileOutputBuilder;

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
 * Its properties are <code>logging.output.rolling.{name}.</code> followed by a
 * {@link RollingFileOutputBuilder} property name. {@code uri}, {@code fileName},
 * {@code append}, {@code prudent} and {@code bufferSize} mean the same as for
 * {@link FileOutput} and are passed to the underlying file output this wraps. The
 * {@code append} setting applies when the output is first opened. Replacement outputs
 * always append so recovery preserves any active contents left behind by a failed
 * rotation.
 * <p>
 * Failed automatic rotation attempts record {@link LogMetrics#ROLL_FAIL_METRIC} and an
 * error alert identifying the active file. The event that triggered the failed rotation
 * is not retried and also counts toward {@link LogMetrics#EVENTS_FAILED_METRIC}. The next
 * write attempts to recover the output. Archive changes already completed before a
 * failure are not rolled back. An explicit {@link #reopen()} after external rotation
 * instead uses {@link LogMetrics#REOPEN_FAIL_METRIC}.
 * <p>
 * Only one process should rotate these files. Prudent mode locks individual writes, but
 * does not coordinate archive rotation between processes.
 */
public interface RollingFileOutput extends FileOutput {

	/**
	 * URI scheme for rolling file outputs.
	 */
	static final String ROLLING_SCHEME = "rolling";

	/**
	 * Default buffer size, the same as {@link FileOutput#DEFAULT_BUFFER_SIZE}.
	 */
	static final DataSize DEFAULT_BUFFER_SIZE = FileOutput.DEFAULT_BUFFER_SIZE;

	/**
	 * Default max file size before a roll is triggered: 10MB, matching Logback and Spring
	 * Boot's own default.
	 */
	static final DataSize DEFAULT_MAX_FILE_SIZE = DataSize.ofMegabytes(10);

	/**
	 * Default number of archives to retain - 7, matching Logback/Spring Boot's own
	 * default.
	 */
	static final int DEFAULT_MAX_HISTORY = 7;

	/**
	 * Default total archive size cap. {@link DataSize#ZERO} means unlimited.
	 */
	static final DataSize DEFAULT_TOTAL_SIZE_CAP = DataSize.ZERO;

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
	 * @param append whether or not to append to an existing file when first opened.
	 * @param prudent logback prudent mode where files are locked on each write.
	 * @param bufferSize buffer size, in
	 * {@link io.jstach.rainbowgum.file.DataSize#parse(String)} format when set by
	 * property, e.g. {@code 8KB}. Zero means unbuffered.
	 * @param maxFileSize max file size before a roll is triggered, in
	 * {@link io.jstach.rainbowgum.file.DataSize#parse(String)} format when set by
	 * property, e.g. {@code 10MB}.
	 * @param maxHistory number of archives to retain.
	 * @param totalSizeCap total archive size cap; zero means unlimited.
	 * @param cleanHistoryOnStart whether pruning also runs once at start, not just after
	 * each roll.
	 * @param fileNamePattern archive naming pattern; must contain <code>%i</code> and
	 * must not contain <code>%d</code> (date based rotation is not supported).
	 * @return rolling file output provider.
	 */
	@LogConfigurable(name = "RollingFileOutputBuilder", prefix = LogProperties.OUTPUT_PREFIX + "rolling.{name}.")
	public static LogProvider<RollingFileOutput> of(@LogConfigurable.KeyParameter String name, @Nullable URI uri,
			@Nullable String fileName, @Nullable Boolean append, @Nullable Boolean prudent,
			@ConvertParameter("parseDataSize") @DefaultParameter("DEFAULT_BUFFER_SIZE") DataSize bufferSize,
			@ConvertParameter("parseDataSize") @DefaultParameter("DEFAULT_MAX_FILE_SIZE") DataSize maxFileSize,
			@DefaultParameter("DEFAULT_MAX_HISTORY") Integer maxHistory,
			@ConvertParameter("parseDataSize") @DefaultParameter("DEFAULT_TOTAL_SIZE_CAP") DataSize totalSizeCap,
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
		long maxFileSize_ = maxFileSize.toBytes();
		int maxHistory_ = maxHistory;
		long totalSizeCap_ = totalSizeCap.toBytes();
		boolean cleanHistoryOnStart_ = cleanHistoryOnStart;
		return (n, config) -> {
			var fileBuilder = new FileOutputBuilder(n).fileName(fileNameForDelegate)
				.append(append == null ? true : append)
				.prudent(prudent)
				.bufferSize(bufferSize);
			var initialOutput = fileBuilder.build().provide(n, config);
			// append=false applies only to initial opening. Recovery must preserve any
			// active contents left behind by a failed rotation.
			var replacementProvider = fileBuilder.append(true).build();
			Supplier<FileOutput> supplier = () -> replacementProvider.provide(n, config);
			return new DefaultRollingFileOutput(activeFile, parsedPattern, maxFileSize_, maxHistory_, totalSizeCap_,
					cleanHistoryOnStart_, supplier, initialOutput, config);
		};
	}

	/**
	 * Converts a {@code maxFileSize} or {@code totalSizeCap} property value.
	 * @param value property value.
	 * @return data size.
	 * @see DataSize#parse(String)
	 */
	static DataSize parseDataSize(String value) {
		return DataSize.parse(value);
	}

}
