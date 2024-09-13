package io.jstach.rainbowgum.spi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.ServiceRegistry;

/**
 * RainbowGum SPI. The Rainbow Gum SPI uses the {@link ServiceLoader} with the
 * registration of <strong>this class</strong> and <em>NOT the <code>non-sealed</code>
 * subclasses!</em> Read the {@link ServiceLoader} doc to understand how to register a
 * service loader class. A common option is to use an annotation processor to generate the
 * <code>META-INF/services</code> registration. There are several libraries that can do
 * this:
 * <ul>
 * <li><a href="https://github.com/jstachio/pistachio#serviceloader-helper">Pistachio
 * SVC</a></li>
 * <li><a href="https://avaje.io/spi/">Avaje SPI</a></li>
 * <li><a href="https://github.com/kohsuke/metainf-services">metainf-services</a></li>
 * </ul>
 * However if your application is fully modularized (has a <code>module-info.java</code>)
 * you will need to use the module-info syntax:
 * {@snippet :
 *
 * provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider with com.mycompany.SomeService;
 *
 * }
 * <strong>Initialization Order:</strong>
 * <ol>
 * <li>{@link PropertiesProvider}</li>
 * <li>{@link Configurator}</li>
 * <li>{@link RainbowGumProvider}</li>
 * </ol>
 *
 */
@SuppressWarnings("InvalidInlineTag")
public sealed interface RainbowGumServiceProvider {

	/**
	 * Provides properties and or register services.
	 */
	public non-sealed interface PropertiesProvider extends RainbowGumServiceProvider {

		/**
		 * Provides properties and or register services. <em>If just registering services
		 * and not providing properies an empty list can be returned. </em>
		 * @param registry registry is usually empty here.
		 * @return list of properties.
		 */
		List<LogProperties> provideProperties(ServiceRegistry registry);

	}

	/**
	 * Providers sometimes need multiple passes to support dependencies without using true
	 * dependency injection.
	 */
	public enum Pass {

		/**
		 * First pass.
		 */
		FIRST,
		/**
		 * Second or greater pass but not the last pass.
		 */
		MIDDLE,
		/**
		 * The last pass to return true otherwise Rainbow Gum will fail to load. This
		 * state is useful if a provider can provide a better error by throwing an
		 * exception.
		 */
		LAST;

		static Pass of(int pass, int total) {
			if (pass >= (total - 1)) {
				return LAST;
			}
			if (pass == 0) {
				return FIRST;
			}
			return MIDDLE;
		}

	}

	/**
	 * The default amount of passes made to resolve registrators or configurators.
	 */
	public static int PASSES = 4;

	/**
	 * Called after {@link LogConfig} has been loaded to do various custom initialization
	 * like registering {@link io.jstach.rainbowgum.LogOutput.OutputProvider}s.
	 * <p>
	 * The configurator has the option to return <code>false</code> to indicate a retry
	 * needs to be made. This is a simple way to handle dependency needs of one
	 * configurator needing another to run prior.
	 *
	 * @see LogConfig#outputRegistry()
	 * @see ServiceRegistry
	 */
	@FunctionalInterface
	public non-sealed interface Configurator extends RainbowGumServiceProvider {

		/**
		 * Do ad-hoc initialization before RainbowGum is fully loaded.
		 * @param config config.
		 * @param pass info on what pass this is.
		 * @return <code>true</code> if all dependencies were found.
		 */
		boolean configure(LogConfig config, Pass pass);

		/**
		 * The default amount of passes made to resolve configurators.
		 */
		public static int CONFIGURATOR_PASSES = PASSES;

		/**
		 * Runs configurators. If a configurator returns false then it will be retried.
		 * The default of {@value #CONFIGURATOR_PASSES} passes will be tried.
		 * @param configurators stream of configurators.
		 * @param config config to use for registration.
		 * @throws IllegalStateException if there are configurators still returning
		 * <code>false</code>.
		 */
		public static void runConfigurators(Stream<? extends RainbowGumServiceProvider.Configurator> configurators,
				LogConfig config) {
			runConfigurators(configurators, config, CONFIGURATOR_PASSES);
		}

		/**
		 * Runs configurators. If a configurator returns false then it will be retried.
		 * @param configurators stream of configurators.
		 * @param config config to use for registration.
		 * @param passes number of times to retry configurator if it returns
		 * @throws IllegalStateException if there are configurators still returning
		 * <code>false</code>.
		 */
		private static void runConfigurators(Stream<? extends RainbowGumServiceProvider.Configurator> configurators,
				LogConfig config, int passes) {
			List<Configurator> unresolved = new ArrayList<>(configurators.toList());
			for (int i = 0; i < passes; i++) {
				var it = unresolved.iterator();
				while (it.hasNext()) {
					var c = it.next();
					if (c.configure(config, Pass.of(i, passes))) {
						it.remove();
						if (c instanceof AutoCloseable ac) {
							config.serviceRegistry().onClose(ac);
						}
					}
				}
				if (unresolved.isEmpty()) {
					break;
				}
			}
			if (!unresolved.isEmpty()) {
				throw new IllegalStateException("Configurators could not find dependencies (returned false) after "
						+ passes + " passes. configurators = " + unresolved);
			}

		}

	}

	/**
	 * Implement to create a custom RainbowGum. This is the preferred way to customize
	 * RainbowGum publishers, appenders, and outputs.
	 */
	public non-sealed interface RainbowGumProvider extends RainbowGumServiceProvider {

		/**
		 * Optionally provides a rainbow gum based on config.
		 * @param config config loaded.
		 * @return a Rainbow Gum or not.
		 */
		Optional<RainbowGum> provide(LogConfig config);

		/**
		 * If there are multiple rainbow gum providers found the higher priority ones are
		 * tried first ({@link #provide(LogConfig)}). The default is {@code 0}.
		 * <p>
		 * This feature allows custom rainbow gums for different environments like a
		 * testing version that lives in its own jar that is in Maven scope test. If its
		 * priority is higher and {@link #provide(LogConfig)} returns a non-empty optional
		 * then it will be used instead of than the production Rainbow Gum.
		 * @return priority order where higher number means it will be tried earlier.
		 */
		default int priority() {
			return 0;
		}

	}

	/**
	 * This is a marker interface that indicates that a facade implementation will do the
	 * eager loading of RainbowGum. This is used to determine if another facade that has
	 * the option to queue should queue and not load rainbowgum if any implementations are
	 * found of this interface.
	 *
	 * @apiNote Only facade implementers need concern themselves with this interface and
	 * for others it is an internal detail.
	 * @see #exists()
	 */
	public non-sealed interface RainbowGumEagerLoad extends RainbowGumServiceProvider {

		/**
		 * Without loading service providers will carefully check for the existence of any
		 * {@link RainbowGumEagerLoad} implementations.
		 * @return true if something is marked as eager.
		 */
		public static boolean exists() {
			var sl = ServiceLoaderCache.SERVICE_LOADER_CACHE.findServiceLoader();
			return sl.stream()
				.filter(p -> RainbowGumEagerLoad.class.isAssignableFrom(p.type()))
				.findFirst()
				.isPresent();
		}

	}

	/**
	 * Finds service providers based on type.
	 * @param <T> provider interface type.
	 * @param loader service loader to use.
	 * @param providerType provider class.
	 * @return stream containing only provider of the given type.
	 */
	public static <T extends RainbowGumServiceProvider> Stream<T> findProviders(
			ServiceLoader<RainbowGumServiceProvider> loader, Class<T> providerType) {
		return loader.stream().flatMap(p -> {
			if (providerType.isAssignableFrom(p.type())) {
				var s = providerType.cast(p.get());
				return Stream.of(s);
			}
			return Stream.empty();
		});
	}

	/**
	 * Creates config from service loader.
	 * @param loader service loader.
	 * @return config.
	 */
	public static LogConfig provideConfig(ServiceLoader<RainbowGumServiceProvider> loader) {
		return LogConfig.builder().serviceLoader(loader).build();
	}

	/**
	 * Will load all RainbowGum SPI to create a RainbowGum powered from the ServiceLoader.
	 * @param loader service loader to use.
	 * @return Rainbow Gum.
	 */
	public static RainbowGum provide(ServiceLoader<RainbowGumServiceProvider> loader) {
		var config = provideConfig(loader);
		@Nullable
		RainbowGum gum = findProviders(loader, RainbowGumProvider.class)
			.sorted(Comparator.comparingInt(RainbowGumProvider::priority).reversed())
			.flatMap(s -> s.provide(config).stream())
			.findFirst()
			.orElse(null);

		if (gum == null) {
			gum = RainbowGum.builder(config).build();
		}
		return gum;
	}

	/**
	 * Will load all RainbowGum SPI to create a RainbowGum powered from the ServiceLoader
	 * using the current thread context class loader if avaiable otherwise the system
	 * classloader.
	 * @return Rainbow Gum.
	 */
	public static RainbowGum provide() {
		// TODO perhaps we should use RainbowGum.class classloader
		return provide(cachedServiceLoader(Thread.currentThread().getContextClassLoader()));
	}

	private static ServiceLoader<RainbowGumServiceProvider> cachedServiceLoader(@Nullable ClassLoader classLoader) {
		if (classLoader == null) {
			return ServiceLoaderCache.SERVICE_LOADER_CACHE.findServiceLoader();
		}
		return ServiceLoaderCache.SERVICE_LOADER_CACHE.serviceLoader(classLoader);
	}

}

@SuppressWarnings("ImmutableEnumChecker")
enum ServiceLoaderCache {

	SERVICE_LOADER_CACHE;

	private final Map<ClassLoader, ServiceLoader<RainbowGumServiceProvider>> serviceLoaderCache = new WeakHashMap<>();

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	static ClassLoader defaultClassLoader() {
		// TODO perhaps we should use RainbowGum.class classloader
		return ClassLoader.getSystemClassLoader();
	}

	ServiceLoader<RainbowGumServiceProvider> serviceLoader(ClassLoader classLoader) {
		lock.writeLock().lock();
		try {
			var sl = serviceLoaderCache.computeIfAbsent(classLoader, cl -> {
				return ServiceLoader.load(RainbowGumServiceProvider.class, classLoader);
			});
			return Objects.requireNonNull(sl);
		}
		finally {
			lock.writeLock().unlock();
		}
	}

	private @Nullable ServiceLoader<RainbowGumServiceProvider> _findServiceLoader(
			@Nullable ClassLoader[] classLoaders) {
		for (var cl : classLoaders) {
			if (cl != null) {
				var sl = serviceLoaderCache.get(cl);
				if (sl != null) {
					return sl;
				}
			}
		}
		return null;
	}

	ServiceLoader<RainbowGumServiceProvider> findServiceLoader() {
		@Nullable
		ServiceLoader<RainbowGumServiceProvider> loader = null;
		@Nullable
		ClassLoader[] classLoaders = new @Nullable ClassLoader[] { Thread.currentThread().getContextClassLoader(),
				ServiceLoaderCache.class.getClassLoader(), defaultClassLoader() };
		lock.readLock().lock();
		try {
			loader = _findServiceLoader(classLoaders);
		}
		finally {
			lock.readLock().unlock();
		}
		if (loader != null) {
			return loader;
		}
		return serviceLoader(defaultClassLoader());
	}

}
