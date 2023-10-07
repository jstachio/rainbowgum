package io.jstach.rainbowgum;

import io.jstach.rainbowgum.LogEncoder.Buffer;
import io.jstach.rainbowgum.LogEncoder.BufferProvider;

/**
 * Log encoders must be thread safe.
 */
public interface LogEncoder {

	public void encode(LogEvent event, Buffer buffer);

	public static LogEncoder of(LogFormatter formatter) {
		return new FormatterEncoder(formatter);
	}

	public interface Buffer extends AutoCloseable {

		public StringBuilder stringBuilder();

		public void drain(LogOutput output, LogEvent event);

		public void clear();

		public void close();

	}

	public sealed interface BufferProvider {

		public Buffer provideBuffer();

		public void releaseBuffer(Buffer buffer);

		public static BufferProvider of() {
			return DefaultBufferProvider.INSTANT;
		}

	}

}

enum DefaultBufferProvider implements BufferProvider {

	INSTANT;

	@Override
	public Buffer provideBuffer() {
		return new DefaultBuffer(new StringBuilder());
	}

	@Override
	public void releaseBuffer(Buffer buffer) {

	}

}

class DefaultBuffer implements Buffer {

	private final StringBuilder stringBuilder;

	public DefaultBuffer(StringBuilder stringBuilder) {
		super();
		this.stringBuilder = stringBuilder;
	}

	@Override
	public StringBuilder stringBuilder() {
		return stringBuilder;
	}

	@Override
	public void drain(LogOutput output, LogEvent event) {
		output.write(event, stringBuilder.toString());
	}

	@Override
	public void clear() {
		stringBuilder.setLength(0);

	}

	@Override
	public void close() {
		clear();
	}

}

class FormatterEncoder implements LogEncoder {

	private final LogFormatter formatter;

	public FormatterEncoder(LogFormatter formatter) {
		super();
		this.formatter = formatter;
	}

	@Override
	public void encode(LogEvent event, Buffer buffer) {
		StringBuilder sb = buffer.stringBuilder();
		formatter.format(sb, event);
	}

}
