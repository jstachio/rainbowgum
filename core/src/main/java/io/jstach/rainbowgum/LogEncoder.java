package io.jstach.rainbowgum;

import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import io.jstach.rainbowgum.LogEncoder.Buffer.DirectByteBufferBuffer;
import io.jstach.rainbowgum.LogEncoder.Buffer.StringBuilderBuffer;
import io.jstach.rainbowgum.LogOutput.ContentType.StandardContentType;
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

		/**
		 * A buffer that formats into a reused {@link StringBuilder} (like
		 * {@link StringBuilderBuffer}) but encodes directly into a reused
		 * {@link ByteBuffer} via a {@link CharsetEncoder}, instead of going through an
		 * intermediate {@code String} and {@code byte[]} the way
		 * {@code LogOutput.write(LogEvent, String)}'s default implementation does. This
		 * is the same technique
		 * <a href="https://logging.apache.org/log4j/2.x/manual/garbagefree.html">Log4j2's
		 * garbage-free logging</a> uses.
		 * <p>
		 * The {@link CharsetEncoder} work happens in {@link #encodeToByteBuffer()},
		 * called by the encoder's {@code encode(LogEvent, Buffer)} step - i.e. before
		 * {@link #drain(LogOutput, LogEvent) drain}, which appenders that separate
		 * formatting from writing ({@code LockThreadLocalBufferLogAppender},
		 * {@code SynchronizedThreadLocalBufferLogAppender}) call outside their lock.
		 * {@code drain} - called from inside the lock - therefore does nothing but write
		 * the already-encoded bytes, matching how Log4j2 confines the actual
		 * character-to-byte transcoding to a thread-local scratch buffer and synchronizes
		 * only the final copy into the shared destination.
		 * <p>
		 * Constructed for a specific {@link LogOutput.WriteMethod} - either
		 * {@link LogOutput.WriteMethod#BYTE_BUFFER}, in which case {@link #drain} calls
		 * {@link LogOutput#write(LogEvent, ByteBuffer, LogOutput.ContentType)} directly,
		 * or {@link LogOutput.WriteMethod#BYTES}, in which case {@link #drain} calls
		 * {@link LogOutput#write(LogEvent, byte[], int, int, LogOutput.ContentType)}
		 * using the backing array directly, rather than going through
		 * {@code LogOutput#write(LogEvent, ByteBuffer, ContentType)}'s default
		 * implementation (which would allocate a fresh {@code byte[]} copy every call for
		 * an output that does not itself implement that overload).
		 * <p>
		 * Not thread-safe. Like all {@link Buffer}s it relies on the appender to
		 * guarantee no overlapping use.
		 */
		public final class DirectByteBufferBuffer implements Buffer {

			/**
			 * Default initial byte buffer capacity, matching Log4j2's own
			 * {@code log4j2.encoderByteBufferSize} default.
			 */
			public static final int DEFAULT_INITIAL_BYTE_CAPACITY = 8192;

			/**
			 * The buffer the formatter writes characters into.
			 */
			public final StringBuilder stringBuilder = new StringBuilder();

			private final CharsetEncoder charsetEncoder;

			private final LogOutput.WriteMethod writeMethod;

			private ByteBuffer byteBuffer;

			/**
			 * Creates a buffer with the default initial byte capacity and
			 * {@link StandardCharsets#UTF_8}.
			 * @param writeMethod which {@link LogOutput#write} overload {@link #drain}
			 * should call - {@link LogOutput.WriteMethod#BYTES} or
			 * {@link LogOutput.WriteMethod#BYTE_BUFFER}.
			 */
			public DirectByteBufferBuffer(LogOutput.WriteMethod writeMethod) {
				this(writeMethod, DEFAULT_INITIAL_BYTE_CAPACITY, StandardCharsets.UTF_8);
			}

			/**
			 * Creates a buffer with the given write method, initial byte capacity and
			 * charset.
			 * @param writeMethod which {@link LogOutput#write} overload {@link #drain}
			 * should call - {@link LogOutput.WriteMethod#BYTES} or
			 * {@link LogOutput.WriteMethod#BYTE_BUFFER}.
			 * @param initialByteCapacity initial capacity of the byte buffer. It will
			 * grow (doubling, or to whatever a single event needs if larger) as needed
			 * and the grown capacity is kept for subsequent events.
			 * @param charset charset to encode with.
			 */
			public DirectByteBufferBuffer(LogOutput.WriteMethod writeMethod, int initialByteCapacity, Charset charset) {
				this.writeMethod = writeMethod;
				this.byteBuffer = ByteBuffer.allocate(initialByteCapacity);
				this.charsetEncoder = charset.newEncoder()
					.onMalformedInput(CodingErrorAction.REPLACE)
					.onUnmappableCharacter(CodingErrorAction.REPLACE);
			}

			/**
			 * Writes the already-encoded bytes to the output. Assumes
			 * {@link #encodeToByteBuffer()} has already been called for this event - see
			 * the class doc for why that step lives in the encoder instead of here.
			 */
			@Override
			public void drain(LogOutput output, LogEvent event) {
				switch (writeMethod) {
					case BYTE_BUFFER -> output.write(event, byteBuffer, StandardContentType.TEXT_PLAIN);
					case BYTES ->
						output.write(event, byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(),
								byteBuffer.remaining(), StandardContentType.TEXT_PLAIN);
					case STRING ->
						throw new IllegalStateException("DirectByteBufferBuffer does not support WriteMethod.STRING");
				}
			}

			/**
			 * Encodes the current contents of {@link #stringBuilder} into the reused
			 * {@link ByteBuffer}, growing it first if needed. Deliberately not called
			 * from {@link #drain(LogOutput, LogEvent)} - see the class doc.
			 */
			void encodeToByteBuffer() {
				int maxBytes = (int) Math.ceil(stringBuilder.length() * (double) charsetEncoder.maxBytesPerChar());
				if (byteBuffer.capacity() < maxBytes) {
					byteBuffer = ByteBuffer.allocate(Math.max(maxBytes, byteBuffer.capacity() * 2));
				}
				byteBuffer.clear();
				charsetEncoder.reset();
				CharBuffer cb = CharBuffer.wrap(stringBuilder);
				var result = charsetEncoder.encode(cb, byteBuffer, true);
				if (result.isError()) {
					try {
						result.throwException();
					}
					catch (CharacterCodingException e) {
						throw new UncheckedIOException(e);
					}
				}
				/*
				 * maxBytes above already sized the buffer to fit the worst case so flush
				 * should never overflow, but check defensively rather than silently
				 * truncate.
				 */
				var flushResult = charsetEncoder.flush(byteBuffer);
				if (flushResult.isOverflow()) {
					throw new IllegalStateException("CharsetEncoder flush overflowed despite pre-sized buffer");
				}
				byteBuffer.flip();
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

/*
 * Not an AbstractEncoder since it needs to hand out either a StringBuilderBuffer or a
 * DirectByteBufferBuffer depending on the output's WriteMethod hint - see buffer(hints).
 */
final class FormatterEncoder implements LogEncoder {

	private final LogFormatter formatter;

	public FormatterEncoder(LogFormatter formatter) {
		super();
		this.formatter = formatter;
	}

	@Override
	public Buffer buffer(BufferHints hints) {
		return switch (hints.writeMethod()) {
			case STRING -> StringBuilderBuffer.of(new StringBuilder());
			case BYTES, BYTE_BUFFER -> new DirectByteBufferBuffer(hints.writeMethod());
		};
	}

	@Override
	public void encode(LogEvent event, Buffer buffer) {
		switch (buffer) {
			case StringBuilderBuffer sb -> {
				sb.clear();
				formatter.format(sb.stringBuilder, event);
			}
			case DirectByteBufferBuffer bb -> {
				bb.clear();
				formatter.format(bb.stringBuilder, event);
				bb.encodeToByteBuffer();
			}
			default -> throw new IllegalStateException("Unsupported buffer: " + buffer.getClass());
		}
	}

}
