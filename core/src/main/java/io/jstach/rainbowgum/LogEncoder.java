package io.jstach.rainbowgum;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogEncoder.Buffer;
import io.jstach.rainbowgum.LogEncoder.BufferProvider;
import io.jstach.rainbowgum.LogEncoder.BufferWriter;

/**
 * Log encoders must be thread safe.
 */
@SuppressWarnings("exports")
public interface LogEncoder {

	public void encode(LogEvent event, Buffer buffer);

	public static LogEncoder of(LogFormatter formatter) {
		return new FormatterEncoder(formatter);
	}

	public interface Buffer extends AutoCloseable {

		public StringBuilder stringBuilder();
		
		public <T> @Nullable T get();
		
		public <T> void store(T object, BufferWriter<T> writer);
		
		public void drain(LogOutput output, LogEvent event);

		public void clear();

		public void close();

	}
	
	public interface BufferWriter<T> {
		public void write(LogOutput output, LogEvent event, T buffer);
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
	private @Nullable Object custom;
	@SuppressWarnings("rawtypes")
	private @Nullable BufferWriter writer;

	public DefaultBuffer(StringBuilder stringBuilder) {
		super();
		this.stringBuilder = stringBuilder;
	}

	@Override
	public StringBuilder stringBuilder() {
		return stringBuilder;
	}
	
	@SuppressWarnings("unchecked")
	public <T> @Nullable T get() {
		return (T) custom;
	}
	
	public <T> void store(T object, LogEncoder.BufferWriter<T> writer) {
		this.custom = object;
		this.writer = writer;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void drain(LogOutput output, LogEvent event) {
		var w = this.writer;
		if (w == null) {
			writer.write(output, event, this.custom);
		} 
		else {
			output.write(event, stringBuilder.toString());
		}
		clear();
	}

	@Override
	public void clear() {
		stringBuilder.setLength(0);
		writer = null;
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
