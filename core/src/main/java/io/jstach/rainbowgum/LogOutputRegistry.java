package io.jstach.rainbowgum;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.jstach.rainbowgum.DefaultOutputRegistry.StandardLogOutputProvider;
import io.jstach.rainbowgum.LogOutput.OutputProvider;
import io.jstach.rainbowgum.output.FileOutputBuilder;
import io.jstach.rainbowgum.output.ListLogOutput;

/**
 * Register output providers by URI scheme.
 */
public sealed interface LogOutputRegistry extends OutputProvider permits DefaultOutputRegistry {

	/**
	 * A meta URI scheme to reference outputs registered somewhere else.
	 */
	public static String NAMED_OUTPUT_SCHEME = "name";

	/**
	 * The URI scheme for list provider.
	 */
	public static String LIST_OUTPUT_SCHEME = "list";

	/**
	 * Register a provider by {@link URI#getScheme() scheme}.
	 * @param scheme URI scheme to match for.
	 * @param provider provider for scheme.
	 */
	public void register(String scheme, OutputProvider provider);

	/**
	 * Finds an output by name.
	 * @param name output name.
	 * @return maybe an output.
	 */
	Optional<LogOutput> output(String name);

	/**
	 * Registers an output by name for lookup.
	 * @param name name assigned to output.
	 * @param output for name.
	 */
	public void register(String name, LogOutput output);

	/**
	 * Default output provider.
	 * @return default output provider.
	 */
	public static LogOutputRegistry of() {
		var r = new DefaultOutputRegistry();
		for (var p : StandardLogOutputProvider.values()) {
			r.register(p.scheme(), p);
		}
		return r;
	}

}

final class DefaultOutputRegistry implements LogOutputRegistry {

	private final Map<String, LogOutput> outputs = new ConcurrentHashMap<>();

	private final Map<String, OutputProvider> providers = new ConcurrentHashMap<>();

	@Override
	public void register(String scheme, OutputProvider provider) {
		providers.put(scheme, provider);
	}

	@Override
	public void register(String name, LogOutput output) {
		_register(name, output);
	}

	private LogOutput _register(String name, LogOutput output) {
		if (outputs.putIfAbsent(name, output) != null) {
			throw new IllegalArgumentException("Name is already in use. name = '%s'".formatted("name"));
		}
		return output;
	}

	@Override
	public Optional<LogOutput> output(String name) {
		return Optional.ofNullable(outputs.get(name));
	}

	// LogOutput provide(String name, LogProperties properties) throws IOException {
	// var o = output(name).orElse(null);
	// if (o != null) {
	// return o;
	// }
	// return provide(URI.create(name + ":///"), name, properties);
	// }

	private static URI normalize(URI uri) {
		String scheme = uri.getScheme();
		String path = uri.getPath();

		if (scheme == null) {
			if (path == null) {
				throw new IllegalArgumentException("URI is not proper: " + uri);
			}
			if (path.startsWith("./")) {
				uri = Paths.get(path).toUri();
			}
			else {
				uri = URI.create(path + ":///");
			}
		}
		return uri;
	}

	@Override
	public LogOutput provide(URI uri, String name, LogProperties properties) throws IOException {
		uri = normalize(uri);
		String scheme = Objects.requireNonNull(uri.getScheme());
		OutputProvider customProvider = providers.get(scheme);
		if (customProvider == null) {
			throw new IOException("Output for uri: " + uri + " not found.");
		}
		return _register(name, customProvider.provide(uri, name, properties));
	}

	enum StandardLogOutputProvider implements LogOutput.OutputProvider {

		STDOUT {
			@Override
			public LogOutput provide(URI uri, String name, LogProperties properties) throws IOException {
				return new StdOutOutput();
			}

			@Override
			public String scheme() {
				return LogOutput.STDOUT_SCHEME;
			}

		},
		STDERR {
			@Override
			public LogOutput provide(URI uri, String name, LogProperties properties) throws IOException {
				return new StdErrOutput();
			}

			@Override
			public String scheme() {
				return LogOutput.STDERR_SCHEME;
			}

		},
		LIST {
			@Override
			public LogOutput provide(URI uri, String name, LogProperties properties) throws IOException {
				return new ListLogOutput();
			}

			@Override
			public String scheme() {
				return LIST_OUTPUT_SCHEME;
			}

		},
		FILE {
			@Override
			public LogOutput provide(URI uri, String name, LogProperties properties) throws IOException {
				FileOutputBuilder b = new FileOutputBuilder(name);
				b.uri(uri);
				b.fromProperties(properties);
				return b.build();
			}

			@Override
			public String scheme() {
				return LogOutput.FILE_SCHEME;
			}
		};

		public abstract String scheme();

	}

}
