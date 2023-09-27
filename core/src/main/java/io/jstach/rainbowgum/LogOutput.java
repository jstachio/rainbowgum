package io.jstach.rainbowgum;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;

public interface LogOutput extends AutoCloseable, Flushable {

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

	public void flush();

	default OutputType type() {
		return OutputType.FILE;
	}

	public enum OutputType {

		CONSOLE_OUT, CONSOLE_ERR, FILE, NETWORK

	}

	public static LogOutput ofStandardOut() {
		// var out = FileDescriptor.out;
		// @SuppressWarnings("resource")
		// FileOutputStream fos = new FileOutputStream(out);
		// return new StdOutOutput(fos.getChannel(), fos);
		return new StdOutOutput();
	}

	public static LogOutput ofStandardErr() {
		var out = FileDescriptor.err;
		@SuppressWarnings("resource")
		FileOutputStream fos = new FileOutputStream(out);
		return new StdErrOutput(fos.getChannel(), fos);
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
			public void flush() {
				try {
					out.flush();
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
		return new FileChannelOutput(channel);
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

			// Clear any current interrupt (see LOGBACK-875)
			boolean interrupted = Thread.interrupted();

			FileLock fileLock = null;
			try {
				fileLock = channel.lock();
				long position = channel.position();
				long size = channel.size();
				if (size != position) {
					channel.position(size);
				}
				channel.write(buffer);

			}
			catch (IOException e) {
				LogRouter.error(FileChannelOutput.class, e);
			}
			finally {
				if (fileLock != null && fileLock.isValid()) {
					fileLock.release();
				}
				if (interrupted) {
					Thread.currentThread().interrupt();
				}
			}
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

	@Override
	public void flush() {
		try {
			channel.force(false);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}

abstract class Std extends FileChannelOutput implements LogOutput {

	private final OutputStream outputStream;

	public Std(FileChannel channel, OutputStream outputStream) {
		super(channel);
		this.outputStream = outputStream;
	}

	@Override
	public void write(LogEvent event, ByteBuffer buffer) {
		try {
			channel.write(buffer);
		}
		catch (ClosedByInterruptException e) {
			byte[] arr = new byte[buffer.position()];
			buffer.rewind();
			buffer.get(arr);
			try {
				outputStream.write(arr);
			}
			catch (IOException e1) {
				throw new UncheckedIOException(e1);
			}
		}
		catch (ClosedChannelException e) {
			byte[] arr = new byte[buffer.position()];
			buffer.rewind();
			buffer.get(arr);
			try {
				outputStream.write(arr);
			}
			catch (IOException e1) {
				throw new UncheckedIOException(e1);
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void close() {
		// try {
		// this.outputStream.close();
		// }
		// catch (IOException e) {
		// throw new UncheckedIOException(e);
		// }
	}

	@Override
	public void flush() {
		// try {
		// outputStream.flush();
		// }
		// catch (IOException e) {
		// throw new UncheckedIOException(e);
		// }
	}

}

// class StdOutOutput extends Std {
//
// public StdOutOutput(FileChannel channel, OutputStream outputStream) {
// super(channel, outputStream);
// }
//
// @Override
// public OutputType type() {
// return OutputType.CONSOLE_OUT;
// }
//
// }

abstract class OutputStreamOutput implements LogOutput {

	protected abstract OutputStream outputStream();

	@Override
	public void write(LogEvent event, byte[] bytes, int off, int len) {
		try {
			outputStream().write(bytes, off, len);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void flush() {
		try {
			outputStream().flush();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void close() {
		try {
			outputStream().close();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}

class StdOutOutput extends OutputStreamOutput {

	@Override
	protected OutputStream outputStream() {
		return System.out;
	}

	@Override
	public OutputType type() {
		return OutputType.CONSOLE_OUT;
	}

}

class StdErrOutput extends Std {

	public StdErrOutput(FileChannel channel, OutputStream outputStream) {
		super(channel, outputStream);
	}

	@Override
	public OutputType type() {
		return OutputType.CONSOLE_ERR;
	}

}