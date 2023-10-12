package io.jstach.rainbowgum;

import io.jstach.rainbowgum.LogEncoder.AbstractEncoder;
import io.jstach.rainbowgum.LogEncoder.Buffer.StringBuilderBuffer;

/**
 * Log encoders must be thread safe.
 */
public interface LogEncoder {

	public Buffer buffer();

	public void encode(LogEvent event, Buffer buffer);

	public static LogEncoder of(LogFormatter formatter) {
		return new FormatterEncoder(formatter);
	}

	static LogEncoder cached(LogEncoder encoder) {
		if (encoder instanceof CachedEncoder ce) {
			return ce;
		}
		return new CachedEncoder(encoder, encoder.buffer());
	}

	public interface Buffer extends AutoCloseable {

		public void drain(LogOutput output, LogEvent event);

		public void clear();

		default void close() {
			clear();
		}

		public class StringBuilderBuffer implements Buffer {

			public final StringBuilder stringBuilder;

			public StringBuilderBuffer(StringBuilder stringBuilder) {
				super();
				this.stringBuilder = stringBuilder;
			}

			@Override
			public void drain(LogOutput output, LogEvent event) {
				output.write(event, stringBuilder.toString());
			}

			@Override
			public void clear() {
				stringBuilder.setLength(0);
			}

		}

	}

	abstract class AbstractEncoder<T extends Buffer> implements LogEncoder {

		protected abstract T doBuffer();

		protected abstract void doEncode(LogEvent event, T buffer);

		@Override
		public Buffer buffer() {
			return doBuffer();
		}

		@SuppressWarnings("unchecked")
		@Override
		public void encode(LogEvent event, Buffer buffer) {
			doEncode(event, (T) buffer);

		}

	}

}

final class CachedEncoder implements LogEncoder {

	private final LogEncoder encoder;

	private final Buffer buffer;

	public CachedEncoder(LogEncoder encoder, Buffer buffer) {
		super();
		this.encoder = encoder;
		this.buffer = buffer;
	}

	@Override
	public Buffer buffer() {
		return this.buffer;
	}

	@Override
	public void encode(LogEvent event, Buffer buffer) {
		encoder.encode(event, buffer);
	}

}

final class FormatterEncoder extends AbstractEncoder<StringBuilderBuffer> {

	private final LogFormatter formatter;

	public FormatterEncoder(LogFormatter formatter) {
		super();
		this.formatter = formatter;
	}

	@Override
	protected void doEncode(LogEvent event, StringBuilderBuffer buffer) {
		buffer.clear();
		StringBuilder sb = buffer.stringBuilder;
		formatter.format(sb, event);
	}

	@Override
	protected StringBuilderBuffer doBuffer() {
		return new StringBuilderBuffer(new StringBuilder());
	}

}
