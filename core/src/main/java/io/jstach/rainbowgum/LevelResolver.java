package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LevelResolver.LevelConfig;
import io.jstach.rainbowgum.LogProperties.PropertyGetter;

/**
 * Resolves levels from logger names.
 *
 * @apiNote {@linkplain Level#ALL} is considered to be equivalent to null.
 */
public interface LevelResolver {

	/**
	 * Determines what level the logger should be at.
	 * @param name logger name.
	 * @return level.
	 */
	public Level resolveLevel(String name);

	/**
	 * Convenience method that checks if the loggers resolved level is less than the
	 * passed in level.
	 * @param loggerName logger name.
	 * @param level level.
	 * @return true if logger is less than passed in level.
	 */
	default boolean isEnabled(String loggerName, Level level) {
		return resolveLevel(loggerName).getSeverity() <= level.getSeverity();
	}

	/**
	 * Clears any caching of levels.
	 */
	default void clear() {

	}

	/**
	 * A special resolver that has direct mappings of logger name to level via
	 * {@link #levelOrNull(String)}.
	 */
	@FunctionalInterface
	interface LevelConfig extends LevelResolver {

		@Nullable
		Level levelOrNull(String name);

		default Level defaultLevel() {
			var root = levelOrNull("");
			if (root == null) {
				return Level.ALL;
			}
			return root;
		}

		default Level resolveLevel(String name) {
			return LevelResolver.resolveLevel(this, name);
		}

		public static LevelConfig of(Collection<? extends LevelConfig> config) {
			if (config.isEmpty()) {
				return StaticLevelResolver.OFF;
			}
			else if (config.size() == 1) {
				return config.iterator().next();
			}
			return new CompositeLevelConfig(config.stream().toList().toArray(new LevelConfig[] {}));
		}

	}

	/**
	 * Level resolver builder.
	 * @return builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * A level resolver that is off.
	 * @return level resolver that is {@link Level#OFF}.
	 */
	public static LevelResolver off() {
		return StaticLevelResolver.OFF;
	}

	/**
	 * Abstract level resolver builder.
	 *
	 * @param <T> builder type.
	 */
	sealed abstract class AbstractBuilder<T> permits Builder, LogRouter.Router.Builder {

		protected List<LevelConfig> resolvers = new ArrayList<>();

		protected Map<String, Level> levels = new LinkedHashMap<>();

		public T level(Level level, String loggerName) {
			levels.put(loggerName, level);
			return self();
		}

		public T level(Level level) {
			levels.put("", level);
			return self();
		}

		public T levelResolver(LevelConfig resolver) {
			resolvers.add(resolver);
			return self();
		}

		protected LevelResolver buildLevelResolver() {
			return buildLevelResolver(null);
		}

		protected LevelResolver buildLevelResolver(@Nullable LevelConfig globalLevelResolver) {
			var copyLevels = new LinkedHashMap<>(levels);
			boolean noBuilderLevels = copyLevels.isEmpty();

			var copyResolvers = new ArrayList<>(resolvers);
			if (!copyLevels.isEmpty()) {
				copyResolvers.add(0, InternalLevelResolver.of(copyLevels));
			}
			if (globalLevelResolver != null) {
				copyResolvers.add(globalLevelResolver);
			}

			if (noBuilderLevels) {
				copyResolvers.add(StaticLevelResolver.INFO);
			}

			var combined = LevelConfig.of(copyResolvers);

			return combined;
		}

		protected abstract T self();

	}

	/**
	 * Level resolver builder.
	 */
	public final class Builder extends AbstractBuilder<Builder> {

		/**
		 * Builds a level resolver.
		 * @return level resolver.
		 */
		public LevelResolver build() {
			return buildLevelResolver();
		}

		@Override
		protected Builder self() {
			return this;
		}

	}

	private static Level resolveLevel(LevelConfig levelBindings, String name) {
		Function<String, @Nullable Level> f = s -> allToNull(levelBindings.levelOrNull(s));
		var level = LogProperties.searchPath(name, f);
		if (level != null) {
			return level;
		}
		return levelBindings.defaultLevel();
	}

	private static @Nullable Level allToNull(@Nullable Level level) {
		if (level == null || level == Level.ALL) {
			return null;
		}
		return level;
	}

}

interface InternalLevelResolver {

	public static LevelResolver of(Collection<? extends LevelResolver> resolvers) {
		if (resolvers.isEmpty()) {
			return LevelResolver.off();
		}
		else if (resolvers.size() == 1) {
			return resolvers.iterator().next();
		}
		return CompositeLevelResolver.of(resolvers);
	}

	public static LevelConfig of(Map<String, Level> levels) {
		if (levels.isEmpty()) {
			return StaticLevelResolver.ALL;
		}
		else if (levels.size() == 1) {
			var e = levels.entrySet().iterator().next();
			return new SingleLevelResolver(e.getKey(), e.getValue());
		}
		return new MapLevelResolver(levels);
	}

	public static LevelResolver of(Level level) {
		return switch (level) {
			case INFO -> StaticLevelResolver.INFO;
			case ALL -> StaticLevelResolver.ALL;
			case DEBUG -> StaticLevelResolver.DEBUG;
			case ERROR -> StaticLevelResolver.ERROR;
			case OFF -> StaticLevelResolver.OFF;
			case TRACE -> StaticLevelResolver.TRACE;
			case WARNING -> StaticLevelResolver.WARNING;
		};
	}

	public static LevelResolver cached(LevelResolver resolver) {
		if (resolver instanceof StaticLevelResolver) {
			return resolver;
		}
		return new CachedLevelResolver(resolver);
	}

}

enum StaticLevelResolver implements LevelResolver, LevelConfig {

	INFO(Level.INFO), OFF(Level.OFF), ALL(Level.ALL), DEBUG(Level.DEBUG), ERROR(Level.ERROR), WARNING(Level.WARNING),
	TRACE(Level.TRACE);

	private final Level level;

	private StaticLevelResolver(Level level) {
		this.level = level;
	}

	public Level levelOrNull(String name) {
		if ("".equals(name)) {
			return this.level;
		}
		return null;
	}

	@Override
	public Level resolveLevel(String name) {
		return this.level;
	}

}

record SingleLevelResolver(String name, Level level) implements LevelConfig {

	@Override
	public @Nullable Level levelOrNull(String name) {
		if (this.name.equals(name)) {
			return this.level;
		}
		return null;
	}

}

record MapLevelResolver(Map<String, Level> levels) implements LevelConfig {
	@Override
	public Level levelOrNull(String name) {
		return levels.get(name);
	}
}

record CompositeLevelConfig(LevelConfig[] levelConfigs) implements LevelConfig {

	@Override
	public @Nullable Level levelOrNull(String name) {
		for (var c : levelConfigs) {
			var level = c.levelOrNull(name);
			if (level != null) {
				return level;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + Arrays.asList(levelConfigs);
	}

}

record CompositeLevelResolver(LevelResolver[] resolvers, Level defaultLevel) implements LevelResolver {

	public static LevelResolver of(Collection<? extends LevelResolver> resolvers) {
		List<LevelResolver> resolved = new ArrayList<>();

		for (var r : resolvers) {
			if (r instanceof CompositeLevelResolver cr) {
				for (var j : cr.resolvers()) {
					resolved.add(j);
				}
			}
			else {
				resolved.add(r);
			}
		}
		LevelResolver[] array = resolved.toArray(new LevelResolver[] {});
		return new CompositeLevelResolver(array, Level.OFF);
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + Arrays.asList(resolvers);
	}

	@Override
	public Level resolveLevel(String name) {
		Level current = Level.ALL;
		for (var resolver : resolvers) {
			var level = resolver.resolveLevel(name);
			if (level == Level.TRACE) {
				return level;
			}
			if (current == Level.ALL) {
				current = level;
				continue;
			}
			if (current.getSeverity() > level.getSeverity()) {
				current = level;
			}
		}
		if (current == Level.ALL) {
			current = defaultLevel;
		}
		return current;
	}

	public void clear() {
		for (var resolver : resolvers) {
			resolver.clear();
		}
	}

}

/*
 * Revisit perf. ConcurrentHashMap based on benchmarks is incredibly slow for most cases
 * and looping can be faster.
 *
 * Luckily level resolution is only called when a logger is created and the loggers are
 * cached.
 */
final class CachedLevelResolver implements LevelResolver {

	private final LevelResolver levelResolver;

	private final ConcurrentHashMap<String, Level> levelCache = new ConcurrentHashMap<>();

	public CachedLevelResolver(LevelResolver levelResolver) {
		super();
		this.levelResolver = levelResolver;
	}

	@Override
	public Level resolveLevel(String name) {
		var level = levelCache.get(name);
		if (level != null) {
			return level;
		}
		return levelCache.computeIfAbsent(name, n -> levelResolver.resolveLevel(name));
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + "[" + levelResolver + "]";
	}

	@Override
	public void clear() {
		levelCache.clear();
		levelResolver.clear();
	}

}

final class ConfigLevelResolver implements LevelConfig {

	private final LogProperties properties;

	public ConfigLevelResolver(LogProperties properties) {
		super();
		this.properties = properties;
	}

	static PropertyGetter<Level> levelExtractor = PropertyGetter.of()
		.withPrefix(LogProperties.LEVEL_PREFIX)
		.map(s -> s.toUpperCase(Locale.ROOT))
		.map(Level::valueOf);

	public @Nullable Level levelOrNull(String name) {
		return levelExtractor.property(name).get(properties).valueOrNull();
	}

}