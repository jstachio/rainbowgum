package io.jstach.rainbowgum.nio;

import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import io.jstach.rainbowgum.LogEncoder.Buffer;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogOutput.ContentType.StandardContentType;

/**
 * A {@link Buffer} that formats into a reused {@link StringBuilder} (like the standard
 * formatter path) but encodes directly into a reused {@link ByteBuffer} on
 * {@linkplain #drain(LogOutput, LogEvent) drain} via a {@link CharsetEncoder}, instead of
 * going through an intermediate {@code String} and {@code byte[]} the way
 * {@code LogOutput.write(LogEvent, String)}'s default implementation does.
 * <p>
 * Not thread-safe. Like all {@link Buffer}s it relies on the appender to guarantee no
 * overlapping use.
 */
public final class DirectByteBufferBuffer implements Buffer {

	/**
	 * The buffer the formatter writes characters into.
	 */
	public final StringBuilder stringBuilder = new StringBuilder();

	private final CharsetEncoder charsetEncoder;

	private ByteBuffer byteBuffer;

	/**
	 * Creates a buffer with the given initial byte capacity and charset.
	 * @param initialByteCapacity initial capacity of the byte buffer. It will grow
	 * (doubling, or to whatever a single event needs if larger) as needed and the grown
	 * capacity is kept for subsequent events.
	 * @param charset charset to encode with.
	 */
	public DirectByteBufferBuffer(int initialByteCapacity, Charset charset) {
		this.byteBuffer = ByteBuffer.allocate(initialByteCapacity);
		this.charsetEncoder = charset.newEncoder()
			.onMalformedInput(CodingErrorAction.REPLACE)
			.onUnmappableCharacter(CodingErrorAction.REPLACE);
	}

	@Override
	public void drain(LogOutput output, LogEvent event) {
		encode();
		output.write(event, byteBuffer, StandardContentType.TEXT_PLAIN);
	}

	private void encode() {
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
		 * maxBytes above already sized the buffer to fit the worst case so flush should
		 * never overflow, but check defensively rather than silently truncate.
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
