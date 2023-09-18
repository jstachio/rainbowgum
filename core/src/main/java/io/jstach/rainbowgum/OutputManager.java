package io.jstach.rainbowgum;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public interface OutputManager extends AutoCloseable {

	LogEncoder of(URI uri) throws IOException;
	
	default void close() {}
}
enum DefaultOutputManager implements OutputManager {
	INSTANCE;

	//private CopyOnWriteArrayList<AutoCloseable> closeables = new CopyOnWriteArrayList<>();
	
	@Override
	public LogEncoder of(
			URI uri) throws IOException {
		String scheme = uri.getScheme();
		String path = uri.getPath();
		if (scheme == null && path != null) {
			@SuppressWarnings("resource")
			FileOutputStream fos = new FileOutputStream(path);
			//closeables.add(fos);
			return LogEncoder.of(fos.getChannel());
		}
		else if ("stdout".equals(scheme)) {
			return LogEncoder.of(System.out);
		}
		else if ("stderr".equals(scheme)) {
			return LogEncoder.of(System.err);
		}
		else {
			var p = Paths.get(uri);
			var channel = FileChannel.open(p, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
			channel.close();
			return LogEncoder.of(channel);
		}
	}
	
}
