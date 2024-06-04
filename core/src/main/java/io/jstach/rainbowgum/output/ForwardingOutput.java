package io.jstach.rainbowgum.output;

import java.net.URI;
import java.nio.ByteBuffer;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEncoder.Buffer;
import io.jstach.rainbowgum.LogEncoder.BufferHints;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogResponse.Status;

interface ForwardingOutput extends LogOutput {

	@Nullable
	LogOutput delegate();

	@Override
	default void start(LogConfig config) {
		var d = delegate();
		if (d != null) {
			d.start(config);
		}
	}

	@Override
	default void write(@NonNull LogEvent[] events, int count, LogEncoder encoder) {
		var d = delegate();
		if (d != null) {
			d.write(events, count, encoder);
		}
	}

	@Override
	default void write(@NonNull LogEvent[] events, int count, LogEncoder encoder, Buffer buffer) {
		var d = delegate();
		if (d != null) {
			d.write(events, count, encoder, buffer);
		}
	}

	@Override
	default void write(LogEvent event, Buffer buffer) {
		var d = delegate();
		if (d != null) {
			d.write(event, buffer);
		}
	}

	@Override
	default void write(LogEvent event, String s) {
		var d = delegate();
		if (d != null) {
			d.write(event, s);
		}
	}

	@Override
	default void write(LogEvent event, byte[] bytes, ContentType contentType) {
		var d = delegate();
		if (d != null) {
			d.write(event, bytes, contentType);
		}
	}

	@Override
	default void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
		var d = delegate();
		if (d != null) {
			d.write(event, bytes, off, len, contentType);
		}
	}

	@Override
	default void write(LogEvent event, ByteBuffer buf, ContentType contentType) {
		var d = delegate();
		if (d != null) {
			d.write(event, buf, contentType);
		}
	}

	@Override
	default void flush() {
		var d = delegate();
		if (d != null) {
			d.flush();
		}
	}

	@Override
	default void close() {
		var d = delegate();
		if (d != null) {
			d.close();
		}
	}

	@Override
	public URI uri() throws UnsupportedOperationException;

	@Override
	public OutputType type();

	@Override
	public BufferHints bufferHints();

	@Override
	public Status reopen();

}
