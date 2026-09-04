package io.jstach.rainbowgum.rolling;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder.BufferHints;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogResponse.Status;
import io.jstach.rainbowgum.output.FileOutput;

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

	private final int maxFileSize;

	private final int maxHistory;

	private final int totalSizeCap;

	private final boolean cleanHistoryOnStart;

	private final Supplier<FileOutput> supplier;

	private FileOutput delegate;

	private long bytesWritten;

	DefaultRollingFileOutput(Path activeFile, RollingPolicy.ParsedPattern pattern, int maxFileSize, int maxHistory,
			int totalSizeCap, boolean cleanHistoryOnStart, Supplier<FileOutput> supplier) {
		this.activeFile = activeFile;
		this.pattern = pattern;
		this.maxFileSize = maxFileSize;
		this.maxHistory = maxHistory;
		this.totalSizeCap = totalSizeCap;
		this.cleanHistoryOnStart = cleanHistoryOnStart;
		this.supplier = supplier;
		this.delegate = supplier.get();
		this.bytesWritten = currentFileSize();
	}

	private long currentFileSize() {
		try {
			return Files.exists(activeFile) ? Files.size(activeFile) : 0L;
		}
		catch (IOException e) {
			return 0L;
		}
	}

	@Override
	public void start(LogConfig config) {
		if (cleanHistoryOnStart) {
			RollingPolicy.cleanHistory(activeFile, pattern, maxHistory, totalSizeCap);
		}
		delegate.start(config);
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
		if (maxFileSize > 0 && bytesWritten >= maxFileSize) {
			delegate.close();
			RollingPolicy.roll(activeFile, pattern, maxHistory, totalSizeCap);
			delegate = supplier.get();
			bytesWritten = 0;
		}
	}

	@Override
	public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
		maybeRoll();
		delegate.write(event, bytes, off, len, contentType);
		bytesWritten += len;
	}

	@Override
	public void write(LogEvent event, ByteBuffer buf, ContentType contentType) {
		int len = buf.remaining();
		maybeRoll();
		delegate.write(event, buf, contentType);
		bytesWritten += len;
	}

	@Override
	public void flush() {
		delegate.flush();
	}

	@Override
	public void close() {
		delegate.close();
	}

	@Override
	public Status reopen() {
		return delegate.reopen();
	}

	@Override
	public String toString() {
		return getClass().getName() + "[activeFile=" + activeFile + ", maxFileSize=" + maxFileSize + ", maxHistory="
				+ maxHistory + "]";
	}

}
