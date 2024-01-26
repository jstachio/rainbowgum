package io.jstach.rainbowgum;

import java.net.URI;

import io.jstach.rainbowgum.LogEncoder.AbstractEncoder;
import io.jstach.rainbowgum.LogEncoder.Buffer.StringBuilderBuffer;

/**
 * Encodes a {@link LogEvent} into a buffer of its choosing. While the {@link Buffer} does
 * not need to be threadsafe the encoder itself should be.
 * <p>
 * An appender typically calls an encoder by first {@linkplain #buffer() creating a
 * buffer} that the encoder knows about or reusing an existing {@link Buffer} the encoder
 * knows about.
 * <p>
 * The {@linkplain #encode(LogEvent, Buffer) encoding into a buffer} typically happens
 * outside of lock to minimize lock contention. Thus the appender promises not to share a
 * buffer at the same time with other threads as well as only use a buffer the log encoder
 * created at some point.
 * <p>
 * Once encoding is done the appender than typically enters into a lock (appenders
 * attached to an async publisher may not need to use a lock) where the appender will ask
 * the buffer to {@linkplain Buffer#drain(LogOutput, LogEvent) drain} its contents into
 * the output.
 * <p>
 * Because {@link Buffer} is not a specific implementation the Encoder typically casts the
 * buffer to the expected concrete implementation. {@link AbstractEncoder} can make this
 * logic easier and is recommended to extend it.
 * <p>
 * Given the complexity of encoders it is recommend use the much easier to implement
 * interface of {@link LogFormatter} and convert it to an encoder with
 * {@link #of(LogFormatter)}.
 *
 * @see LogFormatter
 * @see Buffer
 * @see LogAppender
 * @see StringBuilderBuffer
 */
public interface LogEncoder {

	/**
	 * Creates a <strong>new</strong> buffer. The encoder should not try to reuse buffers
	 * as that is the responsibility of the {@linkplain LogAppender appender}.
	 * @return a new buffer.
	 */
	public Buffer buffer();

	/**
	 * Encodes an event to the buffer. It is recommended that the encoder call
	 * {@link Buffer#clear()} before using.
	 * @param event log event.
	 * @param buffer buffer created from {@link #buffer()}.
	 */
	public void encode(LogEvent event, Buffer buffer);

	/**
	 * Creates an encoder from a formatter.
	 * @param formatter formatter.
	 * @return encoder.
	 */
	public static LogEncoder of(LogFormatter formatter) {
		return new FormatterEncoder(formatter);
	}

	/**
	 * Finds output based on URI.
	 */
	public interface EncoderProvider {

		/**
		 * Loads an encoder from a URI.
		 * @param uri uri.
		 * @param name name of encoder.
		 * @param properties key value config.
		 * @return output.
		 */
		LogEncoder provide(URI uri, String name, LogProperties properties);

	}

	/**
	 * Encoders buffer.
	 */
	public interface Buffer extends AutoCloseable {

		/**
		 * The appender will call this usually within a lock to transfer content from the
		 * buffer to the output.
		 * @param output output to receive content.
		 * @param event log event.
		 */
		public void drain(LogOutput output, LogEvent event);

		/**
		 * Prepare the buffer for reuse.
		 * <p>
		 * An appender may not call clear before being passed to the encoder so the
		 * encoder should do its own clearing.
		 */
		public void clear();

		/**
		 * Convenience that will call clear.
		 */
		default void close() {
			clear();
		}

		/**
		 * A buffer that simply wraps a {@link StringBuilder}. Direct access to the
		 * {@link StringBuilder} is available as the field {@link #stringBuilder}.
		 *
		 * @see AbstractEncoder
		 */
		public final class StringBuilderBuffer implements Buffer {

			/**
			 * Underlying StringBuilder.
			 */
			public final StringBuilder stringBuilder;

			/**
			 * Creates a StringBuilder based buffer.
			 * @param sb string builder.
			 * @return buffer.
			 */
			public static StringBuilderBuffer of(StringBuilder sb) {
				return new StringBuilderBuffer(sb);
			}

			private StringBuilderBuffer(StringBuilder stringBuilder) {
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

	/**
	 * Abstract encoder that will cast the buffer to the desired implementation. Extend to
	 * make creating encoders easier.
	 *
	 * @param <T> buffer type.
	 */
	abstract class AbstractEncoder<T extends Buffer> implements LogEncoder {

		/**
		 * Do nothing constructor.
		 */
		protected AbstractEncoder() {

		}

		/**
		 * Create a specific buffer implementation.
		 * @return
		 */
		protected abstract T doBuffer();

		/**
		 * A type safe version of {@link #encode(LogEvent, Buffer)}.
		 * @param event event.
		 * @param buffer casted buffer.
		 */
		protected abstract void doEncode(LogEvent event, T buffer);

		@Override
		public final Buffer buffer() {
			return doBuffer();
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void encode(LogEvent event, Buffer buffer) {
			doEncode(event, (T) buffer);

		}

	}

}

final class CachedEncoder implements LogEncoder {

	static LogEncoder of(LogEncoder encoder) {
		if (encoder instanceof CachedEncoder ce) {
			return ce;
		}
		return new CachedEncoder(encoder, encoder.buffer());
	}

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
		return StringBuilderBuffer.of(new StringBuilder());
	}

}
