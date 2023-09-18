package io.jstach.rainbowgum;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;

public non-sealed interface LogEncoder extends LogOutput {

	@Override
	default void append(String s) {
		encode(s.getBytes(StandardCharsets.UTF_8));
	}

	default void encode(byte[] bytes) {
		encode(bytes, 0, bytes.length);
	}

	public void encode(byte[] bytes, int off, int len);

	default void encode(ByteBuffer buf) {
		byte[] arr = new byte[buf.position()];
		buf.rewind();
		buf.get(arr);
		encode(arr);
	}

	public static LogEncoder of(PrintStream printStream) {
		return new LogEncoder() {

			@Override
			public void append(String s) {
				printStream.append(s);
			}

			@Override
			public void encode(byte[] bytes, int off, int len) {
				printStream.write(bytes, off, len);

			}
			
			@Override
			public void close() {
			}
		};
	}

	public static LogEncoder of(OutputStream out) {
		return new LogEncoder() {

			@Override
			public void encode(byte[] bytes, int off, int len) {
				try {
					out.write(bytes, off, len);
				}
				catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
			
			@Override
			public void close() {
				// TODO Auto-generated method stub
				
			}
		};
	}

	public static LogEncoder of(WritableByteChannel channel) {
		return new LogEncoder() {

			@Override
			public void encode(byte[] bytes, int off, int len) {
				encode(ByteBuffer.wrap(bytes, off, len));
			}

			@Override
			public void encode(ByteBuffer buffer) {
				try {
					channel.write(buffer);
				}
				catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
			
			@Override
			public void close() {
				
			}
		};
	}

}
