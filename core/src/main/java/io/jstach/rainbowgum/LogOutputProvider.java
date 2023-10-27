package io.jstach.rainbowgum;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Finds output based on URI.
 */
public interface LogOutputProvider extends AutoCloseable {

	/**
	 * Loads an output from a URI.
	 * @param uri uri.
	 * @return output.
	 * @throws IOException
	 */
	LogOutput of(URI uri) throws IOException;

	default void close() {
	}

	/**
	 * Default output provider.
	 * @return default output provider.
	 */
	public static LogOutputProvider of() {
		return DefaultOutputProvider.INSTANCE;
	}

}

enum DefaultOutputProvider implements LogOutputProvider {

	INSTANCE;

	@Override
	public LogOutput of(URI uri) throws IOException {
		String scheme = uri.getScheme();
		String path = uri.getPath();
		if (scheme == null && path != null) {
			@SuppressWarnings("resource")
			FileOutputStream fos = new FileOutputStream(path);
			return LogOutput.of(uri, fos.getChannel());
		}
		else if (LogOutput.STDOUT_SCHEME.equals(scheme)) {
			return LogOutput.ofStandardOut();
		}
		else if (LogOutput.STDERR_SCHEME.equals(scheme)) {
			return LogOutput.ofStandardErr();
		}
		else {
			var p = Paths.get(uri);
			var channel = FileChannel.open(p, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
			channel.close();
			return LogOutput.of(uri, channel);
		}
	}

}
