package io.jstach.rainbowgum;

import static io.jstach.rainbowgum.spi.RainbowGumServiceProvider.findProviders;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LevelResolver.LevelConfig;
import io.jstach.rainbowgum.LogConfig.ChangePublisher;
import io.jstach.rainbowgum.LogEvent.Caller.CallerType;
import io.jstach.rainbowgum.annotation.EnumAlias;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.PropertiesProvider;

/**
 * The configuration of a RainbowGum. In some other logging implementations this is called
 * "context".
 * <p>
 * A config can be used by only one {@link RainbowGum}, even after that RainbowGum is
 * closed. Using it for another {@link RainbowGum.Builder} throws
 * {@link IllegalStateException}; build a new config instead.
 */
public sealed interface LogConfig extends LogProperty.PropertySupport {

	/**
	 * When to dump bootstrap alerts to the fail-safe output.
	 */
	public enum DebugModeType {

		/**
		 * Do not dump alerts beyond the normal alert startup policy.
		 */
		@EnumAlias("false")
		OFF,
		/**
		 * Dump the failure and collected alerts if building the config fails.
		 */
		ERROR,
		/**
		 * Dump collected alerts after every successful config build and on build failure.
		 */
		INFO,
		/**
		 * Dump collected alerts as in {@link #INFO}, and print a {@link LogReporter}
		 * report after Rainbow Gum starts.
		 */
		@EnumAlias("true")
		ALL,
		/**
		 * Everything {@link #ALL} does, then fails after Rainbow Gum starts if a property
		 * key set in a source that can list its keys (system properties, property files)
		 * was never read and is close to a key that was, which is almost always a typo.
		 * Meant for trying configuration on the command line and in CI.
		 */
		HELP;

		boolean checksUnusedKeys() {
			return this == ALL || this == HELP;
		}

		static DebugModeType parse(String value) {
			String v = value.toUpperCase(Locale.ROOT);
			return switch (v) {
				case "FALSE" -> OFF;
				case "TRUE" -> ALL;
				default -> LogProperty.enumValue(DebugModeType.class, value, "true", "false");
			};
		}

	}

	/**
	 * String key value properties.
	 * @return properties.
	 */
	@Override
	public LogProperties properties();

	/**
	 * Effective debug mode collected while this configuration was built. A system
	 * property value, when present, takes precedence over the builder setting.
	 * @return debug mode.
	 */
	public DebugModeType debugMode();

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
	 * Accounts for every logger name a facade implementation has vended.
	 * @return logger registry.
	 */
	public LoggerRegistry loggerRegistry();

	/**
	 * Accounts for logger names, and which {@link LoggerAPI} vended them, registered via
	 * {@link #registerLoggerName(LoggerAPI, String)}, deliberately without exposing the
	 * accumulated names/APIs themselves: the one supported way to see them is
	 * {@link LogReporter.Section#LOGGERS}/{@link LogReporter.Section#FACADES}.
	 */
	interface LoggerRegistry {

		/**
		 * Registers that a logger with this name exists. Idempotent: registering the same
		 * name more than once has no additional effect. The first time a given name is
		 * registered this also increments {@link LogMetrics#LOGGER_NAMES_METRIC} on
		 * {@link LogConfig#metrics()}, so the running total is cheap to observe (e.g. to
		 * catch unbounded logger name usage) without ever having to size the accumulated
		 * names themselves.
		 * @param api which facade this name came through.
		 * @param loggerName logger name.
		 */
		void registerLoggerName(LoggerAPI api, String loggerName);

	}

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
	 * {@value LogProperties#GLOBAL_CHANGE_PROPERTY} set to <code>true</code>, which then
	 * gates two independent, per-logger-name properties:
	 * {@value LogProperties#CHANGE_PREFIX} + {@value LogProperties#SEP} + logger name,
	 * set to a list of {@link ChangeType} or <code>true</code>/<code>false</code> to
	 * enable or disable all changes, and {@value LogProperties#CALLER_PREFIX} +
	 * {@value LogProperties#SEP} + logger name, set to a {@link CallerType} (or
	 * <code>true</code>/<code>false</code>) to control caller info. The two are resolved
	 * independently - one does not imply or override the other, even for overlapping
	 * logger name prefixes.
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
		 * Test to see if <strong>any</strong> changes are enabled for a logger - either
		 * {@link #allowedChanges(String)} is non-empty or {@link #callerType(String)} is
		 * not {@link CallerType#NONE}.
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
		 * The caller info strategy for a logger, resolved from
		 * {@value LogProperties#CALLER_PREFIX}, independently of
		 * {@link #allowedChanges(String)} /{@value LogProperties#CHANGE_PREFIX}. Unlike
		 * {@link ChangeType#LEVEL}, which is genuinely re-evaluated on every
		 * {@link #publish()}, this is resolved once when a logger is first created and
		 * never revisited afterward - it reflects a static per-logger capability, not
		 * something that changes at runtime.
		 * @param loggerName logger name.
		 * @return caller type, {@link CallerType#NONE} if not configured.
		 */
		public CallerType callerType(String loggerName);

		/**
		 * Whether caller info should be computed for a logger at all, regardless of which
		 * non-{@link CallerType#NONE} strategy.
		 * @param loggerName logger name.
		 * @return true if caller info is enabled for this logger.
		 */
		default boolean callerInfoEnabled(String loggerName) {
			return callerType(loggerName) != CallerType.NONE;
		}

		/**
		 * Changing type options.
		 */
		public enum ChangeType {

			/**
			 * No changes are allowed. Synonymous with <code>false</code> - never actually
			 * present in a {@link #allowedChanges(String)} result (that case is just the
			 * empty set); this exists so a single malformed-vs-explicitly-off token can
			 * be told apart while parsing, and so <code>false</code> is a real,
			 * documented enum member rather than only a magic string.
			 */
			@EnumAlias("false")
			NONE,
			/**
			 * The logger is allowed to change levels.
			 */
			LEVEL;

			static Set<ChangeType> parse(List<String> value) {
				if (value.isEmpty()) {
					return Set.of();
				}
				var s = EnumSet.noneOf(ChangeType.class);
				for (var v : value) {
					if (v.equalsIgnoreCase("true")) {
						return EnumSet.complementOf(EnumSet.of(ChangeType.NONE));
					}
					var ct = ChangeType.parse(v);
					if (ct == NONE) {
						return EnumSet.noneOf(ChangeType.class);
					}
					s.add(ct);
				}
				return s;
			}

			static ChangeType parse(String value) {
				String v = value.toUpperCase(Locale.ROOT);
				return switch (v) {
					case "FALSE" -> NONE;
					default -> LogProperty.enumValue(ChangeType.class, value, "true", "false");
				};
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

		private DebugModeType debugMode = DebugModeType.OFF;

		private LogAlerts.FailLevel alertsFail = LogAlerts.FailLevel.OFF;

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
		 * Sets the debug mode used when the system property
		 * {@value LogProperties#DEBUG_PROPERTY} is absent.
		 * @param debugMode debug mode.
		 * @return this.
		 */
		public Builder debug(DebugModeType debugMode) {
			this.debugMode = Objects.requireNonNull(debugMode);
			return this;
		}

		/**
		 * Sets the alert level at which starting fails, used when the property
		 * {@value LogProperties#ALERTS_FAIL_PROPERTY} is absent.
		 * @param failLevel fail level, default {@link LogAlerts.FailLevel#OFF}.
		 * @return this.
		 */
		public Builder alertsFail(LogAlerts.FailLevel failLevel) {
			this.alertsFail = Objects.requireNonNull(failLevel);
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
			var prePropertiesAlerts = new SwappableLogAlerts();
			LogAlerts dumpAlerts = prePropertiesAlerts;
			boolean startingAlerts = false;
			DebugModeType debug = LogProperties.StandardProperties.SYSTEM_PROPERTIES
				.forKey(LogProperties.DEBUG_PROPERTY)
				.ofString()
				.map(DebugModeType::parse)
				.or(this.debugMode)
				.validateNow(Builder.class);
			try {
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
						prePropertiesAlerts.info(LogConfig.class, "Loading properties from "
								+ LogReporter.Reportable.toString(pp, "unknown PropertiesProvider"));
						props.addAll(pp.provideProperties(serviceRegistry, prePropertiesAlerts));
					}
					if (props.isEmpty() && serviceLoader != null) {
						props.addAll(provideProperties(serviceRegistry, serviceLoader, prePropertiesAlerts));
					}
					/*
					 * LogProperties.of(List, LogProperties)'s fallback argument is only
					 * used when the list is empty, so SYSTEM_PROPERTIES has to be a real
					 * member of the list itself to always be included, not passed as that
					 * fallback: otherwise it silently disappears the moment any
					 * PropertiesProvider contributes anything at all (e.g.
					 * rainbowgum-micronaut5's own GLOBAL_CHANGE_PROPERTY layer), breaking
					 * every -D system property override for such an application with no
					 * warning.
					 */
					props.add(LogProperties.StandardProperties.SYSTEM_PROPERTIES);
					logProperties = LogProperties.of(props);

				}
				@Nullable UnusedKeyCheck unusedKeyCheck = null;
				/*
				 * Reads are recorded for the debug modes that report unused keys, and for
				 * logging.alerts.fail=warning, where a misspelled key's warning fails.
				 */
				if (debug.checksUnusedKeys()
						|| DefaultLogAlerts.failLevelOr(logProperties, alertsFail) == LogAlerts.FailLevel.WARNING) {
					unusedKeyCheck = unusedKeyCheck(logProperties);
					logProperties = recording(logProperties, unusedKeyCheck);
				}
				/*
				 * Built before DefaultLogConfig itself (rather than left for
				 * DefaultLogConfig's constructor to create, as before) specifically so
				 * buildGlobalResolver below can hand LogAlerts to the global level
				 * resolver's alerting wrapper - the global resolver is built before a
				 * full LogConfig exists to pull config.alerts() from, so alerts (and
				 * metrics, via alerts' own listener wiring) are constructed directly here
				 * instead. Capacity and logging.alerts.unobservedErrorsAction both come
				 * from logProperties as already resolved above - before any configurator
				 * runs, the same as everything else built directly in this method - see
				 * DefaultLogAlerts.of(...) for how the two are validated together.
				 */
				var alerts = DefaultLogAlerts.of(logProperties, alertsFail, debug == DebugModeType.HELP);
				LogMetrics metrics = new DefaultLogMetrics();
				var levelResolver = this.buildGlobalResolver(logProperties, alerts);
				var config = new DefaultLogConfig(serviceRegistry, logProperties, levelResolver, alerts, metrics, debug,
						unusedKeyCheck);
				// The config constructor installs the metrics listener before replay.
				prePropertiesAlerts.drainTo(alerts);
				dumpAlerts = alerts;
				if (serviceLoader != null) {
					configurators = new ArrayList<>(configurators);
					findProviders(serviceLoader, Configurator.class).forEach(configurators::add);
				}
				if (!configurators.isEmpty()) {
					for (var configurator : configurators) {
						alerts.info(LogConfig.class, "Adding configurator: "
								+ LogReporter.Reportable.toString(configurator, "unknown Configurator"));
					}
					/*
					 * TODO two-pass config: configurators run after config (and therefore
					 * the global level resolver above) already exists, so a configurator
					 * that contributes additional property sources or otherwise changes
					 * what the level resolver should have seen is invisible to it - the
					 * resolver was already built from logProperties as it stood before
					 * any configurator ran. A more correct design would run configurators
					 * first, then rebuild whatever is purely derived from properties
					 * (starting with the level resolver) a second time against the
					 * now-fully-configured LogConfig. Not done here - see todo.md.
					 */
					RainbowGumServiceProvider.Configurator.runConfigurators(configurators.stream(), config);
				}
				/*
				 * Deliberately last: alerts already resolved
				 * LogAlerts.UnobservedErrorsAction at construction above, but only now,
				 * once configurators have had their chance to record alerts and/or
				 * register a real Listener, does "should I report/refuse to start"
				 * actually mean anything - by construction nothing has had a chance to
				 * register one before this point.
				 */
				// Failure here uses the builder's debug dump, including informational
				// context.
				alerts.failIfAlerted("building the configuration");
				// start() itself reports the backlog before throwing for unobserved
				// errors.
				startingAlerts = true;
				alerts.start(config);
				if (debug == DebugModeType.INFO || debug.checksUnusedKeys()) {
					dumpSuccessfulBuild(alerts);
				}
				return config;
			}
			catch (RuntimeException | Error e) {
				if (debug != DebugModeType.OFF && !startingAlerts) {
					dumpBuildFailure(dumpAlerts, e);
				}
				throw e;
			}
		}

		/*
		 * Layers that can list their keys, highest priority first. System properties are
		 * always listed, even when the properties given do not include them, since a -D
		 * that nothing reads is exactly what this is meant to catch.
		 */
		private static UnusedKeyCheck unusedKeyCheck(LogProperties properties) {
			var sources = new ArrayList<UnusedKeyCheck.KeySource>();
			var system = LogProperties.StandardProperties.SYSTEM_PROPERTIES;
			sources.add(new UnusedKeyCheck.KeySource(system,
					() -> new TreeSet<>(System.getProperties().stringPropertyNames())));
			for (var layer : layers(properties)) {
				if (layer instanceof LogProperties.Listable listable) {
					sources.add(new UnusedKeyCheck.KeySource(listable, listable::keys));
				}
			}
			return new UnusedKeyCheck(sources);
		}

		/*
		 * Preserve composite precedence and mutability while recording every layer.
		 */
		private static LogProperties recording(LogProperties properties, UnusedKeyCheck check) {
			if (properties instanceof ListLogProperties list) {
				var wrapped = java.util.Arrays.stream(list.properties())
					.map(layer -> recording(layer, check))
					.toArray(LogProperties[]::new);
				return properties instanceof LogProperties.MutableLogProperties
						? new CompositeMutableLogProperties(wrapped) : new CompositeLogProperties(wrapped);
			}
			return check.wrap(properties);
		}

		private static List<LogProperties> layers(LogProperties properties) {
			var layers = new ArrayList<LogProperties>();
			if (properties instanceof ListLogProperties list) {
				for (var layer : list.properties()) {
					layers.addAll(layers(layer));
				}
			}
			else {
				layers.add(properties);
			}
			return layers;
		}

		private static void dumpSuccessfulBuild(LogAlerts alerts) {
			var events = alerts.dump();
			MetaLog.error(LogEventFactory.of(LogConfig.class.getName())
				.eventNoArg(Level.INFO, "LogConfig built; dumping " + events.size() + " alert(s)", null));
			for (var event : events) {
				MetaLog.error(event);
			}
		}

		/* Throwable.addSuppressed rejects self-suppression. */
		@SuppressWarnings("ReferenceEquality")
		private static void dumpBuildFailure(LogAlerts alerts, Throwable failure) {
			try {
				var events = alerts.dump();
				MetaLog.error(LogConfig.class, "LogConfig build failed; dumping " + events.size() + " alert(s)",
						failure);
				for (var event : events) {
					MetaLog.error(event);
				}
			}
			catch (Throwable dumpFailure) {
				if (dumpFailure != failure) {
					failure.addSuppressed(dumpFailure);
				}
			}
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
				ServiceLoader<RainbowGumServiceProvider> loader, LogAlerts alerts) {
			List<LogProperties> props = new ArrayList<>();
			for (var provider : findProviders(loader, PropertiesProvider.class).toList()) {
				alerts.info(LogConfig.class, "Loading properties from "
						+ LogReporter.Reportable.toString(provider, "unknown PropertiesProvider"));
				props.addAll(provider.provideProperties(registry, alerts));
			}
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

	/*
	 * Sibling to changesCache, not folded into it - CallerType is resolved from its own
	 * logging.caller.<name> property, independent of logging.change.<name>, so it gets
	 * its own map rather than a combined per-name struct (one map per concern).
	 */
	private final ConcurrentHashMap<String, CallerType> callerTypeCache = new ConcurrentHashMap<>();

	protected abstract LogConfig reload();

	protected abstract LogConfig config();

	@Override
	public void publish() {
		changesCache.clear();
		callerTypeCache.clear();
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
		return !allowedChanges(loggerName).isEmpty() || callerType(loggerName) != CallerType.NONE;
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
			try {
				return result.validateNow(ChangePublisher.class);
			}
			catch (LogProperty.ValidationException e) {
				/*
				 * A malformed logging.change.<name> value must not be able to break
				 * logging itself. ConcurrentHashMap#computeIfAbsent leaves nothing cached
				 * when the mapping function throws, so without catching this a bad
				 * property would re-parse and re-alert on every single allowedChanges(n)
				 * call for this logger name, not just once - same amplification risk
				 * CachedLevelResolver guards against for level properties. Fall back to
				 * "nothing allowed to change" and alert exactly once per logger name
				 * instead, since the fallback value is itself cached above just like a
				 * successful parse would be. validateNow's own Missing = failure
				 * semantics are moot here - the lambda above never returns a Missing
				 * result to it in the first place.
				 */
				config().alerts().error(ChangePublisher.class, e);
				return Set.of();
			}
		});
	}

	@Override
	public CallerType callerType(String loggerName) {
		return callerTypeCache.computeIfAbsent(loggerName, n -> {
			LogProperty.Result<CallerType> result = config().properties()
				.findOrNull(LogProperties.CALLER_PREFIX, n, (props, fullKey) -> {
					LogProperty.Result<CallerType> r = props.forKey(fullKey).ofString().map(CallerType::parse);
					return r instanceof LogProperty.Result.Missing ? null : r;
				});
			if (result == null) {
				return CallerType.NONE;
			}
			try {
				return result.validateNow(ChangePublisher.class);
			}
			catch (LogProperty.ValidationException e) {
				/*
				 * Same reasoning as allowedChanges()'s catch above: a malformed (or
				 * pre-split, e.g. the old "caller" token that used to live in
				 * logging.change.<name>) logging.caller.<name> value alerts once and
				 * falls back to CallerType.NONE, cached, rather than re-parsing and
				 * re-alerting on every call.
				 */
				config().alerts().error(ChangePublisher.class, e);
				return CallerType.NONE;
			}
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

	@Override
	public CallerType callerType(String loggerName) {
		return CallerType.NONE;
	}

}

final class DefaultLogConfig implements LogConfig {

	/*
	 * Present only in debug modes that check for unused keys.
	 */
	@Nullable UnusedKeyCheck unusedKeyCheck() {
		return unusedKeyCheck;
	}

	private final ServiceRegistry registry;

	private final LogProperties properties;

	private final DebugModeType debugMode;

	private final LevelConfig levelResolver;

	private final ChangePublisher changePublisher;

	private final LogOutputRegistry outputRegistry;

	private final LogEncoderRegistry encoderRegistry;

	private final LogPublisherRegistry publisherRegistry;

	private final LogAlerts alerts;

	private final LogMetrics metrics;

	private final LoggerRegistry loggerRegistry;

	/*
	 * Registered outputs/appenders, alert listeners, and service registry closeables are
	 * never reset when a RainbowGum closes, so a second RainbowGum on this config would
	 * fail on duplicate output names, flush/reopen the first one's closed appenders, and
	 * lose alert listeners. Ownership and built state change together in one CAS so
	 * concurrent build() calls cannot both win.
	 */
	private final AtomicReference<@Nullable Claim> claim = new AtomicReference<>();

	private record Claim(Object owner, boolean built) {
	}

	private final @Nullable UnusedKeyCheck unusedKeyCheck;

	DefaultLogConfig(ServiceRegistry registry, LogProperties properties, LevelConfig levelResolver, LogAlerts alerts,
			LogMetrics metrics, DebugModeType debugMode, @Nullable UnusedKeyCheck unusedKeyCheck) {
		super();
		this.unusedKeyCheck = unusedKeyCheck;
		this.registry = registry;
		this.properties = properties;
		this.debugMode = debugMode;
		this.levelResolver = levelResolver;
		this.alerts = alerts;
		this.metrics = metrics;
		this.loggerRegistry = new DefaultLoggerRegistry(metrics);
		boolean changeable = properties.forKey(LogProperties.GLOBAL_CHANGE_PROPERTY)
			.ofBoolean()
			.or(false)
			.validateNow(DefaultLogConfig.class);
		this.changePublisher = changeable ? new DefaultChangePublisher() : IgnoreChangePublisher.INSTANT;
		applyGlobalAppenderReentrantLockProperty(properties);
		applyGlobalThreadLocalDisabledProperty(properties);
		applyGlobalOptimizeProperty(properties);
		registry.put(KeyValuesContributors.Disabled.class, KeyValuesContributors.Disabled.of(properties));
		this.outputRegistry = DefaultOutputRegistry.of(registry);
		this.encoderRegistry = DefaultEncoderRegistry.of();
		this.publisherRegistry = DefaultPublisherRegistry.of();
		/*
		 * addInternalListener, not addListener: a passive in-process counter nobody has
		 * wired an exporter to does not count as "someone is watching" for
		 * LogAlerts.UnobservedErrorsAction's purposes - see DefaultLogAlerts's own
		 * comment on hasExternalListener. Config alerts is always a DefaultLogAlerts;
		 * SwappableLogAlerts is only passed to PropertiesProviders before config exists.
		 */
		((DefaultLogAlerts) this.alerts).addInternalListener(event -> {
			switch (event.level()) {
				case ERROR -> this.metrics.errorCounter(event.loggerName(), 1);
				case WARNING -> this.metrics.warnCounter(event.loggerName(), 1);
				default -> this.metrics.infoCounter(event.loggerName(), 1);
			}
		});
	}

	void bind(Object owner) {
		acquire(owner, false);
	}

	void markBuilt(Object owner) {
		acquire(owner, true);
	}

	@SuppressWarnings("ReferenceEquality") // owner is a specific builder instance
	private void acquire(Object owner, boolean build) {
		while (true) {
			var current = claim.get();
			if (current != null && current.owner() != owner) {
				throw new IllegalStateException("LogConfig is already used by another RainbowGum. "
						+ "A LogConfig can only be used by one RainbowGum, even after that RainbowGum is closed. "
						+ "Build a new LogConfig instead.");
			}
			if (current != null && current.built() && build) {
				throw new IllegalStateException("This builder already built a RainbowGum. "
						+ "A LogConfig can only be used by one RainbowGum. Build a new LogConfig instead.");
			}
			if (current != null && (current.built() || !build)) {
				return;
			}
			if (claim.compareAndSet(current, new Claim(owner, build))) {
				return;
			}
		}
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
			.validateNow(DefaultLogConfig.class);
	}

	/*
	 * An enum (TRUE/FALSE) rather than .ofBoolean() deliberately, unlike its sibling just
	 * above - a typo'd value here (e.g. "yse") fails loudly through the existing
	 * Property/Result machinery instead of silently resolving to "not disabled", which is
	 * the wrong failure mode for a property whose whole point is a hard, otherwise
	 * easy-to-silently-miss guarantee.
	 */
	private enum ThreadLocalDisabled {

		TRUE, FALSE;

		static ThreadLocalDisabled parse(String value) {
			return LogProperty.enumValue(ThreadLocalDisabled.class, value);
		}

	}

	/*
	 * Same last-writer-wins/process-wide-guarantee tradeoff as
	 * applyGlobalAppenderReentrantLockProperty just above - see that method's comment.
	 * rainbowgum-slf4j independently reads this same property key at its own SLF4J
	 * provider initialize() touchpoint to decide whether to disable MDC too - see
	 * LogProperties#GLOBAL_THREADLOCAL_DISABLED_PROPERTY's javadoc for why that is two
	 * independent readers rather than one shared flag.
	 */
	private static void applyGlobalThreadLocalDisabledProperty(LogProperties properties) {
		AbstractLogAppender.forceNoThreadLocalAppenders = properties
			.forKey(LogProperties.GLOBAL_THREADLOCAL_DISABLED_PROPERTY)
			.ofString()
			.map(ThreadLocalDisabled::parse)
			.or(ThreadLocalDisabled.FALSE)
			.validateNow(DefaultLogConfig.class) == ThreadLocalDisabled.TRUE;
	}

	/*
	 * Same enum-not-boolean reasoning as ThreadLocalDisabled above. Unlike the other two
	 * global properties this one is not a "force/guarantee" downgrade applied after a
	 * type is already resolved - it changes what the *default* resolves to when nothing
	 * more specific (an explicit appenderType(...)/useGetBytes(...) call, or a more
	 * specific property) was set, so it is read into the same kind of process-wide static
	 * flag but consulted at a different point (LogAppender.Builder#build(),
	 * LogEncoder.Builder#build()) - see LogProperties#GLOBAL_OPTIMIZE_PROPERTY's javadoc.
	 */
	private enum GlobalOptimize {

		TRUE, FALSE;

		static GlobalOptimize parse(String value) {
			return LogProperty.enumValue(GlobalOptimize.class, value);
		}

	}

	private static void applyGlobalOptimizeProperty(LogProperties properties) {
		AbstractLogAppender.globalOptimizeEnabled = properties.forKey(LogProperties.GLOBAL_OPTIMIZE_PROPERTY)
			.ofString()
			.map(GlobalOptimize::parse)
			.or(GlobalOptimize.FALSE)
			.validateNow(DefaultLogConfig.class) == GlobalOptimize.TRUE;
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
	public DebugModeType debugMode() {
		return debugMode;
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

	@Override
	public LoggerRegistry loggerRegistry() {
		return this.loggerRegistry;
	}

}

final class DefaultLoggerRegistry implements LogConfig.LoggerRegistry {

	private final Set<String> loggerNames = ConcurrentHashMap.newKeySet();

	private final Set<LoggerAPI> loggerAPIs = ConcurrentHashMap.newKeySet();

	private final LogMetrics metrics;

	DefaultLoggerRegistry(LogMetrics metrics) {
		this.metrics = metrics;
	}

	@Override
	public void registerLoggerName(LoggerAPI api, String loggerName) {
		Objects.requireNonNull(api);
		loggerAPIs.add(api);
		if (loggerNames.add(loggerName)) {
			metrics.infoCounter(LogMetrics.LOGGER_NAMES_METRIC, 1);
		}
	}

	/*
	 * Deliberately not on the LogConfig.LoggerRegistry interface itself (see its
	 * javadoc): DefaultLogReporter downcasts to this package-private type to reach it,
	 * the same pattern DefaultLogConfig's own constructor already uses to reach
	 * DefaultLogAlerts#addInternalListener.
	 */
	Set<String> loggerNames() {
		return Set.copyOf(loggerNames);
	}

	/**
	 * Every {@link LoggerAPI} registered so far via {@link #registerLoggerName}, not just
	 * {@link LoggerAPI.Standard}: an application-provided {@link LoggerAPI} works too.
	 * Same downcast-only visibility as {@link #loggerNames()}.
	 * @return immutable snapshot.
	 */
	Set<LoggerAPI> loggerAPIs() {
		return Set.copyOf(loggerAPIs);
	}

}
