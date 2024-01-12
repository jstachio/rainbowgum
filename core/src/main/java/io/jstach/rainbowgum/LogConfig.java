package io.jstach.rainbowgum;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LevelResolver.LevelConfig;
import io.jstach.rainbowgum.LogConfig.ChangePublisher;
import io.jstach.rainbowgum.LogProperties.PropertyGetter;

/**
 * The configuration of a RainbowGum. In some other logging implementations this is called
 * "context".
 */
public sealed interface LogConfig {

	/**
	 * String kv properties.
	 * @return properties.
	 */
	public LogProperties properties();

	/**
	 * Level resolver for resolving levels from logger names.
	 * @return level resolver.
	 */
	public LevelConfig levelResolver();

	/**
	 * Registered formatters.
	 * @return formatter registry.
	 */
	public LogFormatterRegistry formatterRegistry();

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
	 * Provides encoders by name.
	 * @return encoder registry.
	 */
	public LogEncoderRegistry encoderRegistry();

	/**
	 * Finds an output from a URI.
	 * @param uri uri.
	 * @param name output name.
	 * @return output.
	 * @throws IOException if output fails fast
	 */
	default LogOutput output(URI uri, String name) throws IOException {
		return outputRegistry().output(uri, name, properties());
	}

	/**
	 * Creates default log config backed by system properties.
	 * @return config.
	 */
	public static LogConfig of() {
		return LogConfig.of(ServiceRegistry.of(), LogProperties.StandardProperties.SYSTEM_PROPERTIES);
	}

	/**
	 * Creates config.
	 * @param registry service registry.
	 * @param properties properties.
	 * @return config.
	 */
	public static LogConfig of(ServiceRegistry registry, LogProperties properties) {
		return new DefaultLogConfig(registry, properties);
	}

	/**
	 * Creates a builder for making LogConfig.
	 * @return builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * An event publisher to publish configuration changes.
	 * @return publisher.
	 */
	public ChangePublisher publisher();

	/**
	 * Service registry are custom services needed by plugins particularly during the
	 * initialization process.
	 * @return registry.
	 */
	public ServiceRegistry serviceRegistry();

	/**
	 * A factory that may need config to provide. <strong>Most components implements this
	 * interface so that you can use already created instances</strong>.
	 *
	 * @param <T> component
	 */
	@FunctionalInterface
	public interface Provider<T> {

		/**
		 * Creates the component from config. The component is not always guaranteed to be
		 * new object.
		 * @param config config.
		 * @return component.
		 */
		T provide(LogConfig config);

		/**
		 * Convenience for flattening nullable providers.
		 * @param <U> component
		 * @param provider nullable provider
		 * @param config config used to provide if not null.
		 * @return maybe null component.
		 */
		@SuppressWarnings("exports")
		public static <U> @Nullable U provideOrNull(@Nullable Provider<U> provider, LogConfig config) {
			if (provider == null) {
				return null;
			}
			return provider.provide(config);
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
		 * Test to see if changes are enabled for a logger.
		 * @param loggerName logger name.
		 * @return true if enabled.
		 */
		public boolean isEnabled(String loggerName);

	}

	/**
	 * Builder for LogConfig.
	 */
	public static final class Builder {

		private @Nullable ServiceRegistry serviceRegistry;

		private @Nullable LogProperties logProperties;

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
		public Builder logProperties(LogProperties logProperties) {
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
		 * Builds LogConfig using defaults on missing properties.
		 * @return log config
		 */
		public LogConfig build() {
			ServiceRegistry serviceRegistry = this.serviceRegistry;
			LogProperties logProperties = this.logProperties;
			if (serviceRegistry == null) {
				serviceRegistry = ServiceRegistry.of();
			}
			if (logProperties == null) {
				logProperties = LogProperties.StandardProperties.SYSTEM_PROPERTIES;
			}
			return LogConfig.of(serviceRegistry, logProperties);
		}

	}

}

abstract class AbstractChangePublisher implements ChangePublisher {

	static final PropertyGetter<Boolean> changeSetting = PropertyGetter.of()
		.withSearch(LogProperties.CHANGE_PREFIX)
		.map(s -> Boolean.parseBoolean(s))
		.orElse(false);

	private final Collection<Consumer<? super LogConfig>> consumers = new CopyOnWriteArrayList<Consumer<? super LogConfig>>();

	protected abstract LogConfig _config();

	@Override
	public void publish() {
		LogConfig config = _config();
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
		return changeSetting.value(_config().properties(), loggerName);

	}

}

final class DefaultLogConfig implements LogConfig {

	private final ServiceRegistry registry;

	private final LogProperties properties;

	private final LevelConfig levelResolver;

	private final ChangePublisher publisher;

	private final LogOutputRegistry outputRegistry;

	private final LogFormatterRegistry formatterRegistry;

	private final LogAppenderRegistry appenderRegistry;

	private final LogEncoderRegistry encoderRegistry;

	public DefaultLogConfig(ServiceRegistry registry, LogProperties properties) {
		super();
		this.registry = registry;
		this.properties = properties;
		this.levelResolver = new ConfigLevelResolver(properties);
		this.publisher = new AbstractChangePublisher() {
			@Override
			protected LogConfig _config() {
				return DefaultLogConfig.this;
			}
		};
		this.outputRegistry = LogOutputRegistry.of();
		this.formatterRegistry = LogFormatterRegistry.of();
		this.appenderRegistry = LogAppenderRegistry.of();
		this.encoderRegistry = LogEncoderRegistry.of();
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
	public ChangePublisher publisher() {
		return this.publisher;
	}

	@Override
	public LogOutputRegistry outputRegistry() {
		return this.outputRegistry;
	}

	@Override
	public LogFormatterRegistry formatterRegistry() {
		return this.formatterRegistry;
	}

	@Override
	public LogAppenderRegistry appenderRegistry() {
		return this.appenderRegistry;
	}

	@Override
	public LogEncoderRegistry encoderRegistry() {
		return this.encoderRegistry;
	}

}
