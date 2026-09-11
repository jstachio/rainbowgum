package io.jstach.rainbowgum;

import static io.jstach.rainbowgum.spi.RainbowGumServiceProvider.findProviders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LevelResolver.LevelConfig;
import io.jstach.rainbowgum.LogConfig.ChangePublisher;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.PropertiesProvider;

/**
 * The configuration of a RainbowGum. In some other logging implementations this is called
 * "context".
 */
public sealed interface LogConfig extends LogProperty.PropertySupport {

	/**
	 * String key value properties.
	 * @return properties.
	 */
	@Override
	public LogProperties properties();

	/**
	 * Level resolver for resolving levels from logger names.
	 * @return level resolver.
	 */
	public LevelConfig levelResolver();

	/**
	 * Output provider that uses URI to find output.
	 * @return output provider.
	 */
	public LogOutputRegistry outputRegistry();

	/**
	 * Provides encoders by URI scheme.
	 * @return encoder registry.
	 */
	public LogEncoderRegistry encoderRegistry();

	/**
	 * Provides publishers by URI scheme.
	 * @return changePublisher registry.
	 */
	public LogPublisherRegistry publisherRegistry();

	/**
	 * Creates a builder for making LogConfig.
	 * @return builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * An event changePublisher to publish configuration changes.
	 * @return changePublisher.
	 */
	public ChangePublisher changePublisher();

	/**
	 * Service registry are custom services needed by plugins particularly during the
	 * initialization process.
	 * @return registry.
	 */
	public ServiceRegistry serviceRegistry();

	/**
	 * Alerts about problems with the logging system itself (as opposed to application
	 * logging routed through the {@link LogRouter}).
	 * @return alerts.
	 */
	public LogAlerts alerts();

	/**
	 * Metrics about the logging system itself (as opposed to application logging routed
	 * through the {@link LogRouter}).
	 * @return metrics.
	 */
	public LogMetrics metrics();

	/**
	 * Mixin for config support.
	 */
	interface ConfigSupport extends LogProperty.PropertySupport {

		/**
		 * Provides config.
		 * @return config.
		 */
		public LogConfig config();

		@Override
		default LogProperties properties() {
			return config().properties();
		}

	}

	/**
	 * Config Change Publisher. By default this is enabled with
	 * {@value LogProperties#GLOBAL_CHANGE_PROPERTY} set to <code>true</code> and then
	 * configuring which "logger" prefix will be allowed to change with
	 * {@value LogProperties#CHANGE_PREFIX} + {@value LogProperties#SEP} + logger name set
	 * to a list of {@link ChangeType} or <code>true</code>/<code>false</code> to enable
	 * or disable all changes.
	 */
	interface ChangePublisher {

		/**
		 * Subscribe to changes.
		 * @param consumer consumer.
		 */
		public void subscribe(Consumer<? super LogConfig> consumer);

		/**
		 * Publish that there has been changes.
		 */
		public void publish();

		/**
		 * Test to see if <strong>any</strong> changes are enabled for a logger.
		 * @param loggerName logger name.
		 * @return true if enabled.
		 */
		public boolean isEnabled(String loggerName);

		/**
		 * Returns what the logger is allowed to change.
		 * @param loggerName logger name.
		 * @return which things are allowed to change in the logger
		 */
		public Set<ChangeType> allowedChanges(String loggerName);

		/**
		 * Whether caller info should be computed for a logger. Unlike
		 * {@link ChangeType#LEVEL}, which is genuinely re-evaluated on every
		 * {@link #publish()}, this is resolved once when a logger is first created and
		 * never revisited afterward - it reflects a static per-logger capability, not
		 * something that changes at runtime, despite being parsed from the same
		 * {@value LogProperties#CHANGE_PREFIX} property and the same {@link ChangeType}
		 * set as {@code LEVEL} (deliberately, to avoid a second property lookup per
		 * logger name).
		 * @param loggerName logger name.
		 * @return true if caller info is enabled for this logger.
		 */
		default boolean callerInfoEnabled(String loggerName) {
			return allowedChanges(loggerName).contains(ChangeType.CALLER);
		}

		/**
		 * Changing type options.
		 */
		public enum ChangeType {

			/**
			 * The logger is allowed to change levels.
			 */
			LEVEL,
			/**
			 * Caller info is enabled for the logger. Despite being a member of this enum
			 * and parsed from the same property as {@link #LEVEL}, this is not actually
			 * re-evaluated on {@link ChangePublisher#publish()} the way {@code LEVEL} is
			 * - it is a static per-logger capability decided once. See
			 * {@link ChangePublisher#callerInfoEnabled(String)} for the accessor callers
			 * should actually use.
			 */
			CALLER;

			static Set<ChangeType> parse(List<String> value) {
				if (value.isEmpty()) {
					return Set.of();
				}
				var s = EnumSet.noneOf(ChangeType.class);
				for (var v : value) {
					if (v.equalsIgnoreCase("true")) {
						return EnumSet.allOf(ChangeType.class);
					}
					if (v.equalsIgnoreCase("false")) {
						return EnumSet.noneOf(ChangeType.class);
					}
					s.add(ChangeType.parse(v));
				}
				return s;
			}

			static ChangeType parse(String value) {
				String v = value.toUpperCase(Locale.ROOT);
				return ChangeType.valueOf(v);
			}

		}

	}

	/**
	 * Builder for LogConfig.
	 * <p>
	 * <strong>NOTE:</strong> The service loader is not used by default with this builder.
	 * If the automatic discovery of components is desired call
	 * {@link #serviceLoader(ServiceLoader)}.
	 */
	public static final class Builder extends LevelResolver.AbstractBuilder<Builder> {

		private @Nullable ServiceRegistry serviceRegistry;

		private @Nullable LogProperties logProperties;

		private @Nullable ServiceLoader<RainbowGumServiceProvider> serviceLoader;

		private final List<RainbowGumServiceProvider.Configurator> configurators = new ArrayList<>();

		private final List<RainbowGumServiceProvider.PropertiesProvider> propertiesProviders = new ArrayList<>();

		/**
		 * Default constructor
		 */
		private Builder() {
		}

		/**
		 * Sets log properties
		 * @param logProperties log properties.
		 * @return this.
		 */
		public Builder properties(LogProperties logProperties) {
			this.logProperties = logProperties;
			return this;
		}

		/**
		 * Sets service registry
		 * @param serviceRegistry service registry.
		 * @return this.
		 */
		public Builder serviceRegistry(ServiceRegistry serviceRegistry) {
			this.serviceRegistry = serviceRegistry;
			return this;
		}

		/**
		 * Add to configure LogConfig and ServiceRegistry.
		 * @param configurator will run on build.
		 * @return this.
		 */
		public Builder configurator(RainbowGumServiceProvider.Configurator configurator) {
			configurators.add(configurator);
			return this;
		}

		/**
		 * Add properties.
		 * @param propertiesProvider will run on build.
		 * @return this.
		 */
		public Builder propertiesProvider(RainbowGumServiceProvider.PropertiesProvider propertiesProvider) {
			propertiesProviders.add(propertiesProvider);
			return this;
		}

		/**
		 * Sets the service loader to use for loading components that were not set.
		 * @param serviceLoader loader to use for missing components.
		 * @return this.
		 */
		public Builder serviceLoader(ServiceLoader<RainbowGumServiceProvider> serviceLoader) {
			this.serviceLoader = serviceLoader;
			return this;
		}

		/**
		 * Sets a default service loader to use for loading components that were not set.
		 * This call will just call the {@link ServiceLoader} without providing a class
		 * loader.
		 * @return this.
		 */
		public Builder serviceLoader() {
			return this.serviceLoader(ServiceLoader.load(RainbowGumServiceProvider.class));
		}

		/**
		 * Configures the builder with a function for ergonomics.
		 * @param consumer passed the builder.
		 * @return this.
		 */
		public Builder with(Consumer<? super Builder> consumer) {
			consumer.accept(this);
			return this;
		}

		/**
		 * Builds LogConfig which will use the {@link ServiceLoader} if set to load
		 * missing components and if not set will use static defaults.
		 * @return log config
		 */
		public LogConfig build() {
			ServiceRegistry serviceRegistry = this.serviceRegistry;
			LogProperties logProperties = this.logProperties;

			var serviceLoader = this.serviceLoader;
			var configurators = this.configurators;
			if (serviceRegistry == null) {
				serviceRegistry = ServiceRegistry.of();
			}
			if (logProperties == null) {
				List<LogProperties> props = new ArrayList<>();
				for (var pp : propertiesProviders) {
					props.addAll(pp.provideProperties(serviceRegistry));
				}
				if (props.isEmpty() && serviceLoader != null) {
					props.addAll(provideProperties(serviceRegistry, serviceLoader));
				}
				logProperties = LogProperties.of(props, LogProperties.StandardProperties.SYSTEM_PROPERTIES);

			}
			/*
			 * Built before DefaultLogConfig itself (rather than left for
			 * DefaultLogConfig's constructor to create, as before) specifically so
			 * buildGlobalResolver below can hand LogAlerts to the global level resolver's
			 * alerting wrapper - the global resolver is built before a full LogConfig
			 * exists to pull config.alerts() from, so alerts (and metrics, via alerts'
			 * own listener wiring) are constructed directly here instead.
			 */
			LogAlerts alerts = new DefaultLogAlerts(LogAlerts.DEFAULT_CAPACITY);
			LogMetrics metrics = new DefaultLogMetrics();
			var levelResolver = this.buildGlobalResolver(logProperties, alerts);
			var config = new DefaultLogConfig(serviceRegistry, logProperties, levelResolver, alerts, metrics);
			if (serviceLoader != null) {
				configurators = new ArrayList<>(configurators);
				findProviders(serviceLoader, Configurator.class).forEach(configurators::add);
			}
			if (!configurators.isEmpty()) {
				/*
				 * TODO two-pass config: configurators run after config (and therefore the
				 * global level resolver above) already exists, so a configurator that
				 * contributes additional property sources or otherwise changes what the
				 * level resolver should have seen is invisible to it - the resolver was
				 * already built from logProperties as it stood before any configurator
				 * ran. A more correct design would run configurators first, then rebuild
				 * whatever is purely derived from properties (starting with the level
				 * resolver) a second time against the now-fully-configured LogConfig. Not
				 * done here - see todo.md.
				 */
				RainbowGumServiceProvider.Configurator.runConfigurators(configurators.stream(), config);
			}
			return config;
		}

		LevelConfig buildGlobalResolver(LogProperties logProperties, LogAlerts alerts) {
			LevelConfig levelResolver = LevelConfig
				.of(List.of(ConfigLevelResolver.of(logProperties), GroupLevelResolver.of(logProperties)));
			var config = buildLevelConfigOrNull();
			if (config != null) {
				levelResolver = LevelConfig.of(List.<LevelConfig>of(config, levelResolver));
			}
			return new AlertingLevelConfig(levelResolver, alerts);
		}

		private static List<LogProperties> provideProperties(ServiceRegistry registry,
				ServiceLoader<RainbowGumServiceProvider> loader) {
			List<LogProperties> props = findProviders(loader, PropertiesProvider.class)
				.flatMap(s -> s.provideProperties(registry).stream())
				.toList();
			return props;
		}

		@Override
		protected Builder self() {
			return this;
		}

	}

}

abstract class AbstractChangePublisher implements ChangePublisher {

	/*
	 * TODO Ideally this would be a concurrent weak hashmap. The reasoning is we may want
	 * a configuration where SLF4J loggers are not stored in concurrent hash map but some
	 * sort of GC friendly cache or not cached at all. If they subscribe to changes the
	 * loggers will never be GCed.
	 *
	 * Consequently the functional Consumer interface might not be the best choice.
	 */
	private final Collection<Consumer<? super LogConfig>> consumers = new CopyOnWriteArrayList<Consumer<? super LogConfig>>();

	/*
	 * Same shape as LevelResolver.java's CachedLevelResolver: allowedChanges(name) is
	 * only ever called once per never-before-seen logger name (RainbowGumLoggerFactory
	 * caches the resulting Logger forever afterward), so a ConcurrentHashMap here matches
	 * the exact call pattern that made a ConcurrentHashMap the right tradeoff there too.
	 */
	private final ConcurrentHashMap<String, Set<ChangeType>> changesCache = new ConcurrentHashMap<>();

	protected abstract LogConfig reload();

	protected abstract LogConfig config();

	@Override
	public void publish() {
		changesCache.clear();
		LogConfig config = reload();
		for (var c : consumers) {
			c.accept(config);
		}
	}

	@Override
	public void subscribe(Consumer<? super LogConfig> consumer) {
		consumers.add(consumer);
	}

	@Override
	public boolean isEnabled(String loggerName) {
		return !allowedChanges(loggerName).isEmpty();
	}

	@Override
	public Set<ChangeType> allowedChanges(String loggerName) {
		return changesCache.computeIfAbsent(loggerName, n -> {
			/*
			 * findOrNull walks the logger-name hierarchy (closest match wins, see its own
			 * javadoc) trying each candidate key in turn - per candidate, resolve it as a
			 * LogProperty and only return null (telling findOrNull to keep walking up)
			 * when the key is genuinely Missing there. A present-but-malformed value is a
			 * Result.Error, not a Missing - it must stop the walk right there rather than
			 * silently falling through to a less specific ancestor's value.
			 */
			LogProperty.Result<Set<ChangeType>> result = config().properties()
				.findOrNull(LogProperties.CHANGE_PREFIX, n, (props, fullKey) -> {
					LogProperty.Result<Set<ChangeType>> r = props.forKey(fullKey).ofList().map(ChangeType::parse);
					return r instanceof LogProperty.Result.Missing ? null : r;
				});
			if (result == null) {
				return Set.of();
			}
			return switch (result) {
				case LogProperty.Result.Success<Set<ChangeType>> s -> s.value();
				case LogProperty.Result.Error<Set<ChangeType>> e -> {
					/*
					 * A malformed logging.change.<name> value must not be able to break
					 * logging itself. ConcurrentHashMap#computeIfAbsent leaves nothing
					 * cached when the mapping function throws, so without catching this
					 * (here, via the Result.Error case rather than a try/catch -
					 * LogProperty.Result#map already turned ChangeType.parse's thrown
					 * exception into this Error, with a richer message than a manual
					 * catch would build) a bad property would re-parse and re-alert on
					 * every single allowedChanges(n) call for this logger name, not just
					 * once - same amplification risk CachedLevelResolver guards against
					 * for level properties. Fall back to "nothing allowed to change" and
					 * alert exactly once per logger name instead, since the fallback
					 * value is itself cached above just like a successful parse would be.
					 */
					config().alerts().error(AbstractChangePublisher.class, e.message(), e.cause());
					yield Set.of();
				}
				// Unreachable: the lambda above never returns a Missing result.
				case LogProperty.Result.Missing<Set<ChangeType>> m -> Set.of();
			};
		});
	}

}

enum IgnoreChangePublisher implements ChangePublisher {

	INSTANT;

	@Override
	public void subscribe(Consumer<? super LogConfig> consumer) {
	}

	@Override
	public void publish() {

	}

	@Override
	public boolean isEnabled(String loggerName) {
		return false;
	}

	@Override
	public Set<ChangeType> allowedChanges(String loggerName) {
		return Set.of();
	}

}

final class DefaultLogConfig implements LogConfig {

	private final ServiceRegistry registry;

	private final LogProperties properties;

	private final LevelConfig levelResolver;

	private final ChangePublisher changePublisher;

	private final LogOutputRegistry outputRegistry;

	private final LogEncoderRegistry encoderRegistry;

	private final LogPublisherRegistry publisherRegistry;

	private final LogAlerts alerts;

	private final LogMetrics metrics;

	DefaultLogConfig(ServiceRegistry registry, LogProperties properties, LevelConfig levelResolver, LogAlerts alerts,
			LogMetrics metrics) {
		super();
		this.registry = registry;
		this.properties = properties;
		this.levelResolver = levelResolver;
		this.alerts = alerts;
		this.metrics = metrics;
		boolean changeable = properties.forKey(LogProperties.GLOBAL_CHANGE_PROPERTY).ofBoolean().or(false).value();
		this.changePublisher = changeable ? new DefaultChangePublisher() : IgnoreChangePublisher.INSTANT;
		applyGlobalAppenderReentrantLockProperty(properties);
		this.outputRegistry = DefaultOutputRegistry.of(registry);
		this.encoderRegistry = DefaultEncoderRegistry.of();
		this.publisherRegistry = DefaultPublisherRegistry.of();
		this.alerts.addListener(event -> this.metrics.errorCounter(event.loggerName(), 1));
	}

	/*
	 * Deliberately mutates process-wide static state (AbstractLogAppender's field) rather
	 * than per-instance state - LogProperties#GLOBAL_APPENDER_REENTRANT_LOCK_PROPERTY is
	 * a global guarantee by design (see its javadoc), not a per-LogConfig setting.
	 * Last-writer-wins if multiple RainbowGum instances with different values coexist in
	 * one JVM - an accepted tradeoff for a property whose whole point is a process-wide
	 * guarantee independent of any single instance's configuration.
	 */
	private static void applyGlobalAppenderReentrantLockProperty(LogProperties properties) {
		AbstractLogAppender.forceReentrantLockAppenders = properties
			.forKey(LogProperties.GLOBAL_APPENDER_REENTRANT_LOCK_PROPERTY)
			.ofBoolean()
			.or(false)
			.value();
	}

	class DefaultChangePublisher extends AbstractChangePublisher {

		@Override
		protected LogConfig config() {
			return DefaultLogConfig.this;
		}

		@Override
		protected LogConfig reload() {
			levelResolver.clear();
			return DefaultLogConfig.this;
		}

	}

	@Override
	public LogProperties properties() {
		return properties;
	}

	@Override
	public LevelConfig levelResolver() {
		return this.levelResolver;
	}

	@Override
	public ServiceRegistry serviceRegistry() {
		return this.registry;
	}

	@Override
	public ChangePublisher changePublisher() {
		return this.changePublisher;
	}

	@Override
	public LogOutputRegistry outputRegistry() {
		return this.outputRegistry;
	}

	@Override
	public LogEncoderRegistry encoderRegistry() {
		return this.encoderRegistry;
	}

	@Override
	public LogPublisherRegistry publisherRegistry() {
		return this.publisherRegistry;
	}

	@Override
	public LogAlerts alerts() {
		return this.alerts;
	}

	@Override
	public LogMetrics metrics() {
		return this.metrics;
	}

}
