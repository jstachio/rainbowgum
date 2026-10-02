package io.jstach.rainbowgum.rolling;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.function.Supplier;

import io.jstach.rainbowgum.LogConfig;
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

	private boolean needsOpen;

	private boolean rollingRecovery;

	private boolean started;

	private boolean closed;

	DefaultRollingFileOutput(Path activeFile, RollingPolicy.ParsedPattern pattern, long maxFileSize, int maxHistory,
			long totalSizeCap, boolean cleanHistoryOnStart, Supplier<FileOutput> supplier, FileOutput delegate,
			LogConfig config) {
		this.activeFile = activeFile;
		this.pattern = pattern;
		this.maxFileSize = maxFileSize;
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
			RollingPolicy.cleanHistory(activeFile, pattern, maxHistory, totalSizeCap);
		}
		delegate.start(config);
		started = true;
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

	private void maybeRoll() {
		if (needsOpen && !rollingRecovery) {
			// An explicit reopen failure is not an automatic rotation failure.
			openDelegate();
		}
		try {
			if (needsOpen) {
				openDelegate();
			}
			if (maxFileSize > 0 && bytesWritten >= maxFileSize) {
				// Mark this before closing: any failure must leave the next write able to
				// recover instead of silently writing to a closed output.
				needsOpen = true;
				rollingRecovery = true;
				delegate.close();
				RollingPolicy.roll(activeFile, pattern, maxHistory, totalSizeCap);
				openDelegate();
			}
		}
		catch (RuntimeException e) {
			config.metrics().errorCounter(LogMetrics.ROLL_FAIL_METRIC, 1);
			config.alerts().error(getClass(), "Failed to roll file '" + activeFile + "'", e);
			throw e;
		}
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
		}
	}

	@Override
	public void close() {
		if (!closed) {
			closed = true;
			delegate.close();
		}
	}

	@Override
	public void reopen() {
		if (closed) {
			return;
		}
		needsOpen = true;
		rollingRecovery = false;
		delegate.close();
		openDelegate();
	}

	@Override
	public String toString() {
		return getClass().getName() + "[activeFile=" + activeFile + ", maxFileSize=" + maxFileSize + ", maxHistory="
				+ maxHistory + "]";
	}

}
