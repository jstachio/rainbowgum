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
import io.jstach.rainbowgum.LogOutput.ContentType;
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
	 * Creates an encoder from a formatter that encodes with
	 * {@link StandardCharsets#UTF_8}.
	 * @param formatter formatter.
	 * @return encoder.
	 */
	public static LogEncoder of(LogFormatter formatter) {
		return of(formatter, StandardCharsets.UTF_8);
	}

	/**
	 * Creates an encoder from a formatter that encodes with the given charset and reports
	 * {@link StandardContentType#TEXT_PLAIN} with that charset to the output.
	 * {@link Buffer#isOversized()} is disabled - see
	 * {@link #of(LogFormatter, Charset, int)} to configure it.
	 * @param formatter formatter.
	 * @param charset charset to encode with. Only affects outputs whose
	 * {@link BufferHints#writeMethod()} is {@link WriteMethod#BYTES} or
	 * {@link WriteMethod#BYTE_BUFFER} - the {@link WriteMethod#STRING} path hands a plain
	 * {@link String} to {@link LogOutput#write(LogEvent, String)}, whose own default
	 * implementation is fixed to {@link StandardCharsets#UTF_8} regardless of this
	 * parameter, but no built-in output actually uses that write method.
	 * @return encoder.
	 */
	public static LogEncoder of(LogFormatter formatter, Charset charset) {
		return of(formatter, charset, -1);
	}

	/**
	 * Like {@link #of(LogFormatter, Charset)} but also configures a maximum buffer size -
	 * see {@link Buffer#isOversized()}. This, like charset, is a property of the
	 * encoder/buffer, not of whatever appender ends up reusing the buffer - an appender
	 * that reuses a buffer across events (e.g. a {@code ThreadLocal}-cached one) simply
	 * asks the buffer whether it has become oversized and discards/replaces it if so; it
	 * has no threshold of its own.
	 * @param formatter formatter.
	 * @param charset charset to encode with. See {@link #of(LogFormatter, Charset)}.
	 * @param maxSize a negative value disables {@link Buffer#isOversized()} entirely (the
	 * same behavior as {@link #of(LogFormatter, Charset)}).
	 * @return encoder.
	 */
	public static LogEncoder of(LogFormatter formatter, Charset charset, int maxSize) {
		return of(formatter, charset, ContentType.of(StandardContentType.TEXT_PLAIN.contentType(), charset), maxSize);
	}

	/**
	 * Creates an encoder from a formatter that reports the given content type to the
	 * output, encoding with {@link ContentType#charsetOrNull() its charset} if specified,
	 * otherwise {@link StandardCharsets#UTF_8}. Use this instead of
	 * {@link #of(LogFormatter, Charset)} when the formatter produces something other than
	 * plain text (e.g. a custom {@link ContentType.DefaultContentType}).
	 * {@link Buffer#isOversized()} is disabled - see
	 * {@link #of(LogFormatter, ContentType, int)} to configure it.
	 * @param formatter formatter.
	 * @param contentType content type reported to the output. See
	 * {@link #of(LogFormatter, Charset)} for the effect of its charset.
	 * @return encoder.
	 */
	public static LogEncoder of(LogFormatter formatter, ContentType contentType) {
		return of(formatter, contentType, -1);
	}

	/**
	 * Like {@link #of(LogFormatter, ContentType)} but also configures a maximum buffer
	 * size - see {@link #of(LogFormatter, Charset, int)}.
	 * @param formatter formatter.
	 * @param contentType content type reported to the output.
	 * @param maxSize a negative value disables {@link Buffer#isOversized()} entirely.
	 * @return encoder.
	 */
	public static LogEncoder of(LogFormatter formatter, ContentType contentType, int maxSize) {
		var charset = contentType.charsetOrNull();
		return of(formatter, charset == null ? StandardCharsets.UTF_8 : charset, contentType, maxSize);
	}

	private static LogEncoder of(LogFormatter formatter, Charset charset, ContentType contentType, int maxSize) {
		return new FormatterEncoder(formatter, charset, contentType, maxSize);
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
		 * <p>
		 * Built-in implementations also shrink their own backing storage back down here
		 * if {@link #isOversized()} - see that method - so a single unusually large event
		 * does not permanently bloat a buffer that gets reused for many more events after
		 * it.
		 */
		public void clear();

		/**
		 * Whether this buffer has grown large enough (its capacity, not how much of that
		 * capacity the last event actually used) that its backing storage is worth
		 * shrinking back down rather than kept at its grown size indefinitely - reused
		 * buffers only grow on their own, they never shrink back down without deliberate
		 * help. Built-in implementations consult this themselves inside {@link #clear()}
		 * to decide whether to shrink their own backing storage in place (e.g.
		 * {@link StringBuilder#trimToSize()}, or reallocating a smaller
		 * {@link java.nio.ByteBuffer}) - most callers do not need to call this directly;
		 * it remains here mainly for tests/observability and for a custom {@link Buffer}
		 * implementation that wants the same self-shrinking behavior.
		 * <p>
		 * What "large enough" means, and whether it means anything at all, is entirely up
		 * to the buffer/encoder - the same way charset is an encoder concern rather than
		 * something an appender configures (see
		 * {@link LogEncoder#of(LogFormatter, java.nio.charset.Charset)}). The default
		 * implementation returns {@code false} - a buffer implementation that does not
		 * override this, or an encoder that never configured a threshold, never shrinks.
		 * @return {@code true} if this buffer's backing storage has grown past whatever
		 * threshold it was configured with.
		 */
		default boolean isOversized() {
			return false;
		}

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

			private final int maxSize;

			/**
			 * Creates a StringBuilder based buffer that never reports
			 * {@link #isOversized()}.
			 * @param sb string builder.
			 * @return buffer.
			 */
			public static StringBuilderBuffer of(StringBuilder sb) {
				return of(sb, -1);
			}

			/**
			 * Creates a StringBuilder based buffer that reports {@link #isOversized()}
			 * once {@code sb}'s capacity exceeds {@code maxSize}.
			 * @param sb string builder.
			 * @param maxSize a negative value disables {@link #isOversized()} entirely
			 * (always {@code false}).
			 * @return buffer.
			 */
			public static StringBuilderBuffer of(StringBuilder sb, int maxSize) {
				return new StringBuilderBuffer(sb, maxSize);
			}

			private StringBuilderBuffer(StringBuilder stringBuilder, int maxSize) {
				super();
				this.stringBuilder = stringBuilder;
				this.maxSize = maxSize;
			}

			@Override
			public void drain(LogOutput output, LogEvent event) {
				output.write(event, stringBuilder.toString());
			}

			@Override
			public void clear() {
				stringBuilder.setLength(0);
				// setLength(0) never touches capacity, so isOversized() here still
				// reflects growth from whatever was just written - trimToSize()
				// mutates the StringBuilder in place, no reassignment needed (works
				// fine through the public final field above).
				if (isOversized()) {
					stringBuilder.trimToSize();
				}
			}

			@Override
			public boolean isOversized() {
				return maxSize >= 0 && stringBuilder.capacity() > maxSize;
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

			private final LogOutput.ContentType contentType;

			private final int maxSize;

			private final int initialByteCapacity;

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
				this(writeMethod, initialByteCapacity, charset,
						LogOutput.ContentType.of(StandardContentType.TEXT_PLAIN.contentType(), charset));
			}

			/**
			 * Creates a buffer with the given write method, initial byte capacity,
			 * charset and content type to report to the output. {@link #isOversized()} is
			 * disabled (always {@code false}).
			 * @param writeMethod which {@link LogOutput#write} overload {@link #drain}
			 * should call - {@link LogOutput.WriteMethod#BYTES} or
			 * {@link LogOutput.WriteMethod#BYTE_BUFFER}.
			 * @param initialByteCapacity initial capacity of the byte buffer. It will
			 * grow (doubling, or to whatever a single event needs if larger) as needed
			 * and the grown capacity is kept for subsequent events.
			 * @param charset charset to encode with.
			 * @param contentType content type reported to {@link LogOutput#write}. Its
			 * {@link LogOutput.ContentType#charsetOrNull() charset}, if specified, should
			 * normally match {@code charset}.
			 */
			public DirectByteBufferBuffer(LogOutput.WriteMethod writeMethod, int initialByteCapacity, Charset charset,
					LogOutput.ContentType contentType) {
				this(writeMethod, initialByteCapacity, charset, contentType, -1);
			}

			/**
			 * Creates a buffer with the given write method, initial byte capacity,
			 * charset, content type to report to the output, and maximum combined size
			 * (see {@link #isOversized()}).
			 * @param writeMethod which {@link LogOutput#write} overload {@link #drain}
			 * should call - {@link LogOutput.WriteMethod#BYTES} or
			 * {@link LogOutput.WriteMethod#BYTE_BUFFER}.
			 * @param initialByteCapacity initial capacity of the byte buffer. It will
			 * grow (doubling, or to whatever a single event needs if larger) as needed
			 * and the grown capacity is kept for subsequent events.
			 * @param charset charset to encode with.
			 * @param contentType content type reported to {@link LogOutput#write}. Its
			 * {@link LogOutput.ContentType#charsetOrNull() charset}, if specified, should
			 * normally match {@code charset}.
			 * @param maxSize a negative value disables {@link #isOversized()} entirely
			 * (always {@code false}). Since {@code initialByteCapacity} (commonly
			 * {@value #DEFAULT_INITIAL_BYTE_CAPACITY}) is allocated up front and counted
			 * by {@link #isOversized()} regardless of how much of it any event has
			 * actually used, a {@code maxSize} at or below {@code initialByteCapacity}
			 * makes {@link #isOversized()} unconditionally {@code true} from the very
			 * first event - {@code maxSize} should normally be set well above whatever
			 * initial capacity is in play.
			 */
			public DirectByteBufferBuffer(LogOutput.WriteMethod writeMethod, int initialByteCapacity, Charset charset,
					LogOutput.ContentType contentType, int maxSize) {
				this.writeMethod = writeMethod;
				this.byteBuffer = ByteBuffer.allocate(initialByteCapacity);
				this.charsetEncoder = charset.newEncoder()
					.onMalformedInput(CodingErrorAction.REPLACE)
					.onUnmappableCharacter(CodingErrorAction.REPLACE);
				this.contentType = contentType;
				this.maxSize = maxSize;
				this.initialByteCapacity = initialByteCapacity;
			}

			/**
			 * Writes the already-encoded bytes to the output. Assumes
			 * {@link #encodeToByteBuffer()} has already been called for this event - see
			 * the class doc for why that step lives in the encoder instead of here.
			 */
			@Override
			public void drain(LogOutput output, LogEvent event) {
				switch (writeMethod) {
					case BYTE_BUFFER -> output.write(event, byteBuffer, contentType);
					case BYTES -> output.write(event, byteBuffer.array(),
							byteBuffer.arrayOffset() + byteBuffer.position(), byteBuffer.remaining(), contentType);
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
				// Checked before byteBuffer is touched below, so this still reflects
				// growth from whatever was just drained. stringBuilder.trimToSize()
				// mutates in place; byteBuffer isn't final (it's already reassigned
				// during growth in encodeToByteBuffer()) so reallocating it back down
				// here needs no new field-mutability changes either.
				if (isOversized()) {
					stringBuilder.trimToSize();
					byteBuffer = ByteBuffer.allocate(initialByteCapacity);
				}
			}

			@Override
			public boolean isOversized() {
				return maxSize >= 0 && (stringBuilder.capacity() + byteBuffer.capacity()) > maxSize;
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

	private final Charset charset;

	private final ContentType contentType;

	private final int maxSize;

	public FormatterEncoder(LogFormatter formatter, Charset charset, ContentType contentType, int maxSize) {
		super();
		this.formatter = formatter;
		this.charset = charset;
		this.contentType = contentType;
		this.maxSize = maxSize;
	}

	@Override
	public Buffer buffer(BufferHints hints) {
		return switch (hints.writeMethod()) {
			case STRING -> StringBuilderBuffer.of(new StringBuilder(), maxSize);
			case BYTES, BYTE_BUFFER -> new DirectByteBufferBuffer(hints.writeMethod(),
					DirectByteBufferBuffer.DEFAULT_INITIAL_BYTE_CAPACITY, charset, contentType, maxSize);
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
