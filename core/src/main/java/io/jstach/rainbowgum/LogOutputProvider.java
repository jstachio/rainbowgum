package io.jstach.rainbowgum;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public interface LogOutputProvider extends AutoCloseable {

	LogOutput of(URI uri) throws IOException;

	default void close() {
	}

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
			// closeables.add(fos);
			return LogOutput.of(fos.getChannel());
		}
		else if ("stdout".equals(scheme)) {
			return LogOutput.ofStandardOut();
		}
		else if ("stderr".equals(scheme)) {
			return LogOutput.ofStandardErr();
		}
		else {
			var p = Paths.get(uri);
			var channel = FileChannel.open(p, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
			channel.close();
			return LogOutput.of(channel);
		}
	}

}
