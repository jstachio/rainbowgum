package io.jstach.rainbowgum.systemlogger;

import java.lang.System.Logger;
import java.util.Locale;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogRouter;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.LogProperty.Property;

/**
 * Abstract System Logger Finder to allow users to create their own custom
 * System.LoggerFinder. <strong>This implementation does not cache System Loggers</strong>
 * by name!
 *
 * @see #INTIALIZE_RAINBOW_GUM_PROPERTY
 */
public abstract class RainbowGumSystemLoggerFinder extends System.LoggerFinder {

	/**
	 * Initialization flag.
	 * @see InitOption
	 */
	public static final String INITIALIZE_RAINBOW_GUM_PROPERTY = LogProperties.ROOT_PREFIX + "systemlogger.initialize";

	private final RouterProvider routerProvider;

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
	 * @param opt can be resolved with {@link #initOption(LogProperties)}.
	 */
	protected RainbowGumSystemLoggerFinder(InitOption opt) {
		this.routerProvider = switch (opt) {
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
	}

	@Override
	public Logger getLogger(String name, Module module) {
		var router = routerProvider.router(name);
		if (!router.isChangeable(name)) {
			var level = router.levelResolver().resolveLevel(name);
			return LevelSystemLogger.of(name, level, router.route(name, level));
		}
		return RainbowGumSystemLogger.of(name, router);
	}

	/**
	 * Gets the init option from properties.
	 * @param properties usually system properties.
	 * @return intialization option.
	 */
	protected static InitOption initOption(LogProperties properties) {
		return Property.builder() //
			.map(InitOption::parse)
			.build(INITIALIZE_RAINBOW_GUM_PROPERTY) //
			.get(properties) //
			.value(InitOption.CHECK);
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
				// var rootRouter = gum.router();
				// var levelResolver = rootRouter.levelResolver();
				// var level = levelResolver.resolveLevel(loggerName);
				// var logger = rootRouter.route(loggerName, level);
				// router = LogRouter.ofLevel(logger, level);
				router = gum.router();
			}
			return router;
		}

	}

}
