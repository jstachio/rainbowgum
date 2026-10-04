package io.jstach.rainbowgum.output;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.util.function.Supplier;

import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.annotation.LogConfigurable;

/*
 * Factories for the standard out and standard err outputs. The generated
 * StdOutOutputBuilder and StdErrOutputBuilder are the public API; everything here stays
 * package private.
 */
final class ConsoleOutputs {

	private ConsoleOutputs() {
	}

	static final ConsoleStream DEFAULT_STREAM = ConsoleStream.CACHED;

	static ConsoleStream parseStream(String value) {
		return ConsoleStream.parse(value);
	}

	/**
	 * Creates a standard out output.
	 * @param name output name, used for property lookup.
	 * @param stream {@link ConsoleStream#CACHED} (default) uses {@link System#out} as it
	 * is when the output is created, {@link ConsoleStream#FOLLOW} reads it on every
	 * write.
	 * @return output provider.
	 */
	@LogConfigurable(name = "StdOutOutputBuilder", prefix = LogProperties.OUTPUT_PREFIX)
	static LogProvider<LogOutput> stdout(@LogConfigurable.KeyParameter String name,
			@LogConfigurable.DefaultParameter("DEFAULT_STREAM") @LogConfigurable.ConvertParameter("parseStream") ConsoleStream stream) {
		return switch (stream) {
			case CACHED -> (n, c) -> new StdOutOutput();
			case FOLLOW -> (n, c) -> new FollowStdOutOutput();
		};
	}

	/**
	 * Creates a standard err output.
	 * @param name output name, used for property lookup.
	 * @param stream {@link ConsoleStream#CACHED} (default) uses {@link System#err} as it
	 * is when the output is created, {@link ConsoleStream#FOLLOW} reads it on every
	 * write.
	 * @return output provider.
	 */
	@LogConfigurable(name = "StdErrOutputBuilder", prefix = LogProperties.OUTPUT_PREFIX)
	static LogProvider<LogOutput> stderr(@LogConfigurable.KeyParameter String name,
			@LogConfigurable.DefaultParameter("DEFAULT_STREAM") @LogConfigurable.ConvertParameter("parseStream") ConsoleStream stream) {
		return switch (stream) {
			case CACHED -> (n, c) -> new StdErrOutput();
			case FOLLOW -> (n, c) -> new FollowStdErrOutput();
		};
	}

}

/*
 * Looks up the target stream on every call, the same way Logback's console target does.
 */
final class FollowingOutputStream extends OutputStream {

	private final Supplier<PrintStream> stream;

	FollowingOutputStream(Supplier<PrintStream> stream) {
		this.stream = stream;
	}

	@Override
	public void write(int b) throws IOException {
		stream.get().write(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		stream.get().write(b, off, len);
	}

	@Override
	public void flush() throws IOException {
		stream.get().flush();
	}

}

abstract class AbstractConsoleOutput extends LogOutput.AbstractOutputStreamOutput {

	AbstractConsoleOutput(URI uri, OutputStream outputStream) {
		super(uri, outputStream);
	}

	/*
	 * The process owns standard out and err, not the logging system.
	 */
	@Override
	public final void close() {
	}

}

final class StdOutOutput extends AbstractConsoleOutput {

	StdOutOutput() {
		super(LogOutput.STDOUT_URI, System.out);
	}

	@Override
	public OutputType type() {
		return OutputType.CONSOLE_OUT;
	}

}

final class StdErrOutput extends AbstractConsoleOutput {

	StdErrOutput() {
		super(LogOutput.STDERR_URI, System.err);
	}

	@Override
	public OutputType type() {
		return OutputType.CONSOLE_ERR;
	}

}

final class FollowStdOutOutput extends AbstractConsoleOutput {

	FollowStdOutOutput() {
		super(LogOutput.STDOUT_URI, new FollowingOutputStream(() -> System.out));
	}

	@Override
	public OutputType type() {
		return OutputType.CONSOLE_OUT;
	}

}

final class FollowStdErrOutput extends AbstractConsoleOutput {

	FollowStdErrOutput() {
		super(LogOutput.STDERR_URI, new FollowingOutputStream(() -> System.err));
	}

	@Override
	public OutputType type() {
		return OutputType.CONSOLE_ERR;
	}

}
