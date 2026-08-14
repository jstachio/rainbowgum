package io.jstach.rainbowgum;

import java.io.UncheckedIOException;
import java.net.URI;

import io.jstach.rainbowgum.LogEncoder.AbstractEncoder;
import io.jstach.rainbowgum.LogEncoder.Buffer.StringBuilderBuffer;
import io.jstach.rainbowgum.LogOutput.WriteMethod;
import io.jstach.rainbowgum.format.AbstractStandardEventFormatter;

/**
 * Encodes a {@link LogEvent} into a buffer of its choosing. While the {@link Buffer} does
 * not need to be thread-safe the encoder itself should be.
 * <p>
 * An appender typically calls an encoder by first {@linkplain #buffer(BufferHints)
 * creating a buffer} that the encoder knows about or reusing an existing {@link Buffer}
 * the encoder knows about.
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
	 * as that is the responsibility of the {@linkplain LogAppender appender} (and
	 * possibly {@link LogOutput} but usually not). Hints can be retrieved by call
	 * {@link LogOutput#bufferHints()}.
	 * @param hints hints are like size and storage type etc.
	 * @return a new buffer.
	 * @apiNote hints can be retrieved by calling {@link LogOutput#bufferHints()} the
	 * reason the output itself is not passed is to prevent the buffer from using the
	 * output directly at an inappropriate time as well as the rare possibility of the
	 * buffer being used by multiple outputs.
	 */
	public Buffer buffer(BufferHints hints);

	/**
	 * Encodes an event to the buffer. It is recommended that the encoder call
	 * {@link Buffer#clear()} before using.
	 * @param event log event.
	 * @param buffer buffer created from {@link #buffer(BufferHints)}.
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
	 * Provides a lazy loaded encoder from a URI.
	 * @param uri uri.
	 * @return provider of encoder.
	 */
	public static LogProvider<LogEncoder> of(URI uri) {
		return of(LogProviderRef.of(uri));
	}

	/**
	 * Provides the standard TTLL encoder.
	 * @return provider of encoder.
	 */
	public static LogProvider<LogEncoder> ofTTLL() {
		return of(LogProviderRef.of(URI.create(AbstractStandardEventFormatter.SCHEMA)));
	}

	/**
	 * Provides a lazy loaded encoder from a provider ref.
	 * @param ref uri.
	 * @return provider of output.
	 * @apiNote the provider may throw an {@link UncheckedIOException}.
	 */
	public static LogProvider<LogEncoder> of(LogProviderRef ref) {
		return (s, c) -> {
			return c.encoderRegistry().provide(ref).provide(s, c);
		};
	}

	/**
	 * Finds output based on URI.
	 */
	public interface EncoderProvider {

		/**
		 * Loads an encoder from a URI.
		 * @param ref reference to provider usually just a uri.
		 * @return output.
		 * @throws LogProviderRef.NotFoundException if there is no registered provider.
		 */
		LogProvider<LogEncoder> provide(LogProviderRef ref) throws LogProviderRef.NotFoundException;

		/**
		 * Convenience method to register encoders that require no configuration.
		 * @param encoder already configured encoder.
		 * @return encoder provider.
		 */
		static EncoderProvider of(LogEncoder encoder) {
			return ref -> LogProvider.of(encoder);
		}

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
		@Override
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
	 * Hints the output can pass to the encoder for creating buffers like max size and
	 * storage style of the buffer etc.
	 *
	 * @apiNote There is no guarantees the encoder/buffer will honor these hints.
	 */
	public interface BufferHints {

		/*
		 * TODO should we seal this?
		 */

		/**
		 * The preferred write style of the output.
		 * @return write method.
		 * @apiNote {@link WriteMethod} implements this interface for convenience.
		 */
		LogOutput.WriteMethod writeMethod();

		/**
		 * Maximum size of the buffer. This is a way for the encoder to say it can only
		 * handle so much data per event.
		 * @return a negative number indicates size is not important.
		 */
		default int maximumSize() {
			return -1;
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
		 * @param hints buffer creation hints.
		 * @return buffer
		 */
		protected abstract T doBuffer(BufferHints hints);

		/**
		 * A type safe version of {@link #encode(LogEvent, Buffer)}.
		 * @param event event.
		 * @param buffer casted buffer.
		 */
		protected abstract void doEncode(LogEvent event, T buffer);

		@Override
		public final Buffer buffer(BufferHints hints) {
			return doBuffer(hints);
		}

		@SuppressWarnings("unchecked")
		@Override
		public final void encode(LogEvent event, Buffer buffer) {
			doEncode(event, (T) buffer);

		}

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
	protected StringBuilderBuffer doBuffer(BufferHints hints) {
		return StringBuilderBuffer.of(new StringBuilder());
	}

}
