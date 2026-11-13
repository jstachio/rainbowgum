package io.jstach.rainbowgum.rolling;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEncoder.Buffer;
import io.jstach.rainbowgum.LogEncoder.BufferHints;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.file.FileOutput;

/*
 * The active-file byte counter is tracked internally rather than stat-ing the file on
 * every write, matching the "no per-event filesystem call" spirit of the rest of
 * RainbowGum's output layer. Per LogOutput's own contract ("there will be no
 * overlapping write/flush/close calls") this never needs synchronization - the
 * appender/publisher combo already guarantees single-threaded access.
 */
final class DefaultRollingFileOutput implements RollingFileOutput {

	private final Path activeFile;

	private final RollingPolicy.ParsedPattern pattern;

	final long maxFileSize;

	private final int maxHistory;

	final long totalSizeCap;

	private final boolean cleanHistoryOnStart;

	private final Supplier<FileOutput> supplier;

	private final LogConfig config;

	private FileOutput delegate;

	private long bytesWritten;

	private long nextRollSize;

	private boolean needsOpen;

	private boolean rollingRecovery;

	private boolean started;

	private boolean closed;

	/*
	 * Compresses the last rolled file. Only the writing thread starts or joins it.
	 */
	private @Nullable Thread compressor;

	/* Written by the compressor and read only after joining it. */
	private @Nullable RuntimeException compressionFailure;

	private boolean reportingCompressionFailure;

	DefaultRollingFileOutput(Path activeFile, RollingPolicy.ParsedPattern pattern, long maxFileSize, int maxHistory,
			long totalSizeCap, boolean cleanHistoryOnStart, Supplier<FileOutput> supplier, FileOutput delegate,
			LogConfig config) {
		this.activeFile = activeFile;
		this.pattern = pattern;
		this.maxFileSize = maxFileSize;
		this.nextRollSize = maxFileSize;
		this.maxHistory = maxHistory;
		this.totalSizeCap = totalSizeCap;
		this.cleanHistoryOnStart = cleanHistoryOnStart;
		this.supplier = supplier;
		this.config = config;
		this.delegate = delegate;
		try {
			this.bytesWritten = currentFileSize();
		}
		catch (RuntimeException e) {
			try {
				delegate.close();
			}
			catch (RuntimeException closeFailure) {
				e.addSuppressed(closeFailure);
			}
			throw e;
		}
	}

	private long currentFileSize() {
		try {
			return Files.size(activeFile);
		}
		catch (NoSuchFileException e) {
			return 0L;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void start(LogConfig config) {
		if (cleanHistoryOnStart) {
			for (var skipped : RollingPolicy.cleanHistory(activeFile, pattern, maxHistory, totalSizeCap)) {
				config.alerts()
					.warn(getClass(), "Ignored '" + skipped
							+ "' while cleaning history: it is not a regular file with a canonical positive archive index");
			}
		}
		delegate.start(config);
		started = true;
		// A previous process may have stopped before compressing what it rolled.
		if (pattern.gzip() && Files.exists(RollingPolicy.pendingPath(activeFile))) {
			startCompression();
		}
	}

	@Override
	public URI uri() {
		return activeFile.toUri();
	}

	@Override
	public OutputType type() {
		return OutputType.FILE;
	}

	@Override
	public BufferHints bufferHints() {
		return delegate.bufferHints();
	}

	// Identity is required because Throwable rejects self-suppression.
	@SuppressWarnings("ReferenceEquality")
	private void maybeRoll() {
		// A failure listener may log through this same output. Keep the delegate
		// open and let that event append without starting another rotation.
		if (reportingCompressionFailure) {
			return;
		}
		if (needsOpen && !rollingRecovery) {
			// An explicit reopen failure is not an automatic rotation failure.
			openDelegate();
		}
		try {
			if (needsOpen) {
				openDelegate();
				deferRoll();
			}
			reportCompletedCompression();
			if (maxFileSize > 0 && bytesWritten >= nextRollSize) {
				// Join and notify before closing the delegate: listeners can log.
				awaitCompression();
				// Mark this before closing: any failure must leave the next write able to
				// recover instead of silently writing to a closed output.
				needsOpen = true;
				rollingRecovery = true;
				delegate.close();
				if (RollingPolicy.rollUncompressed(activeFile, pattern, maxHistory, totalSizeCap)) {
					startCompression();
				}
				openDelegate();
				nextRollSize = maxFileSize;
			}
		}
		catch (RuntimeException e) {
			config.metrics().errorCounter(LogMetrics.ROLL_FAIL_METRIC, 1);
			config.alerts().error(getClass(), "Failed to roll file '" + activeFile + "'", e);
			// Archive maintenance must not discard the current event when the active
			// file can still be opened. A replacement always opens in append mode.
			try {
				openDelegate();
				deferRoll();
			}
			catch (RuntimeException recoveryFailure) {
				if (recoveryFailure != e) {
					recoveryFailure.addSuppressed(e);
				}
				throw recoveryFailure;
			}
		}
	}

	/*
	 * Gzip reads the whole rolled file, so it runs off the writing thread instead of
	 * holding up every logger behind the appender lock. The next roll waits for it.
	 */
	private void startCompression() {
		var thread = new Thread(this::compress, "rainbowgum-rolling-compress");
		thread.setDaemon(true);
		compressor = thread;
		thread.start();
	}

	private void compress() {
		try {
			RollingPolicy.compress(activeFile, pattern, maxHistory, totalSizeCap);
		}
		catch (RuntimeException e) {
			config.metrics().errorCounter(LogMetrics.ROLL_FAIL_METRIC, 1);
			// Alert listeners run synchronously and can acquire the appender lock.
			// The writer may hold that lock while joining us, so it reports this
			// failure after the join instead of invoking user code on this thread.
			compressionFailure = e;
		}
	}

	private void reportCompletedCompression() {
		var thread = compressor;
		if (thread != null && !thread.isAlive()) {
			awaitCompression();
		}
	}

	private void awaitCompression() {
		var thread = compressor;
		if (thread == null) {
			return;
		}
		compressor = null;
		boolean interrupted = false;
		while (true) {
			try {
				thread.join();
				break;
			}
			catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
		var failure = compressionFailure;
		compressionFailure = null;
		if (failure != null) {
			reportingCompressionFailure = true;
			try {
				config.alerts()
					.error(getClass(), "Failed to compress '" + RollingPolicy.pendingPath(activeFile)
							+ "'; it is kept and compressed again on the next roll", failure);
			}
			finally {
				reportingCompressionFailure = false;
			}
		}
	}

	private void deferRoll() {
		// A failed gzip/rename may already have scanned the whole active file.
		// Retry after its size doubles so repeated failures do not rescan a growing
		// file for every event. Explicit reopen allows an earlier retry.
		long size = Math.max(bytesWritten, maxFileSize);
		nextRollSize = size > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : size * 2;
	}

	private void openDelegate() {
		// Re-read the size because rotation might have failed before or after moving
		// the active file. A failed replacement open must not rotate it a second time.
		long size = currentFileSize();
		FileOutput replacement = supplier.get();
		try {
			if (started) {
				replacement.start(config);
			}
		}
		catch (RuntimeException e) {
			try {
				replacement.close();
			}
			catch (RuntimeException closeFailure) {
				e.addSuppressed(closeFailure);
			}
			throw e;
		}
		delegate = replacement;
		bytesWritten = size;
		needsOpen = false;
		rollingRecovery = false;
	}

	@Override
	public void write(LogEvent[] events, int count, LogEncoder encoder, Buffer buffer) {
		for (int i = 0; i < count; i++) {
			var event = events[i];
			try {
				buffer.clear();
				encoder.encode(event, buffer);
				write(event, buffer);
			}
			catch (Exception e) {
				config.metrics().errorCounter(LogMetrics.EVENTS_FAILED_METRIC, 1);
				config.alerts().error(getClass(), "Failed to append event to rolling file '" + activeFile + "'", e);
			}
		}
	}

	@Override
	public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
		if (closed) {
			return;
		}
		maybeRoll();
		delegate.write(event, bytes, off, len, contentType);
		bytesWritten += len;
	}

	@Override
	public void write(LogEvent event, ByteBuffer buf, ContentType contentType) {
		if (closed) {
			return;
		}
		int len = buf.remaining();
		maybeRoll();
		delegate.write(event, buf, contentType);
		bytesWritten += len;
	}

	@Override
	public void flush() {
		if (!closed && !needsOpen) {
			delegate.flush();
			reportCompletedCompression();
		}
	}

	@Override
	public void close() {
		if (!closed) {
			closed = true;
			try {
				delegate.close();
			}
			finally {
				awaitCompression();
			}
		}
	}

	@Override
	public void reopen() {
		if (closed) {
			return;
		}
		needsOpen = true;
		rollingRecovery = false;
		nextRollSize = maxFileSize;
		delegate.close();
		openDelegate();
	}

	@Override
	public String toString() {
		return getClass().getName() + "[activeFile=" + activeFile + ", maxFileSize=" + maxFileSize + ", maxHistory="
				+ maxHistory + "]";
	}

}
