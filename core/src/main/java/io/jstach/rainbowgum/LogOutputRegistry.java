package io.jstach.rainbowgum;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import io.jstach.rainbowgum.LogProperties.Property;

/**
 * Register output providers by URI scheme.
 */
public sealed interface LogOutputRegistry extends LogOutputProvider permits DefaultOutputRegistry {

	/**
	 * Register a provider by {@link URI#getScheme() scheme}.
	 * @param scheme URI scheme to match for.
	 * @param provider provider for scheme.
	 */
	public void put(String scheme, LogOutputProvider provider);

	/**
	 * Default output provider.
	 * @return default output provider.
	 */
	public static LogOutputRegistry of() {
		return new DefaultOutputRegistry();
	}

}

final class DefaultOutputRegistry implements LogOutputRegistry {

	static final Property<List<String>> outputProperty = Property.builder()
		.map(p -> Stream.of(p.split(",")).filter(s -> !s.trim().isEmpty()).toList())
		.build(LogProperties.OUTPUT_PROPERTY);

	private final Map<String, LogOutputProvider> providers = new ConcurrentHashMap<>();

	@Override
	public void put(String scheme, LogOutputProvider provider) {
		providers.put(scheme, provider);
	}

	@Override
	public LogOutput output(URI uri, String name, LogProperties properties) throws IOException {
		String scheme = uri.getScheme();
		String path = uri.getPath();
		LogOutputProvider customProvider;
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
		else if ((customProvider = providers.get(scheme)) != null) {
			return customProvider.output(uri, name, properties);
		}
		else {
			var p = Paths.get(uri);
			var channel = FileChannel.open(p, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
			channel.close();
			return LogOutput.of(uri, channel);
		}
	}

}
