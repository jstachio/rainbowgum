package io.jstach.rainbowgum;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public interface LogOutput extends AutoCloseable {

	default void write(LogEvent event, String s) {
		write(event, s.getBytes(StandardCharsets.UTF_8));
	}

	default void write(LogEvent event, byte[] bytes) {
		write(event, bytes, 0, bytes.length);
	}

	public void write(LogEvent event, byte[] bytes, int off, int len);

	default void write(LogEvent event, ByteBuffer buf) {
		byte[] arr = new byte[buf.position()];
		buf.rewind();
		buf.get(arr);
		write(event, arr);
	}

	default OutputType type() {
		return OutputType.FILE;
	}

	public enum OutputType {

		CONSOLE_OUT, CONSOLE_ERR, FILE, NETWORK

	}

	public static LogOutput ofStandardOut() {
		var out = FileDescriptor.out;
		@SuppressWarnings("resource")
		FileOutputStream fos = new FileOutputStream(out);
		return new StdOutOutput(fos.getChannel());
	}

	public static LogOutput ofStandardErr() {
		var out = FileDescriptor.err;
		@SuppressWarnings("resource")
		FileOutputStream fos = new FileOutputStream(out);
		return new StdErrOutput(fos.getChannel());
	}

	public static LogOutput of(OutputStream out) {
		return new LogOutput() {

			@Override
			public void write(LogEvent event, byte[] bytes, int off, int len) {
				try {
					out.write(bytes, off, len);
				}
				catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}

			@Override
			public void close() {
				try {
					out.close();
				}
				catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
		};
	}

	public static LogOutput of(FileChannel channel) {
		return new LogOutput() {

			@Override
			public void write(LogEvent event, byte[] bytes, int off, int len) {
				write(event, ByteBuffer.wrap(bytes, off, len));
			}

			@Override
			public void write(LogEvent event, ByteBuffer buffer) {
				try {
					channel.write(buffer);
				}
				catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}

			@Override
			public void close() {
				try {
					channel.close();
				}
				catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
		};
	}

	void close();

}

class FileChannelOutput implements LogOutput {

	protected final FileChannel channel;

	public FileChannelOutput(FileChannel channel) {
		super();
		this.channel = channel;
	}

	@Override
	public void write(LogEvent event, byte[] bytes, int off, int len) {
		write(event, ByteBuffer.wrap(bytes, off, len));
	}

	@Override
	public void write(LogEvent event, ByteBuffer buffer) {
		try {
			channel.write(buffer);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void close() {
		try {
			channel.close();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}

class StdOutOutput extends FileChannelOutput implements LogOutput {

	public StdOutOutput(FileChannel channel) {
		super(channel);
	}

	@Override
	public OutputType type() {
		return OutputType.CONSOLE_OUT;
	}

	@Override
	public void close() {
	}

}

class StdErrOutput extends FileChannelOutput implements LogOutput {

	public StdErrOutput(FileChannel channel) {
		super(channel);
	}

	@Override
	public OutputType type() {
		return OutputType.CONSOLE_ERR;
	}

	@Override
	public void close() {
	}

}