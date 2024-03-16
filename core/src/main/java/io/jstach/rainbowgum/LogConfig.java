package io.jstach.rainbowgum;

import static io.jstach.rainbowgum.spi.RainbowGumServiceProvider.findProviders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LevelResolver.LevelConfig;
import io.jstach.rainbowgum.LogConfig.ChangePublisher;
import io.jstach.rainbowgum.LogProperties.Property;
import io.jstach.rainbowgum.LogProperties.PropertyGetter;
import io.jstach.rainbowgum.LogProperties.PropertyGetter.RequiredPropertyGetter;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.PropertiesProvider;

/**
 * The configuration of a RainbowGum. In some other logging implementations this is called
 * "context".
 */
public sealed interface LogConfig {

	/**
	 * String key value properties.
	 * @return properties.
	 */
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
	 * Provides appenders by name.
	 * @return appender registry.
	 */
	public LogAppenderRegistry appenderRegistry();

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
	 * A factory that may need config to provide.
	 *
	 * @param <T> component
	 */
	@FunctionalInterface
	public interface Provider<T> {

		/*
		 * TODO maybe make this sealed with sub provider types?
		 */

		/**
		 * Creates the component from config. The component is not always guaranteed to be
		 * new object.
		 * @param name config name of the parent component.
		 * @param config config.
		 * @return component.
		 */
		T provide(String name, LogConfig config);

		/**
		 * Convenience for flattening nullable providers.
		 * @param <U> component
		 * @param name name of parent component and can be ignored if not needed.
		 * @param provider nullable provider
		 * @param config config used to provide if not null.
		 * @return maybe null component.
		 */
		@SuppressWarnings("exports")
		public static <U> @Nullable U provideOrNull(String name, @Nullable Provider<U> provider, LogConfig config) {
			if (provider == null) {
				return null;
			}
			return provider.provide(name, config);
		}

		/**
		 * Creates a provider of instance that is already configured.
		 * @param <U> component
		 * @param instance component instance.
		 * @return this.
		 */
		public static <U> Provider<U> of(U instance) {
			return (n, c) -> instance;
		}

	}

	/**
	 * Config Change Publisher.
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
		 * Changing type options.
		 */
		public enum ChangeType {

			/**
			 * The logger is allowed to change levels.
			 */
			LEVEL,
			/**
			 * The logger is allowed to change caller info.
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
	public static final class Builder {

		private @Nullable ServiceRegistry serviceRegistry;

		private @Nullable LogProperties logProperties;

		private @Nullable ServiceLoader<RainbowGumServiceProvider> serviceLoader;

		private final List<RainbowGumServiceProvider.Configurator> configurators = new ArrayList<>();

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
				if (serviceLoader != null) {
					logProperties = provideProperties(serviceRegistry, serviceLoader);
				}
				else {
					logProperties = LogProperties.StandardProperties.SYSTEM_PROPERTIES;
				}
			}
			var config = new DefaultLogConfig(serviceRegistry, logProperties);
			if (configurators.isEmpty() && serviceLoader != null) {
				configurators = findProviders(serviceLoader, Configurator.class).toList();
			}
			if (!configurators.isEmpty()) {
				RainbowGumServiceProvider.Configurator.runConfigurators(configurators.stream(), config);
			}
			return config;
		}

		private static LogProperties provideProperties(ServiceRegistry registry,
				ServiceLoader<RainbowGumServiceProvider> loader) {
			List<LogProperties> props = findProviders(loader, PropertiesProvider.class)
				.flatMap(s -> s.provideProperties(registry).stream())
				.toList();
			return LogProperties.of(props, LogProperties.StandardProperties.SYSTEM_PROPERTIES);
		}

	}

}

abstract class AbstractChangePublisher implements ChangePublisher {

	static final RequiredPropertyGetter<Set<ChangeType>> changeSetting = PropertyGetter.of()
		.withSearch(LogProperties.CHANGE_PREFIX)
		.toList()
		.map(s -> ChangeType.parse(s))
		.orElse(Set.of());

	private final Collection<Consumer<? super LogConfig>> consumers = new CopyOnWriteArrayList<Consumer<? super LogConfig>>();

	protected abstract LogConfig reload();

	protected abstract LogConfig config();

	@Override
	public void publish() {
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
		return changeSetting.get(config().properties(), loggerName).value();
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

	private final LogAppenderRegistry appenderRegistry;

	private final LogEncoderRegistry encoderRegistry;

	private final LogPublisherRegistry publisherRegistry;

	public DefaultLogConfig(ServiceRegistry registry, LogProperties properties) {
		super();
		this.registry = registry;
		this.properties = properties;
		this.levelResolver = ConfigLevelResolver.of(properties);
		boolean changeable = Property.builder() //
			.toBoolean()
			.orElse(false)
			.build(LogProperties.GLOBAL_CHANGE_PROPERTY)
			.get(properties)
			.value();
		this.changePublisher = changeable ? new DefaultChangePublisher() : IgnoreChangePublisher.INSTANT;
		this.outputRegistry = LogOutputRegistry.of();
		this.appenderRegistry = LogAppenderRegistry.of();
		this.encoderRegistry = LogEncoderRegistry.of();
		this.publisherRegistry = LogPublisherRegistry.of();
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
	public LogAppenderRegistry appenderRegistry() {
		return this.appenderRegistry;
	}

	@Override
	public LogEncoderRegistry encoderRegistry() {
		return this.encoderRegistry;
	}

	@Override
	public LogPublisherRegistry publisherRegistry() {
		return this.publisherRegistry;
	}

}
