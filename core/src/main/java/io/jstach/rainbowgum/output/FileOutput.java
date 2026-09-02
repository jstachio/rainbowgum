package io.jstach.rainbowgum.output;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogAlerts;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder.BufferHints;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.LogResponse.Status;
import io.jstach.rainbowgum.annotation.LogConfigurable;
import io.jstach.rainbowgum.annotation.LogConfigurable.DefaultParameter;

/**
 * An output that is designed for writing to a file.
 */
public interface FileOutput extends LogOutput {

	/**
	 * Default file buffer size. This size was chosen based on Logbacks default.
	 */
	public static final int DEFAULT_BUFFER_SIZE = 8192;

	@Override
	default OutputType type() {
		return OutputType.FILE;
	}

	/**
	 * Creates a file output provider from lambda builder and uses the config properties
	 * from the returned log provider.
	 * @param consumer builder lambda.
	 * @return provider.
	 */
	public static LogProvider<FileOutput> of(Consumer<FileOutputBuilder> consumer) {
		return (s, c) -> {
			var builder = new FileOutputBuilder(s);
			consumer.accept(builder);
			builder.fromProperties(c.properties());
			return builder.build();
		};
	}

	/**
	 * Create output from provider ref. This is mostly an internal call.
	 * @param ref file provider ref.
	 * @return provider.
	 */
	public static LogProvider<LogOutput> of(LogProviderRef ref) {
		return (s, c) -> {
			return provide(ref, s, c.properties());
		};
	}

	private static LogOutput provide(LogProviderRef ref, String name, LogProperties properties) {
		FileOutputBuilder b = new FileOutputBuilder(name);
		var uri = ref.uri();
		LogProperties combined;
		if (uri.getQuery() != null) {
			combined = LogProperties.of(uri, b.propertyPrefix(), properties, ref.keyOrNull());
			String s = uri.toString();
			int index = s.indexOf('?');
			s = s.substring(0, index);
			uri = URI.create(s);
			uri = Paths.get(uri).toUri();
		}
		else {
			combined = properties;
		}
		b.uri(uri);
		b.fromProperties(combined);
		return b.build();
	}

	/**
	 * Creates file output.
	 * @param name name of output not file name.
	 * @param uri file uri.
	 * @param fileName file name.
	 * @param append whether or not to append to existing file.
	 * @param prudent logback prudent mode where files are locked on each write.
	 * @param bufferSize buffer size in bytes.
	 * @return file output.
	 * @throws UncheckedIOException if file not found.
	 */
	@SuppressWarnings("resource")
	@LogConfigurable(prefix = LogProperties.OUTPUT_PREFIX)
	public static FileOutput of(@LogConfigurable.KeyParameter String name, @Nullable URI uri, @Nullable String fileName,
			@Nullable Boolean append, @Nullable Boolean prudent,
			@DefaultParameter("DEFAULT_BUFFER_SIZE") Integer bufferSize) throws UncheckedIOException {
		boolean prudent_ = prudent == null ? false : prudent;
		boolean append_ = append == null ? true : append;
		IOSupplier<FileOutput> supplier = () -> {
			File file;
			URI uri_ = uri;
			if (fileName != null) {
				file = new File(fileName);
				uri_ = file.toURI();
			}
			else if (uri_ != null) {
				file = new File(uri_);
			}
			else {
				throw new IOException("fileName and uri cannot both be unset.");
			}
			createMissingParentDirectories(file);
			FileOutputStream stream;
			try {
				stream = new FileOutputStream(file, append_);
			}
			catch (FileNotFoundException e) {
				throw new UncheckedIOException(e);
			}
			if (prudent_) {
				return new FileChannelOutput(uri_, stream.getChannel());
			}
			OutputStream s;
			Objects.requireNonNull(bufferSize);
			if (bufferSize <= 0) {
				s = stream;
			}
			else {
				s = new BufferedOutputStream(stream, bufferSize);
			}
			return new FileOutputStreamOutput(uri_, s);
		};

		return new ReopenableFileOutput(supplier);
	}

	/**
	 * Creates the parent directories of a file. If parent directories not specified in
	 * file's path, then nothing is done and this returns gracefully.
	 * @param file file whose parent directories (if any) should be created
	 * @return {@code true} if either no parents were specified, or if all parent
	 * directories were created successfully; {@code false} otherwise
	 */
	private static boolean createMissingParentDirectories(File file) {
		File parent = file.getParentFile();
		if (parent == null) {
			// Parent directory not specified, therefore it's a request to
			// create nothing. Done! ;)
			return true;
		}

		// File.mkdirs() creates the parent directories only if they don't
		// already exist; and it's okay if they do.
		parent.mkdirs();
		return parent.exists();
	}

}

@FunctionalInterface
interface IOSupplier<T> {

	T _get() throws IOException;

	default T get() throws UncheckedIOException {
		try {
			return _get();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}

class ReopenableFileOutput implements ForwardingOutput, FileOutput {

	private volatile FileOutput fileOutput;

	private @Nullable LogConfig config;

	private final IOSupplier<FileOutput> supplier;

	public ReopenableFileOutput(IOSupplier<FileOutput> supplier) {
		super();
		this.supplier = supplier;
		this.fileOutput = supplier.get();
	}

	@Override
	public void start(LogConfig config) {
		this.config = config;
		fileOutput.start(config);
	}

	@Override
	public @Nullable LogOutput delegate() {
		return fileOutput;
	}

	@Override
	public URI uri() throws UnsupportedOperationException {
		return fileOutput.uri();
	}

	@Override
	public OutputType type() {
		return fileOutput.type();
	}

	@Override
	public BufferHints bufferHints() {
		return fileOutput.bufferHints();
	}

	@Override
	public Status reopen() {
		this.fileOutput.close();
		this.fileOutput = supplier.get();
		var config = this.config;
		if (config != null) {
			this.fileOutput.start(config);
		}
		return Status.StandardStatus.OK;
	}

}

class FileOutputStreamOutput extends LogOutput.AbstractOutputStreamOutput implements FileOutput {

	protected FileOutputStreamOutput(URI uri, OutputStream outputStream) {
		super(uri, outputStream);
	}

}

class FileChannelOutput implements FileOutput {

	protected final URI uri;

	protected final FileChannel channel;

	/*
	 * A log call can still be in flight (e.g. routed through SLF4J from a shutdown hook)
	 * after something outside the normal appender/publisher lifecycle - like Spring
	 * Boot's LoggingSystem.cleanUp() - has already closed this output directly. Once
	 * closed, write/flush become no-ops instead of throwing so that race does not surface
	 * as an uncaught exception on whatever thread is still trying to log. See the
	 * analogous fix for LogOutput.AbstractOutputStreamOutput (issue #323).
	 */
	private final AtomicBoolean closed = new AtomicBoolean();

	private volatile LogAlerts alerts = LogAlerts.of();

	public FileChannelOutput(URI uri, FileChannel channel) {
		super();
		this.uri = uri;
		this.channel = channel;
	}

	@Override
	public void start(LogConfig config) {
		this.alerts = config.alerts();
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
		if (closed.get()) {
			return;
		}
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
				alerts.error(FileChannelOutput.class, e);
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
		if (!closed.compareAndSet(false, true)) {
			return;
		}
		try {
			channel.close();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void flush() {
		if (closed.get()) {
			return;
		}
		try {
			channel.force(false);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public WriteMethod bufferHints() {
		return WriteMethod.BYTE_BUFFER;
	}

}
