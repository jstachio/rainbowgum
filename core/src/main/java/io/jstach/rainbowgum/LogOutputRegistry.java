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
		/*
		 * TODO check rainbowgum is actually running.
		 */
		if (reopenLock.tryLock()) {

			try {
				return Actor.act(
						serviceRegistry.find(LogAppender.class).stream().map(a -> InternalLogAppender.of(a)).toList(),
						LogAction.StandardAction.REOPEN);
			}
			finally {
				reopenLock.unlock();
			}
		}
		else {
			return List.of();
		}
	}

	@Override
	public List<LogResponse> flush() {
		/*
		 * TODO check rainbowgum is actually running.
		 */
		if (reopenLock.tryLock()) {

			try {
				return Actor.act(
						serviceRegistry.find(LogAppender.class).stream().map(a -> InternalLogAppender.of(a)).toList(),
						LogAction.StandardAction.FLUSH);
			}
			finally {
				reopenLock.unlock();
			}
		}
		else {
			return List.of();
		}
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
			public LogOutput provide(LogProviderRef ref, String name, LogProperties properties) {
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
