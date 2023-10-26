package io.jstach.rainbowgum;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.jstach.rainbowgum.LevelResolver.LevelConfig;
import io.jstach.rainbowgum.LogConfig.ChangePublisher;
import io.jstach.rainbowgum.LogProperties.PropertyGetter;

/**
 * The configuration of a RainbowGum. In some other logging implementations this is called
 * "context".
 */
public interface LogConfig {

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
	 * Special defaults. Internal for now.
	 * @return defaults.
	 */
	public Defaults defaults();

	/**
	 * Output provider that uses URI to find output.
	 * @return output provider.
	 */
	default LogOutputProvider outputProvider() {
		return LogOutputProvider.of();
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
	 * An event publisher to publish configuration changes.
	 * @return publisher.
	 */
	public ChangePublisher publisher();

	/**
	 * Service registry are custom services needed by plugins particularly during the
	 * initialization process.
	 * @return registry.
	 */
	public ServiceRegistry registry();

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
		return changeSetting.require(_config().properties(), loggerName);

	}

}

class DefaultLogConfig implements LogConfig {

	private final ServiceRegistry registry;

	private final LogProperties properties;

	private final LevelConfig levelResolver;

	private final Defaults defaults;

	private final ChangePublisher publisher;

	public DefaultLogConfig(ServiceRegistry registry, LogProperties properties) {
		super();
		this.registry = registry;
		this.properties = properties;
		this.levelResolver = new ConfigLevelResolver(properties);
		this.defaults = new Defaults(properties);
		this.publisher = new AbstractChangePublisher() {
			@Override
			protected LogConfig _config() {
				return DefaultLogConfig.this;
			}
		};
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
	public Defaults defaults() {
		return defaults;
	}

	@Override
	public ServiceRegistry registry() {
		return this.registry;
	}

	@Override
	public ChangePublisher publisher() {
		return this.publisher;
	}

}
