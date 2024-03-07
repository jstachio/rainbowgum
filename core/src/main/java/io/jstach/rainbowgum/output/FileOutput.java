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
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogConfig.Provider;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.MetaLog;
import io.jstach.rainbowgum.annotation.LogConfigurable;

/**
 * An output that is designed for writing to a file.
 */
public interface FileOutput extends LogOutput {

	@Override
	default OutputType type() {
		return OutputType.FILE;
	}

	/**
	 * Creates a file output provider from lambda builder.
	 * @param consumer builder lambda.
	 * @return provider.
	 */
	public static Provider<FileOutput> of(Consumer<FileOutputBuilder> consumer) {
		return (s, c) -> {
			var builder = new FileOutputBuilder(s);
			consumer.accept(builder);
			return builder.build();
		};
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
	public static FileOutput of(@LogConfigurable.KeyParameter String name, URI uri, String fileName,
			@Nullable Boolean append, @Nullable Boolean prudent, @Nullable Integer bufferSize)
			throws UncheckedIOException {
		prudent = prudent == null ? false : prudent;
		append = append == null ? true : append;
		File file = new File(fileName);
		createMissingParentDirectories(file);
		FileOutputStream stream;
		try {
			stream = new FileOutputStream(file, append);
		}
		catch (FileNotFoundException e) {
			throw new UncheckedIOException(e);
		}
		if (prudent) {
			return new FileChannelOutput(uri, stream.getChannel());
		}
		OutputStream s;
		if (bufferSize == null) {
			s = new BufferedOutputStream(stream);
		}
		else if (bufferSize > 0) {
			s = new BufferedOutputStream(stream, bufferSize);
		}
		else {
			s = stream;
		}
		return new FileOutputStreamOutput(uri, s);
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

class FileOutputStreamOutput extends LogOutput.AbstractOutputStreamOutput implements FileOutput {

	protected FileOutputStreamOutput(URI uri, OutputStream outputStream) {
		super(uri, outputStream);
	}

}

class FileChannelOutput implements FileOutput {

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
	public WriteMethod writeMethod() {
		return WriteMethod.BYTE_BUFFER;
	}

}
