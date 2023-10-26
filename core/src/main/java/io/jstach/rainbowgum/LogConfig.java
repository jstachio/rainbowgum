package io.jstach.rainbowgum;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.jstach.rainbowgum.LevelResolver.LevelConfig;
import io.jstach.rainbowgum.LogConfig.ChangePublisher;
import io.jstach.rainbowgum.LogProperties.PropertyGetter;

public interface LogConfig {

	public LogProperties properties();

	public LevelConfig levelResolver();

	public Defaults defaults();

	default LogOutputProvider outputProvider() {
		return LogOutputProvider.of();
	}

	public static LogConfig of() {
		return LogConfig.of(ServiceRegistry.of(), LogProperties.StandardProperties.SYSTEM_PROPERTIES);
	}

	public static LogConfig of(ServiceRegistry registry, LogProperties properties) {
		return new DefaultLogConfig(registry, properties);
	}

	public ChangePublisher publisher();

	public ServiceRegistry registry();

	interface ChangePublisher {

		public void subscribe(Consumer<? super LogConfig> consumer);

		public void publish();

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
