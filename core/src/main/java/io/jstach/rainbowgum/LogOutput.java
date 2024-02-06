package io.jstach.rainbowgum;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;

import io.jstach.rainbowgum.LogEncoder.Buffer;
import io.jstach.rainbowgum.LogOutput.ThreadSafeLogOutput;

/**
 * A resource that can be written to usually in binary. In other logging frameworks this
 * is often called an "Appender" not be confused with RainbowGum's appenders.
 * <p>
 * To simplyfy flushing and corruption prevention each call to the write methods MUST be
 * the contents of the entire event!
 * <p>
 * The write methods do not throw {@link IOException} on purpose as it is implementations
 * responsibility to handle errors and to be resilient on their own.
 *
 * @see LogOutput.OutputProvider
 * @see Buffer
 */
public interface LogOutput extends LogLifecycle, Flushable {

	/**
	 * {@link FileDescriptor#err} URI scheme.
	 */
	static final String STDERR_SCHEME = "stderr";

	/**
	 * {@link FileDescriptor#out} URI scheme.
	 */
	static final String STDOUT_SCHEME = "stdout";

	/**
	 * File URI scheme.
	 */
	static final String FILE_SCHEME = "file";

	/**
	 * Standard OUT URI.
	 */
	static final URI STDOUT_URI = uri(STDOUT_SCHEME + ":///");

	/**
	 * Standard ERR URI
	 */
	static final URI STDERR_URI = uri(STDERR_SCHEME + ":///");

	/*
	 * This is because URI create is nullable.
	 */
	private static URI uri(String s) {
		try {
			return new URI(s);
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Finds output based on URI.
	 */
	public interface OutputProvider extends PluginProvider<LogOutput, IOException> {

		/**
		 * Loads an output from a URI.
		 * @param uri uri.
		 * @param name name of output.
		 * @param properties key value config.
		 * @return output.
		 * @throws IOException if unable to use the URI.
		 */
		LogOutput provide(URI uri, String name, LogProperties properties) throws IOException;

	}

	/**
	 * The content type of the binary data passed to the output from an encoder.
	 */
	public interface ContentType {

		/**
		 * Content type of binary datay passed to output.
		 * @return content type.
		 */
		String contentType();

		/**
		 * Builtin content types.
		 */
		public enum StandardContentType implements ContentType {

			/**
			 * text/plain
			 */
			TEXT_PLAIN() {
				@Override
				public String contentType() {
					return "text/plain";
				}
			},
			/**
			 * application/json
			 */
			APPLICATION_JSON() {
				@Override
				public String contentType() {
					return "application/json";
				}
			}

		}

	}

	/**
	 * The uri of the output.
	 * @return uri.
	 * @throws UnsupportedOperationException if this output does not support URI
	 * representation.
	 *
	 */
	public URI uri() throws UnsupportedOperationException;

	@Override
	default void start(LogConfig config) {
	}

	/**
	 * Writes a string and by default with charset {@link StandardCharsets#UTF_8}.
	 * @param event event associated with string.
	 * @param s string.
	 */
	default void write(LogEvent event, String s) {
		write(event, s.getBytes(StandardCharsets.UTF_8), ContentType.StandardContentType.TEXT_PLAIN);
	}

	/**
	 * Analogous to {@link OutputStream#write(byte[])}.
	 * @param event event associated with bytes.
	 * @param bytes data to be written.
	 * @param contentType content type of the bytes.
	 */
	default void write(LogEvent event, byte[] bytes, ContentType contentType) {
		write(event, bytes, 0, bytes.length, contentType);
	}

	/**
	 * Analogous to {@link OutputStream#write(byte[], int, int)}.
	 * @param event event associated with bytes.
	 * @param bytes data to be written.
	 * @param off offset.
	 * @param len length.
	 * @param contentType content type of the bytes.
	 */
	public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType);

	/**
	 * Write event with byte buffer.
	 * @param event event.
	 * @param buf byte buffer.
	 * @param contentType content type of the buf
	 */
	default void write(LogEvent event, ByteBuffer buf, ContentType contentType) {
		byte[] arr = new byte[buf.position()];
		buf.rewind();
		buf.get(arr);
		write(event, arr, contentType);
	}

	public void flush();

	/**
	 * Gross categorization of an output.
	 * @return output type.
	 */
	public OutputType type();

	/**
	 * The preferred write style of this output. The output should still honor all write
	 * methods but this signals to the encoder which style it prefers.
	 * @return write mode.
	 */
	default WriteMethod writeMethod() {
		return switch (type()) {
			case CONSOLE_OUT, CONSOLE_ERR -> WriteMethod.BYTES;
			case FILE, NETWORK -> WriteMethod.BYTE_BUFFER;
			case MEMORY -> WriteMethod.STRING;
		};
	}

	/**
	 * The preferred write style of an output. The output should still honor all write
	 * methods but this signals to the encoder which style it prefers.
	 */
	public enum WriteMethod {

		/**
		 * Prefer calling {@link LogOutput#write(LogEvent, String)}.
		 */
		STRING,
		/**
		 * Prefer calling {@link LogOutput#write(LogEvent, byte[], ContentType)} or
		 * {@link LogOutput#write(LogEvent, byte[], int, int, ContentType)}.
		 */
		BYTES,
		/**
		 * Prefer calling {@link LogOutput#write(LogEvent, ByteBuffer, ContentType)}.
		 */
		BYTE_BUFFER

	}

	/**
	 * Output type useful for defaults and categorization.
	 */
	public enum OutputType {

		/**
		 * Standard out.
		 */
		CONSOLE_OUT,
		/**
		 * Standard err.
		 */
		CONSOLE_ERR,
		/**
		 * Output targets a file.
		 */
		FILE,
		/**
		 * Output targets something over a network.
		 */
		NETWORK,
		/**
		 * Output targets in memory.
		 */
		MEMORY;

	}

	/**
	 * Standard out output.
	 * @return output.
	 */
	public static LogOutput ofStandardOut() {
		return new StdOutOutput();
	}

	/**
	 * Standard err output.
	 * @return output.
	 */
	public static LogOutput ofStandardErr() {
		var out = FileDescriptor.err;
		FileOutputStream fos = new FileOutputStream(out);
		return new StdErrOutput(fos.getChannel(), fos);
	}

	/**
	 * Creates an output from a file channel.
	 * @param uri uri of output.
	 * @param channel file channel.
	 * @return output.
	 */
	public static LogOutput of(URI uri, FileChannel channel) {
		return new FileChannelOutput(uri, channel);
	}

	void close();

	/**
	 * Marker interface that the output is thread safe.
	 */
	public interface ThreadSafeLogOutput extends LogOutput {

	}

}

class SynchronizedLogOutput implements ThreadSafeLogOutput {

	private final LogOutput output;

	public SynchronizedLogOutput(LogOutput output) {
		this.output = output;
	}

	@Override
	public URI uri() throws UnsupportedOperationException {
		return output.uri();
	}

	@Override
	public synchronized void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
		output.write(event, bytes, off, len, contentType);
	}

	@Override
	public synchronized void flush() {
		output.flush();
	}

	@Override
	public synchronized void close() {
		output.close();
	}

	@Override
	public OutputType type() {
		return output.type();
	}

}

class FileChannelOutput implements LogOutput {

	protected final URI uri;

	protected final FileChannel channel;

	public FileChannelOutput(URI uri, FileChannel channel) {
		super();
		this.uri = uri;
		this.channel = channel;
	}

	@Override
	public URI uri() throws UnsupportedOperationException {
		return uri;
	}

	@Override
	public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
		write(event, ByteBuffer.wrap(bytes, off, len), contentType);
	}

	@Override
	public void write(LogEvent event, ByteBuffer buffer, ContentType contentType) {
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
				MetaLog.error(FileChannelOutput.class, e);
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

	@Override
	public OutputType type() {
		return OutputType.FILE;
	}

}

abstract class Std extends FileChannelOutput implements LogOutput {

	private final OutputStream outputStream;

	public Std(URI uri, FileChannel channel, OutputStream outputStream) {
		super(uri, channel);
		this.outputStream = outputStream;
	}

	@Override
	public void write(LogEvent event, ByteBuffer buffer, ContentType contentType) {
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
	}

	@Override
	public void flush() {
	}

}

abstract class OutputStreamOutput implements LogOutput {

	protected final URI uri;

	protected final OutputStream outputStream;

	protected OutputStreamOutput(URI uri, OutputStream outputStream) {
		super();
		this.uri = uri;
		this.outputStream = outputStream;
	}

	@Override
	public URI uri() {
		return uri;
	}

	@Override
	public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
		try {
			outputStream.write(bytes, off, len);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void flush() {
		try {
			outputStream.flush();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void close() {
		try {
			outputStream.close();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public WriteMethod writeMethod() {
		return WriteMethod.BYTES;
	}

}

class StdOutOutput extends OutputStreamOutput {

	public StdOutOutput() {
		super(STDOUT_URI, System.out);
	}

	@Override
	public OutputType type() {
		return OutputType.CONSOLE_OUT;
	}

	@Override
	public void close() {
	}

}

class StdErrOutput extends Std {

	public StdErrOutput(FileChannel channel, OutputStream outputStream) {
		super(STDERR_URI, channel, outputStream);
	}

	@Override
	public OutputType type() {
		return OutputType.CONSOLE_ERR;
	}

}