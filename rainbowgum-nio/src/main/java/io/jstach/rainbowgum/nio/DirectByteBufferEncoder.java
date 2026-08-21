package io.jstach.rainbowgum.nio;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEncoder.AbstractEncoder;
import io.jstach.rainbowgum.LogEncoder.BufferHints;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;

/**
 * <strong>EXPERIMENTAL</strong> encoder that avoids the {@code String}/{@code byte[]}
 * allocation the default {@code LogEncoder} path does per event by encoding a
 * {@link LogFormatter}'s output directly into a reused {@link java.nio.ByteBuffer}. This
 * is the same technique
 * <a href="https://logging.apache.org/log4j/2.x/manual/garbagefree.html">Log4j2's
 * garbage-free logging</a> uses.
 * <p>
 * Pair this with {@link io.jstach.rainbowgum.LogAppender.AppenderFlag#REUSE_BUFFER} (e.g.
 * via {@code logging.appender.<name>.flags=reuse_buffer}) to get the full benefit - the
 * buffer (and its grown byte capacity) is only reused across events if the appender keeps
 * one instance around instead of creating a fresh one per event.
 */
public final class DirectByteBufferEncoder extends AbstractEncoder<DirectByteBufferBuffer> {

	/**
	 * Default initial byte buffer capacity, matching Log4j2's own
	 * {@code log4j2.encoderByteBufferSize} default.
	 */
	public static final int DEFAULT_INITIAL_BYTE_CAPACITY = 8192;

	private final LogFormatter formatter;

	private final int initialByteCapacity;

	private final Charset charset;

	/**
	 * Creates an encoder wrapping a formatter, using the default initial byte capacity
	 * and {@link StandardCharsets#UTF_8}.
	 * @param formatter formatter.
	 */
	public DirectByteBufferEncoder(LogFormatter formatter) {
		this(formatter, DEFAULT_INITIAL_BYTE_CAPACITY, StandardCharsets.UTF_8);
	}

	/**
	 * Creates an encoder wrapping a formatter.
	 * @param formatter formatter.
	 * @param initialByteCapacity initial byte buffer capacity - see
	 * {@link DirectByteBufferBuffer#DirectByteBufferBuffer(int, Charset)}.
	 * @param charset charset to encode with.
	 */
	public DirectByteBufferEncoder(LogFormatter formatter, int initialByteCapacity, Charset charset) {
		super();
		this.formatter = formatter;
		this.initialByteCapacity = initialByteCapacity;
		this.charset = charset;
	}

	/**
	 * Creates an encoder provider from a formatter, for use with
	 * {@code LogAppender.Builder#encoder(LogEncoder)}.
	 * @param formatter formatter.
	 * @return encoder.
	 */
	public static LogEncoder of(LogFormatter formatter) {
		return new DirectByteBufferEncoder(formatter);
	}

	@Override
	protected void doEncode(LogEvent event, DirectByteBufferBuffer buffer) {
		buffer.clear();
		formatter.format(buffer.stringBuilder, event);
	}

	@Override
	protected DirectByteBufferBuffer doBuffer(BufferHints hints) {
		return new DirectByteBufferBuffer(initialByteCapacity, charset);
	}

}
