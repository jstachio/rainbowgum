package io.jstach.rainbowgum;

import java.io.FileDescriptor;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;

import io.jstach.rainbowgum.LogEncoder.Buffer;
import io.jstach.rainbowgum.LogOutput.ThreadSafeLogOutput;
import io.jstach.rainbowgum.annotation.CaseChanging;

/**
 * A resource that can be written to usually in binary. In other logging frameworks this
 * is often called an "Appender" not be confused with RainbowGum's appenders. LogOutputs
 * will be written synchronously by the appender either through locks or some other
 * mechanism so other than output external specific locking (e.g file locking or db
 * transaction) the output does not have to deal with that. <strong> In other words there
 * will be no overlapping write calls as the appender and publisher combo promises that.
 * </strong>
 * <p>
 * To simplyfy flushing and corruption prevention each call to the write methods MUST be
 * the contents of the entire event!
 * <p>
 * The write methods do not throw {@link IOException} on purpose as it is implementations
 * responsibility to handle errors and to be resilient on their own.
 *
 * @see LogOutput.OutputProvider
 * @see Buffer
 * @apiNote if for some reason the output needs to share events with other threads call
 * {@link LogEvent#freeze()} which will return an immutable event.
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
		@CaseChanging
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
	 * Write a batch of events by default calls
	 * {@link #write(LogEvent[], int, LogEncoder, Buffer)}. <strong>DO NOT MODIFY THE
	 * ARRAY</strong>. Do not use the <code>length</code> of the passed in array but
	 * instead use <code>count</code> parameter.
	 * @param events an array guaranteed to be smaller than count.
	 * @param count the number of items.
	 * @param encoder encoder to use for encoding.
	 */
	default void write(LogEvent[] events, int count, LogEncoder encoder) {
		try (var buffer = encoder.buffer()) {
			write(events, count, encoder, buffer);
		}
	}

	/**
	 * Write a batch of events reusing the provided buffer and by default calling
	 * {@link #write(LogEvent, Buffer)} for each event synchronously.
	 * <p>
	 * The batching is usually a result of an async publisher so the maximum size is
	 * configured there with bufferSize. If this method is implemented there are some
	 * caveats.
	 * <p>
	 * <strong>DO NOT MODIFY THE ARRAY</strong>. It is reused. Do not use the
	 * <code>length</code> of the passed in array but instead use <code>count</code>
	 * parameter. Consequently do not share the array or buffer with other threads however
	 * individual events can be shared by calling {@link LogEvent#freeze()} to guarantee
	 * that which is a safe noop if it is already immutable.
	 * <p>
	 * If the output plans on grouping the events into a single blob (byte array or byte
	 * buffer) a different output can be passed to the encoder other than itself (which is
	 * the default).
	 * @param events an array guaranteed to be smaller than count.
	 * @param count the number of items.
	 * @param encoder encoder to use for encoding.
	 * @param buffer buffer to reuse. Do not share it with other threads!
	 * @apiNote If the output wants to fanout or encode with other threads the buffer
	 * passed should not be used and instead {@link LogEncoder#buffer()} should be called
	 * to get a new buffer for each thread. This is generally not desirable for most
	 * resources such as a file or database as sending a batch is much faster (single
	 * writer principle).
	 */
	default void write(LogEvent[] events, int count, LogEncoder encoder, Buffer buffer) {
		for (int i = 0; i < count; i++) {
			buffer.clear();
			var event = events[i];
			encoder.encode(event, buffer);
			write(event, buffer);
		}
	}

	/**
	 * Writes a prepared buffer and by default drains the buffer to this output.
	 * @param event event.
	 * @param buffer buffer that is already filled. Do not share it with other threads!
	 * @apiNote the buffer already has the encoding.
	 */
	default void write(LogEvent event, Buffer buffer) {
		buffer.drain(this, event);
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
	@CaseChanging
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
		return new StdErrOutput();
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

	/**
	 * Abstract output on outputstream.
	 */
	abstract class AbstractOutputStreamOutput implements LogOutput {

		protected final URI uri;

		protected final OutputStream outputStream;

		protected AbstractOutputStreamOutput(URI uri, OutputStream outputStream) {
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

class StdOutOutput extends LogOutput.AbstractOutputStreamOutput {

	public StdOutOutput() {
		super(LogOutput.STDOUT_URI, System.out);
	}

	@Override
	public LogOutput.OutputType type() {
		return OutputType.CONSOLE_OUT;
	}

	@Override
	public void close() {
	}

}

class StdErrOutput extends LogOutput.AbstractOutputStreamOutput {

	protected StdErrOutput() {
		super(LogOutput.STDERR_URI, System.err);
	}

	@Override
	public OutputType type() {
		return OutputType.CONSOLE_ERR;
	}

}
