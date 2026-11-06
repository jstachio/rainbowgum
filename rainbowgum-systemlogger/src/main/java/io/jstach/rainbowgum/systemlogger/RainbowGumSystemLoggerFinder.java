package io.jstach.rainbowgum.systemlogger;

import java.lang.System.Logger;
import java.util.Locale;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogRouter;
import io.jstach.rainbowgum.LoggerAPI;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Abstract System Logger Finder to allow users to create their own custom
 * System.LoggerFinder. <strong>This implementation does not cache System Loggers</strong>
 * by name!
 * <p>
 * <b>GraalVM native image</b>: this module bundles a {@code native-image.properties} (at
 * {@code META-INF/native-image/io.jstach.rainbowgum/rainbowgum-systemlogger/}) with
 * {@code --initialize-at-build-time} for {@code RouterProvider} plus the core classes
 * ({@code GlobalLogRouter}, {@code StaticLevelResolver}) its {@code LogRouter.global()}
 * fallback touches on first real use. See {@link #routerProvider} for why this is still
 * needed even though this class resolves its router lazily rather than eagerly in its
 * constructor, and why what gets frozen in is just the cheap "nothing bound yet" state,
 * not anything environment-specific. native-image's own embedded configuration discovery
 * picks this up from this module's jar automatically, no extra plugin or flag needed on
 * the consuming side.
 *
 * @see #INITIALIZE_RAINBOW_GUM_PROPERTY
 */
public abstract class RainbowGumSystemLoggerFinder extends System.LoggerFinder {

	/**
	 * Initialization flag.
	 * <p>
	 * Property: <code>{@index logging.systemlogger.initialize}</code>
	 * @see InitOption
	 */
	public static final String INITIALIZE_RAINBOW_GUM_PROPERTY = LogProperties.ROOT_PREFIX + "systemlogger.initialize";

	private final Supplier<? extends InitOption> optSupplier;

	/*
	 * Resolved lazily, on the first getLogger(...) call at run time, never in the
	 * constructor and never while a native image is being built.
	 *
	 * The JDK calls System.getLogger(...) for its own diagnostics from unrelated code,
	 * including during a GraalVM native-image build. Observed triggers:
	 * java.time/java.util.Locale formatting, and TrustStoreManagerFeature loading the
	 * default trust store, whose JCA security event logging asks
	 * System.getLogger("jdk.event.security").isLoggable(...). Whatever this finder
	 * resolves then is frozen into the image heap. Resolving the init option at build
	 * time would start a real Rainbow Gum whenever no RainbowGumEagerLoad (SLF4J) is on
	 * the class path, and that gum, plus the JUL handler its configurators install, would
	 * land in the image heap and fail the build. So during an image build this returns
	 * the queuing global router and resolves nothing; see isImageBuildTime().
	 */
	private volatile @Nullable RouterProvider routerProvider;

	/*
	 * Created lazily like routerProvider and for the same reason. Held here because
	 * RainbowGum.onGlobalChange only keeps a weak reference.
	 */
	private volatile @Nullable GlobalRainbowGum global;

	/**
	 * Values (case is ignored) for {@value #INITIALIZE_RAINBOW_GUM_PROPERTY}.
	 */
	public enum InitOption {

		/**
		 * Will not initialize rainbow gum.
		 */
		FALSE,
		/**
		 * Will initialize rainbow gum.
		 */
		TRUE,
		/**
		 * (default) Will check if there are implementations of
		 * {@link RainbowGumServiceProvider.RainbowGumEagerLoad} and if there are not will
		 * load rainbow gum.
		 */
		CHECK,
		/**
		 * Will reuse an existing rainbow gum or fail.
		 */
		REUSE;

		/**
		 * Parses an init option from a property value.
		 * @param input from properties.
		 * @return init option.
		 */
		public static InitOption parse(String input) {
			if (input.isBlank())
				return FALSE;
			return LogProperty.enumValue(InitOption.class, input, "blank");
		}

	}

	/**
	 * Creates the logger inder based on init option.
	 * @param optSupplier can be resolved with {@link #initOption(LogProperties)}.
	 */
	protected RainbowGumSystemLoggerFinder(Supplier<? extends InitOption> optSupplier) {
		this.optSupplier = optSupplier;
	}

	private RouterProvider routerProvider() {
		var rp = this.routerProvider;
		if (rp != null) {
			return rp;
		}
		if (isImageBuildTime()) {
			/*
			 * Not cached: the image starts with this finder unresolved, so the first real
			 * getLogger(...) at run time still applies the init option.
			 */
			return n -> LogRouter.global();
		}
		try {
			var opt = optSupplier.get();
			rp = switch (opt) {
				case FALSE -> n -> LogRouter.global();
				case TRUE -> new InitRouterProvider(RainbowGum::ofOrNull);
				case CHECK -> {
					if (RainbowGumServiceProvider.RainbowGumEagerLoad.exists()) {
						yield n -> LogRouter.global();
					}
					yield new InitRouterProvider(RainbowGum::ofOrNull);
				}
				case REUSE -> new InitRouterProvider(() -> {
					var gum = RainbowGum.getOrNull();
					if (gum == null) {
						throw new IllegalStateException(
								"SystemLogging was configured to reuse a loaded Rainbow Gum but none was found. "
										+ INITIALIZE_RAINBOW_GUM_PROPERTY + "=" + opt);
					}
					return gum;
				});
			};
			this.routerProvider = rp;
			return rp;
		}
		catch (Exception e) {
			// We have to do this because it because very difficult
			// to determine why the System Logging fails as it does not even print the
			// exception.
			var gum = RainbowGum.getOrNull();
			if (gum != null) {
				gum.config().alerts().error(getClass(), "Failed to create System.LoggerFinder", e);
			}
			else {
				System.err.println("[ERROR] - RAINBOW_GUM Failed to create System.LoggerFinder");
				e.printStackTrace();
			}
			throw e;
		}
	}

	@Override
	public Logger getLogger(String name, Module module) {
		var router = routerProvider().router(name);
		var global = global();
		if (!router.isChangeable(name)) {
			var level = router.levelResolver().resolveLevel(name);
			return LevelSystemLogger.of(name, level, router.route(name, level), global);
		}
		return new RainbowGumSystemLogger(name, router, global);
	}

	private GlobalRainbowGum global() {
		var g = this.global;
		if (g != null) {
			return g;
		}
		synchronized (this) {
			g = this.global;
			if (g == null) {
				g = this.global = GlobalRainbowGum.of();
			}
			return g;
		}
	}

	/**
	 * Whether a GraalVM native image is being built right now, from the system property
	 * GraalVM sets for that, so this module does not need GraalVM's SDK.
	 */
	static boolean isImageBuildTime() {
		return "buildtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
	}

	/**
	 * Gets the init option from properties.
	 * @param properties usually system properties.
	 * @return initialization option.
	 */
	protected static InitOption initOption(LogProperties properties) {
		return properties.forKey(INITIALIZE_RAINBOW_GUM_PROPERTY)
			.ofString()
			.map(InitOption::parse)
			.or(InitOption.CHECK)
			.validateNow(RainbowGumSystemLoggerFinder.class);
	}

	private interface RouterProvider {

		LogRouter.RootRouter router(String loggerName);

	}

	private class InitRouterProvider implements RouterProvider {

		private final Supplier<? extends @Nullable RainbowGum> supplier;

		private volatile boolean initialized;

		InitRouterProvider(Supplier<? extends @Nullable RainbowGum> supplier) {
			super();
			this.supplier = supplier;
		}

		@Override
		public LogRouter.RootRouter router(String loggerName) {
			LogRouter.RootRouter router;
			/*
			 * The first lookup finds or starts a gum as the init option says. Later ones
			 * follow the holder, so a removed gum is neither kept nor started again.
			 */
			var current = RainbowGumSystemLoggerFinder.this.global;
			RainbowGum gum = current == null ? null : current.gum();
			if (gum == null) {
				if (initialized) {
					return LogRouter.global();
				}
				gum = supplier.get();
				if (gum == null) {
					/*
					 * This thread is starting a gum, for example a dependency logging
					 * while properties are provided. The global router holds the events
					 * until it is bound.
					 */
					return LogRouter.global();
				}
			}
			initialized = true;
			gum.config().loggerRegistry().registerLoggerName(LoggerAPI.Standard.SYSTEM_LOGGER, loggerName);
			if (gum.config().changePublisher().isEnabled(loggerName)) {
				router = LogRouter.global();
			}
			else {
				router = gum.router();
			}
			return router;
		}

	}

}
