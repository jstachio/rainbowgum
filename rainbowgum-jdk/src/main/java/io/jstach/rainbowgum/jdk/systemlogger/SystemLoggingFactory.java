package io.jstach.rainbowgum.jdk.systemlogger;

import java.lang.System.Logger;
import java.util.Locale;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperties.Property;
import io.jstach.rainbowgum.LogRouter;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.jdk.jul.JULConfigurator;
import io.jstach.rainbowgum.systemlogger.RainbowGumSystemLogger;
import io.jstach.svc.ServiceProvider;

/**
 * System Logger rainbow gum implementation. Unlike the SLF4J implementation
 * <strong>Rainbow Gum does not cache System Loggers</strong> by name!
 *
 * @see #INTIALIZE_RAINBOW_GUM_PROPERTY
 * @see JULConfigurator#JUL_DISABLE_PROPERTY
 */
@ServiceProvider(System.LoggerFinder.class)
public final class SystemLoggingFactory extends System.LoggerFinder {

	/**
	 * Initialization flag.
	 * @see InitOption
	 */
	public static final String INTIALIZE_RAINBOW_GUM_PROPERTY = LogProperties.ROOT_PREFIX + "systemlogger.intialize";

	private final RouterProvider routerProvider;

	/**
	 * No-Arg for Service Loader.
	 */
	public SystemLoggingFactory() {
		this(LogProperties.findGlobalProperties());

	}

	/**
	 * Values (case is ignored) for
	 * {@value SystemLoggingFactory#INTIALIZE_RAINBOW_GUM_PROPERTY}.
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
		 * Will reuse an existing rainbow gum or fail.
		 */
		REUSE;

		public static InitOption parse(String input) {
			if (input.isBlank())
				return FALSE;
			return InitOption.valueOf(input.toUpperCase(Locale.ROOT));
		}

	}

	SystemLoggingFactory(LogProperties properties) {
		JULConfigurator.install(properties);
		var opt = initOption(properties);
		this.routerProvider = switch (opt) {
			case FALSE -> n -> LogRouter.global();
			case TRUE -> new InitRouterProvider(RainbowGum::of);
			case REUSE -> new InitRouterProvider(() -> {
				var gum = RainbowGum.getOrNull();
				if (gum == null) {
					throw new IllegalStateException(
							"SystemLogging was configured to reuse a loaded Rainbow Gum but none was found. "
									+ INTIALIZE_RAINBOW_GUM_PROPERTY + "=" + opt);
				}
				return gum;
			});
		};
	}

	@Override
	public Logger getLogger(String name, Module module) {
		var router = routerProvider.router(name);
		return RainbowGumSystemLogger.of(name, router);
	}

	private static InitOption initOption(LogProperties properties) {
		return Property.builder() //
			.map(InitOption::parse)
			.orElse(InitOption.FALSE) //
			.build(INTIALIZE_RAINBOW_GUM_PROPERTY) //
			.get(properties) //
			.value();
	}

	private interface RouterProvider {

		LogRouter router(String loggerName);

	}

	private class InitRouterProvider implements RouterProvider {

		private final Supplier<RainbowGum> supplier;

		private volatile @Nullable RainbowGum gum = null;

		public InitRouterProvider(Supplier<RainbowGum> supplier) {
			super();
			this.supplier = supplier;
		}

		@Override
		public LogRouter router(String loggerName) {
			LogRouter router;
			RainbowGum gum = this.gum;
			if (gum == null) {
				gum = this.gum = supplier.get();
			}
			if (gum.config().changePublisher().isEnabled(loggerName)) {
				router = LogRouter.global();
			}
			else {
				var rootRouter = gum.router();
				var levelResolver = rootRouter.levelResolver();
				var level = levelResolver.resolveLevel(loggerName);
				var logger = rootRouter.route(loggerName, level);
				router = LogRouter.ofLevel(logger, level);
			}
			return router;
		}

	}

}
