package io.jstach.rainbowgum.systemlogger;

import java.lang.System.Logger;
import java.util.Locale;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogRouter;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Abstract System Logger Finder to allow users to create their own custom
 * System.LoggerFinder. <strong>This implementation does not cache System Loggers</strong>
 * by name!
 *
 * @see #INITIALIZE_RAINBOW_GUM_PROPERTY
 */
public abstract class RainbowGumSystemLoggerFinder extends System.LoggerFinder {

	/**
	 * Initialization flag.
	 * @see InitOption
	 */
	public static final String INITIALIZE_RAINBOW_GUM_PROPERTY = LogProperties.ROOT_PREFIX + "systemlogger.initialize";

	private final Supplier<? extends InitOption> optSupplier;

	/*
	 * Resolved lazily, on the first getLogger(...) call, not eagerly in the constructor.
	 * Constructing a System.LoggerFinder happens through java.util.ServiceLoader, which
	 * the JDK itself may trigger from all sorts of incidental static initialization (see
	 * rainbowgum-jdk's own module javadoc); under GraalVM native-image's default
	 * build-time class initialization in particular, an eager constructor here would
	 * freeze whatever InitOption/RainbowGum state happened to resolve during the build
	 * into the image, using build-time system properties and a build-time
	 * RainbowGum.getOrNull() check, not the real ones the running image would see. A
	 * benign race (two threads computing the same idempotent value once each) is fine
	 * here, matching InitRouterProvider's own existing lazy resolution of its RainbowGum
	 * supplier just below.
	 *
	 * This alone does not make a custom System.LoggerFinder fully invisible to
	 * native-image's build-time analysis, since the JDK's own internals (java.time,
	 * java.util.Locale/Calendar formatting) call System.getLogger(...) incidentally, for
	 * their own diagnostics, from all sorts of unrelated static-init paths that end up
	 * reachable during a real build; whichever registered LoggerFinder is on the
	 * classpath gets swept up regardless of how lazy its own construction is. What
	 * laziness here does buy: whatever gets resolved and frozen into the image heap as a
	 * side effect is now always a *fresh*, real resolution (this exact code path, run for
	 * real, not a stale decision baked in from something else), and the constructor
	 * itself is trivial, so native-image only needs
	 * `--initialize-at-build-time=io.jstach.rainbowgum.jdk.systemlogger
	 * .SystemLoggingFactory,io.jstach.rainbowgum.systemlogger
	 * .RainbowGumSystemLoggerFinder$RouterProvider` (confirmed by hand against a real
	 * GraalVM build), not every class in the whole io.jstach.rainbowgum package.
	 */
	private volatile @Nullable RouterProvider routerProvider;

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
			return InitOption.valueOf(input.toUpperCase(Locale.ROOT));
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
		try {
			var opt = optSupplier.get();
			rp = switch (opt) {
				case FALSE -> n -> LogRouter.global();
				case TRUE -> new InitRouterProvider(RainbowGum::of);
				case CHECK -> {
					if (RainbowGumServiceProvider.RainbowGumEagerLoad.exists()) {
						yield n -> LogRouter.global();
					}
					yield new InitRouterProvider(RainbowGum::of);
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
		if (!router.isChangeable(name)) {
			var level = router.levelResolver().resolveLevel(name);
			return LevelSystemLogger.of(name, level, router.route(name, level));
		}
		return RainbowGumSystemLogger.of(name, router);
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

		private final Supplier<RainbowGum> supplier;

		private volatile @Nullable RainbowGum gum = null;

		public InitRouterProvider(Supplier<RainbowGum> supplier) {
			super();
			this.supplier = supplier;
		}

		@Override
		public LogRouter.RootRouter router(String loggerName) {
			LogRouter.RootRouter router;
			RainbowGum gum = this.gum;
			if (gum == null) {
				gum = this.gum = supplier.get();
			}
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
