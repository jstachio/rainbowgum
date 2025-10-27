package io.jstach.rainbowgum;

import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.jstach.rainbowgum.LogOutput.OutputProvider;
import io.jstach.rainbowgum.output.FileOutput;
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
	 * Attempts to reopen all outputs of a certain type usually for log rotation. This
	 * call will block if it attempts to reopen. If reopening is already happening an
	 * empty list will be returned.
	 * @return the output status of reopened outputs or an empty list if no outputs were
	 * reopened.
	 */
	public List<LogResponse> reopen();

	/**
	 * Attempts to flush all outputs usually for log rotation. This call will block if it
	 * attempts to flush. If flush is already happening an empty list will be returned.
	 * @return the output status of reopened outputs or an empty list if no outputs were
	 * reopened.
	 */
	public List<LogResponse> flush();

	/**
	 * Will retrieve the status of all outputs usually for health checking.
	 * @return list of status of outputs.
	 */
	public List<LogResponse> status();

}

final class DefaultOutputRegistry implements LogOutputRegistry {

	private final Map<String, LogOutput> outputs = new ConcurrentHashMap<>();

	private final Map<String, OutputProvider> providers = new ConcurrentHashMap<>();

	private final ServiceRegistry serviceRegistry;

	private final ReentrantLock reopenLock = new ReentrantLock();

	static LogOutputRegistry of(ServiceRegistry serviceRegistry) {
		var r = new DefaultOutputRegistry(serviceRegistry);
		for (var p : StandardLogOutputProvider.values()) {
			r.register(p.scheme(), p);
		}
		return r;
	}

	public DefaultOutputRegistry(ServiceRegistry serviceRegistry) {
		this.serviceRegistry = serviceRegistry;
	}

	@Override
	public List<LogResponse> reopen() {
		return requestIO(LogAction.StandardAction.REOPEN);
	}

	@Override
	public List<LogResponse> flush() {
		return requestIO(LogAction.StandardAction.FLUSH);
	}

	@Override
	public List<LogResponse> status() {
		/*
		 * TODO should we queue status requests with a lock? Probably not.
		 */
		return _request(LogAction.StandardAction.STATUS);
	}

	private List<LogResponse> requestIO(LogAction action) {
		if (reopenLock.tryLock()) {
			try {
				return _request(action);
			}
			finally {
				reopenLock.unlock();
			}
		}
		else {
			return List.of();
		}
	}

	private List<LogResponse> _request(LogAction action) {
		/*
		 * TODO check rainbowgum is actually running.
		 */
		return Actor.act(serviceRegistry.find(LogAppender.class).stream().map(a -> InternalLogAppender.of(a)).toList(),
				action);
	}

	@Override
	public void register(String scheme, OutputProvider provider) {
		providers.put(scheme, provider);
	}

	private LogOutput _register(String name, LogOutput output) {
		if (outputs.putIfAbsent(name, output) != null) {
			throw new IllegalArgumentException("Name is already in use. name = '%s'".formatted(name));
		}
		return output;
	}

	@Override
	public Optional<LogOutput> output(String name) {
		return Optional.ofNullable(outputs.get(name));
	}

	private static URI normalize(URI uri) {
		String scheme = uri.getScheme();
		String path = uri.getPath();
		String query = uri.getRawQuery();
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
		else if (scheme.equals("file")) {
			if (path.startsWith("/./")) {
				path = path.substring(1);
			}
			uri = Paths.get(path).toUri();
			if (query != null) {
				uri = URI.create(uri.toString() + "?" + query);
			}
		}
		return uri;
	}

	private static LogProviderRef normalize(LogProviderRef ref) {
		var uri = normalize(ref.uri());
		return new DefaultLogProviderRef(uri, ref.keyOrNull());
	}

	@Override
	public LogProvider<LogOutput> provide(LogProviderRef ref) {
		ref = normalize(ref);
		var uri = ref.uri();
		String scheme = Objects.requireNonNull(uri.getScheme());
		OutputProvider customProvider = providers.get(scheme);
		if (customProvider == null) {
			throw new LogProviderRef.NotFoundException(
					"No output found. Scheme not registered. scheme: '" + scheme + "',  URI: '" + uri + "'");
		}
		var _ref = ref;
		return (name, config) -> {
			return _register(name, customProvider.provide(_ref).provide(name, config));
		};
	}

	enum StandardLogOutputProvider implements LogOutput.OutputProvider {

		STDOUT {
			@Override
			public LogOutput provide(LogProviderRef ref, String name, LogProperties properties) {
				return new StdOutOutput();
			}

			@Override
			public String scheme() {
				return LogOutput.STDOUT_SCHEME;
			}

		},
		STDERR {
			@Override
			public LogOutput provide(LogProviderRef ref, String name, LogProperties properties) {
				return new StdErrOutput();
			}

			@Override
			public String scheme() {
				return LogOutput.STDERR_SCHEME;
			}

		},
		LIST {
			@Override
			public LogOutput provide(LogProviderRef ref, String name, LogProperties properties) {
				return new ListLogOutput();
			}

			@Override
			public String scheme() {
				return LIST_OUTPUT_SCHEME;
			}

		},
		FILE {

			@Override
			public LogProvider<LogOutput> provide(LogProviderRef ref) {
				return FileOutput.of(ref);
			}

			@Override
			public LogOutput provide(LogProviderRef ref, String name, LogProperties properties) {
				throw new UnsupportedOperationException();
			}

			@Override
			public String scheme() {
				return LogOutput.FILE_SCHEME;
			}
		};

		@Override
		public LogProvider<LogOutput> provide(LogProviderRef ref) {
			return (s, c) -> {
				return provide(ref, s, c.properties());
			};
		}

		public abstract LogOutput provide(LogProviderRef ref, String name, LogProperties properties);

		public abstract String scheme();

	}

}
