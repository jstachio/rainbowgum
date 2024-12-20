package io.jstach.rainbowgum;

import java.io.FileDescriptor;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.LogEncoder.Buffer;
import io.jstach.rainbowgum.LogEncoder.BufferHints;
import io.jstach.rainbowgum.annotation.CaseChanging;

/**
 * A resource that can be written to usually in binary. In other logging frameworks this
 * is often called an "Appender" not be confused with RainbowGum's appenders. LogOutputs
 * will be written synchronously by the appender either through locks or some other
 * mechanism so other than output external specific locking (e.g file locking or db
 * transaction) the output does not have to deal with that. <strong> In other words there
 * will be no overlapping write/flush/close calls as the appender and publisher combo
 * promises that. </strong>
 * <p>
 * To simplyfy flushing and corruption prevention each call to the write methods MUST be
 * the contents of the entire event! However the output should not flush unless
 * {@link #flush()} is called by the appender. That is the appender
 * <strong>usually</strong> decides when to flush.
 * <p>
 * The write methods do not throw {@link IOException} on purpose as it is implementations
 * responsibility to handle errors and to be resilient on their own.
 *
 * @see LogOutput.OutputProvider
 * @see Buffer
 * @see AppenderFlag#IMMEDIATE_FLUSH
 * @apiNote if for some reason the output needs to share events with other threads call
 * {@link LogEvent#freeze()} which will return an immutable event.
 */
public interface LogOutput extends LogLifecycle, Flushable, LogComponent {

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
	static final URI STDOUT_URI = URI.create(STDOUT_SCHEME + ":///");

	/**
	 * Standard ERR URI
	 */
	static final URI STDERR_URI = URI.create(STDERR_SCHEME + ":///");

	/**
	 * Provides a lazy loaded output from a URI.
	 * @param uri uri.
	 * @return provider of output.
	 * @apiNote the provider may throw an {@link UncheckedIOException}.
	 */
	private static LogProvider<LogOutput> of(URI uri) {
		/*
		 * TODO this should probably be public.
		 */
		return of(LogProviderRef.of(uri));
	}

	/**
	 * Provides a lazy loaded output from a URI.
	 * @param ref uri.
	 * @return provider of output.
	 * @apiNote the provider may throw an {@link UncheckedIOException}.
	 */
	public static LogProvider<LogOutput> of(LogProviderRef ref) {
		return (s, c) -> {
			return c.outputRegistry().provide(ref).provide(s, c);
		};
	}

	/**
	 * Standard out output. The default implementation <em>uses whatever is set to
	 * {@link System#out} at provision time</em>. <strong>If {@link System#out} is rebound
	 * the output will not be updated!</strong>
	 * @return output.
	 */
	public static LogProvider<LogOutput> ofStandardOut() {
		return of(LogOutput.STDOUT_URI);
	}

	/**
	 * Standard err output. The default implementation <em>uses whatever is set to
	 * {@link System#err} at provision time</em>. <strong>If {@link System#err} is rebound
	 * the output will not be updated!</strong>
	 * @return output.
	 */
	public static LogProvider<LogOutput> ofStandardErr() {
		return of(LogOutput.STDERR_URI);
	}

	/**
	 * Finds output based on URI.
	 */
	public interface OutputProvider {

		/**
		 * Creates an output ready to be injected with config.
		 * @param ref a URI reference to the output.
		 * @return provider of output.
		 */
		LogProvider<LogOutput> provide(LogProviderRef ref);

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
		 *
		 * @apiNote additional "standard" content types maybe added in the future before
		 * 1.0 thus one should expect the number of enum symbols to change and is not safe
		 * to pattern match on without a default.
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

	/**
	 * Requests the health of this output. If no exception is thrown the returned value is
	 * used. If an exception is thrown the status is considered to be error. This call
	 * follows the write rules where there will never be overlapping calls. The default
	 * implementation will return {@link LogResponse.Status.StandardStatus#OK}. which will
	 * check previous meta log error entries.
	 * @return status of this output.
	 * @throws Exception if status check fails which will be an error status.
	 */
	default LogResponse.Status status() throws Exception {
		return LogResponse.Status.StandardStatus.OK;
	}

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
		try (var buffer = encoder.buffer(this.bufferHints())) {
			write(events, count, encoder, buffer);
		}
	}

	/**
	 * Write a batch of events reusing the provided buffer and by default calling
	 * {@link #write(LogEvent, Buffer)} for each event synchronously.
	 * <p>
	 * The batching is usually a result of an asynchronous publisher so the maximum size
	 * is configured there with bufferSize. If this method is implemented there are some
	 * caveats.
	 * <p>
	 * <strong>DO NOT MODIFY THE ARRAY</strong>. It is reused. Do not use the
	 * <code>length</code> of the passed in array but instead use <code>count</code>
	 * parameter. Consequently do not share the array or buffer with other threads however
	 * individual events can be shared by calling {@link LogEvent#freeze()} to guarantee
	 * that which is a safe no-op if it is already immutable.
	 * <p>
	 * If the output plans on grouping the events into a single blob (byte array or byte
	 * buffer) a different output can be passed to the encoder other than itself (which is
	 * the default).
	 * @param events an array guaranteed to be smaller than count.
	 * @param count the number of items.
	 * @param encoder encoder to use for encoding.
	 * @param buffer buffer to reuse. Do not share it with other threads!
	 * @apiNote If the output wants to fan-out or encode with other threads the buffer
	 * passed should not be used and instead {@link LogEncoder#buffer(BufferHints)} should
	 * be called to get a new buffer for each thread. This is generally not desirable for
	 * most resources such as a file or database as sending a batch is much faster (single
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

	/**
	 * Flushes if the output is maintaining a buffer. An appender will call this after
	 * each event in synchronous mode or after a batched group of events if in
	 * asynchronous mode if {@link LogAppender.AppenderFlag#IMMEDIATE_FLUSH} is set.
	 * @see LogAppender.AppenderFlag#IMMEDIATE_FLUSH
	 */
	@Override
	public void flush();

	/**
	 * Gross categorization of an output.
	 * @return output type.
	 */
	public OutputType type();

	/**
	 * Hints to the buffer like what write style of the output and maximum buffer size.
	 * The output should still honor all write methods but this signals to the encoder
	 * which style it prefers.
	 * @return write mode.
	 */
	default BufferHints bufferHints() {
		return WriteMethod.BYTES;
	}

	/**
	 * Attempts to reopen the output if supported and <strong>SHOULD only be called by the
	 * appender</strong>! This call is mainly used for external log rotation systems such
	 * as logrotate or an aggregator agent.
	 * @return status and the default implementation will return
	 * {@link LogResponse.Status.StandardStatus#IGNORED}.
	 */
	default LogResponse.Status reopen() {
		return LogResponse.Status.StandardStatus.IGNORED;
	}

	/**
	 * The preferred write style of an output. The output should still honor all write
	 * methods but this signals to the encoder which style it prefers.
	 */
	public enum WriteMethod implements BufferHints {

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
		BYTE_BUFFER;

		@Override
		public WriteMethod writeMethod() {
			return this;
		}

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
	 * Will close the output and should only be called by the owning appender.
	 */
	@Override
	void close();

	/**
	 * Marker interface that the output is thread safe.
	 */
	public interface ThreadSafeLogOutput extends LogOutput {

	}

	/**
	 * Abstract output on output stream.
	 */
	abstract class AbstractOutputStreamOutput implements LogOutput {

		/**
		 * passed in uri
		 */
		protected final URI uri;

		/**
		 * output stream to write to.
		 */
		protected final OutputStream outputStream;

		/**
		 * Adapts an OutputStream to an Output.
		 * @param uri usually comes from output registry.
		 * @param outputStream output stream to adapt.
		 */
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
		public BufferHints bufferHints() {
			return WriteMethod.BYTES;
		}

	}

}

class StdOutOutput extends LogOutput.AbstractOutputStreamOutput {

	public StdOutOutput() {
		super(LogOutput.STDOUT_URI, Objects.requireNonNull(System.out));
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
		super(LogOutput.STDERR_URI, Objects.requireNonNull(System.err));
	}

	@Override
	public OutputType type() {
		return OutputType.CONSOLE_ERR;
	}

}
