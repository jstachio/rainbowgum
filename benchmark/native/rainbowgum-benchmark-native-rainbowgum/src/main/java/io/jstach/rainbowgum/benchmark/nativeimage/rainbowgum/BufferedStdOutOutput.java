package io.jstach.rainbowgum.benchmark.nativeimage.rainbowgum;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.net.URI;

import io.jstach.rainbowgum.LogOutput;

/**
 * A stdout output that writes to the raw file descriptor wrapped in a plain
 * {@link BufferedOutputStream} (8192 bytes, matching Log4j2's own default buffer size)
 * instead of {@link System#out} - deliberately <strong>not</strong> a
 * {@link java.io.PrintStream}, which auto-flushes on every {@code write} call containing
 * a newline (confirmed directly this session - disabling
 * {@link io.jstach.rainbowgum.LogAppender.AppenderFlag#DISABLE_IMMEDIATE_FLUSH} entirely
 * still produced one {@code write()} syscall per event against the default console
 * output). {@link #write} here just appends to {@link BufferedOutputStream}'s own
 * internal buffer - no syscall - until either that buffer would overflow or
 * {@link #flush()} is called explicitly, which is what
 * {@link io.jstach.rainbowgum.LogAppender.AppenderType#SYNCHRONIZED_DEFERRED_FLUSH} needs
 * to have anything to actually defer.
 * <p>
 * <strong>This buffering is a real, deliberate departure from the
 * <a href="https://12factor.net/logs">twelve-factor app</a> guidance that an app write
 * its own event stream unbuffered to stdout</strong>, and it has a real, measured
 * consequence: whatever is sitting in the buffer, unflushed, at the moment the process is
 * killed (not stopped gracefully) is lost - confirmed directly by killing a process
 * mid-run and observing already-{@code write()}-called content that never reached the OS
 * at all, unrecoverable. The default console output ({@code StdOutOutput}, wrapping
 * {@link System#out}) does not have this risk - its underlying {@link System#out}
 * {@link java.io.PrintStream} auto-flushes on every write, confirmed by killing a process
 * using it mid-run and finding the last line's own embedded timestamp matched the kill
 * instant to the millisecond, every time. Exists purely to give
 * {@code SYNCHRONIZED_DEFERRED_FLUSH} something to defer into - not a general-purpose
 * recommendation.
 */
final class BufferedStdOutOutput extends LogOutput.AbstractOutputStreamOutput {

	static final String SCHEME = "buffered-stdout";

	BufferedStdOutOutput() {
		super(URI.create(SCHEME + ":///"), new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 8192));
	}

	@Override
	public OutputType type() {
		return OutputType.CONSOLE_OUT;
	}

	@Override
	public void close() {
	}

}
