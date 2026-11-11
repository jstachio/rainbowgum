package io.jstach.rainbowgum.benchmark.nativeimage.rainbowgum;

import java.net.URI;
import java.util.Objects;

import io.jstach.rainbowgum.LogEncoder.BufferHints;
import io.jstach.rainbowgum.LogOutput;

/**
 * A stdout output identical to {@link LogOutput#ofStandardOut()} except it hints
 * {@link LogOutput.WriteMethod#STRING} instead of {@link LogOutput.WriteMethod#BYTES} -
 * this activates {@link LogOutput#write(io.jstach.rainbowgum.LogEvent, String)}'s default
 * implementation, a plain {@code String.getBytes(UTF_8)} call with no
 * {@link java.nio.charset.CharsetEncoder}, matching Logback's own encoding strategy (see
 * {@code benchmark/native/RESULTS.md}'s "Non-Latin1 content" section - no built-in
 * {@link LogOutput} hints {@code STRING}, so this fast path is otherwise dead code).
 */
final class StringStdOutOutput extends LogOutput.AbstractOutputStreamOutput {

	static final String SCHEME = "string-stdout";

	StringStdOutOutput() {
		super(URI.create(SCHEME + ":///"), Objects.requireNonNull(System.out));
	}

	@Override
	public OutputType type() {
		return OutputType.CONSOLE_OUT;
	}

	@Override
	public BufferHints bufferHints() {
		return LogOutput.WriteMethod.STRING;
	}

	@Override
	public void close() {
	}

}
