package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LevelResolver.LevelConfig;
import io.jstach.rainbowgum.LogProperty.Property;
import io.jstach.rainbowgum.LogProperty.PropertyGetter;

/**
 * Resolves levels from logger names.
 *
 * @apiNote {@linkplain Level#ALL} is considered to be equivalent to null or unspecified.
 */
public interface LevelResolver {

	/**
	 * Determines what level the logger should be at. {@linkplain Level#ALL} is to signal
	 * that nothing was resolved.
	 * @param name logger name.
	 * @return resolved level or {@link Level#ALL} if unable to resolve.
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
		return checkEnabled(level, resolveLevel(loggerName));
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

		/**
		 * Returns a level exactly matching a logger name.
		 * @param name logger name.
		 * @return level or <code>null</code>.
		 */
		@Nullable
		Level levelOrNull(String name);

		/**
		 * The default level if no level is found. By default if not overridden this is
		 * calculated by calling {@link #levelOrNull(String)} with an empty String and if
		 * it returns <code>null</code> ALL is returned.
		 * @return level
		 */
		default Level defaultLevel() {
			var root = levelOrNull("");
			if (root == null) {
				return Level.ALL;
			}
			return root;
		}

		/**
		 * Resolves a level by walking up the dotted path by calling
		 * {@link #levelOrNull(String)} which follows <a href=
		 * "https://logback.qos.ch/manual/architecture.html#effectiveLevel">Logbacks
		 * Effective Level</a>. {@inheritDoc}
		 */
		@Override
		default Level resolveLevel(String name) {
			return resolveLevel(this, name);
		}

		/**
		 * Creates a level config from a list of level configs.
		 * @param config configs.
		 * @return coalesced config.
		 */
		public static LevelConfig of(Collection<? extends LevelConfig> config) {
			if (config.isEmpty()) {
				return StaticLevelResolver.OFF;
			}
			else if (config.size() == 1) {
				return Objects.requireNonNull(config.iterator().next());
			}
			return new CompositeLevelConfig(config.stream().flatMap(c -> {
				if (c instanceof CompositeLevelConfig cc) {
					return Stream.of(cc.levelConfigs());
				}
				return Stream.of(c);
			}).distinct().toList().toArray(new LevelConfig[] {}));
		}

		private static Level resolveLevel(LevelConfig levelBindings, String name) {
			Function<String, @Nullable Level> f = s -> allToNull(levelBindings.levelOrNull(s));
			var level = LogProperties.findUpPathOrNull(name, f);
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
	 * Statically checks if requested level is greater than or equal to the logger level
	 * based on LevelResolver rules.
	 * <p>
	 * Rules are:
	 * <ol>
	 * <li><code>OFF</code> for either level or logger level is <code>false</code></li>
	 * <li><code>ALL</code> is converted to <code>TRACE</code> for both level and
	 * loggerLevel</li>
	 * </ol>
	 * @param level if OFF will always return false.
	 * @param loggerLevel if OFF will always return false.
	 * @return true if level is greater or equal to loggerLevel unless either is OFF.
	 * @apiNote this method is mainly used for testing purposes and is not designed with
	 * performance in mind. In facades it is better to get an integer and use that for
	 * comparison.
	 */
	public static boolean checkEnabled(System.Logger.Level level, System.Logger.Level loggerLevel) {
		if (level == System.Logger.Level.OFF) {
			return false;
		}
		if (loggerLevel == System.Logger.Level.OFF) {
			return false;
		}
		level = normalizeLevel(level);
		loggerLevel = normalizeLevel(loggerLevel);
		return level.getSeverity() >= loggerLevel.getSeverity();
	}

	/**
	 * Parses a Level from a string. ALL is converted to TRACE.
	 * @param input level like String where case is ignored and SLF4J and JUL level names
	 * are supported as well.
	 * @return level
	 * @throws IllegalArgumentException if the input is not recognized as a level.
	 */
	public static Level parseLevel(String input) throws IllegalArgumentException {
		input = input.toUpperCase(Locale.ROOT);
		return switch (input) {
			case "ALL" -> Level.TRACE;
			case "TRACE", "FINEST" -> Level.TRACE;
			case "DEBUG", "FINE" -> Level.DEBUG;
			case "INFO" -> Level.INFO;
			case "WARN", "WARNING" -> Level.WARNING;
			case "ERROR", "SEVERE" -> Level.ERROR;
			case "OFF" -> Level.OFF;
			default -> {
				throw new IllegalArgumentException("Cannot parse Level from input. input='" + input + "'");
			}
		};
	}

	/**
	 * Will convert ALL to TRACE.
	 * @param level level
	 * @return a Level.
	 */
	public static Level normalizeLevel(Level level) {
		return Builder.allToTrace(level);
	}

	/**
	 * Abstract level resolver builder.
	 *
	 * @param <T> builder type.
	 * @apiNote while this builder class is public it is effectively package protected per
	 * the constructor on purpose. It is public to allow Javadoc to propagate correctly.
	 */
	abstract class AbstractBuilder<T> {

		/**
		 * level configs.
		 */
		protected List<LevelConfig> levelConfigs = new ArrayList<>();

		/**
		 * levels
		 */
		protected Map<String, Level> levels = new LinkedHashMap<>();

		/**
		 * Do nothing constructor.
		 */
		AbstractBuilder() {
		}

		/**
		 * Sets a logger to a level.
		 * @param level level.
		 * @param loggerName logger name.
		 * @return this builder.
		 */
		public T level(Level level, String loggerName) {
			levels.put(loggerName, level);
			return self();
		}

		/**
		 * Sets a level for all loggers.
		 * @param level level.
		 * @return this builder.
		 */
		public T level(Level level) {
			levels.put("", level);
			return self();
		}

		/**
		 * Adds a level config.
		 * @param resolver level config.
		 * @return this builder.
		 */
		public T config(LevelConfig resolver) {
			levelConfigs.add(resolver);
			return self();
		}

		/**
		 * Adds a level config based on properties and will use the prefix as the root.
		 * @param properties properties containing levels with prefix.
		 * @param prefix for example <code>logging.level</code>
		 * @return this.
		 */
		public T config(LogProperties properties, String prefix) {
			config(ConfigLevelResolver.of(properties, prefix));
			return config(GroupLevelResolver.of(properties, prefix));
		}

		/**
		 * Builds the level config based on {@link #levelConfigs} and {@link #levels}.
		 * @return level config or <code>null</code>.
		 */
		protected @Nullable LevelConfig buildLevelConfigOrNull() {
			var copyLevels = new LinkedHashMap<>(levels);
			var copyResolvers = new ArrayList<>(levelConfigs);
			if (copyLevels.isEmpty() && copyResolvers.isEmpty()) {
				return null;
			}
			if (!copyLevels.isEmpty()) {
				copyResolvers.add(0, Builder.ofStaticMap(copyLevels));
			}
			var combined = LevelConfig.of(copyResolvers);
			return combined;
		}

		/**
		 * This.
		 * @return this.
		 */
		protected abstract T self();

	}

	/**
	 * Level resolver builder. {@link LevelConfig} that are added have higher precedence.
	 */
	public final class Builder extends AbstractBuilder<Builder> {

		/**
		 * level resolvers.
		 */
		private final List<LevelResolver> levelResolvers = new ArrayList<>();

		/**
		 * lower priority fallback resolvers.
		 */
		private final List<LevelResolver> fallbacks = new ArrayList<>();

		private Builder() {
		}

		/**
		 * Builds a level resolver combining {@link #config(LevelConfig)},
		 * {@link #level(Level, String)}, and {@link #resolver(LevelResolver)} into a
		 * single resolver of equal priority (the highest severity/most restrictive level
		 * found for a logger name wins), and, if any {@link #fallback(LevelResolver)
		 * fallbacks} were added, wrapping that in a priority chain where the fallbacks
		 * are only consulted for logger names the combined resolver above has no opinion
		 * on (resolves to {@link Level#ALL}). The result is cached.
		 * @return level resolver.
		 */
		public LevelResolver build() {
			var config = buildLevelConfigOrNull();
			List<LevelResolver> copy = new ArrayList<>();
			if (config != null) {
				copy.add(config);
			}
			copy.addAll(levelResolvers);
			LevelResolver resolver = ofResolvers(copy);
			if (!fallbacks.isEmpty()) {
				List<LevelResolver> priority = new ArrayList<>();
				priority.add(resolver);
				priority.addAll(fallbacks);
				resolver = PriorityLevelResolver.of(priority);
			}
			return cached(resolver);
		}

		/**
		 * Adds a level resolver to the end of the resolve list. Resolvers added this way
		 * are combined with equal priority: for a given logger name the most restrictive
		 * level found across all of them (including {@link #config(LevelConfig)} /
		 * {@link #level(Level, String)}) wins.
		 * @param resolver level resolver.
		 * @return this
		 */
		public Builder resolver(LevelResolver resolver) {
			this.levelResolvers.add(resolver);
			return this;
		}

		/**
		 * Adds a lower priority fallback level resolver. Fallbacks are tried in the order
		 * added and only consulted for a logger name if every resolver ahead of it
		 * (everything from {@link #config(LevelConfig)}, {@link #level(Level, String)},
		 * {@link #resolver(LevelResolver)}, and any earlier added fallback) has no
		 * opinion for that name, i.e. resolves to {@link Level#ALL}. This is useful for
		 * e.g. giving a route's own level configuration priority over a global level
		 * resolver without a more specific match in the global resolver being able to
		 * win.
		 * @param resolver fallback level resolver.
		 * @return this
		 */
		public Builder fallback(LevelResolver resolver) {
			this.fallbacks.add(resolver);
			return this;
		}

		/**
		 * This.
		 * @return this.
		 */
		@Override
		protected Builder self() {
			return this;
		}

		static LevelResolver ofResolvers(Collection<? extends LevelResolver> resolvers) {
			if (resolvers.isEmpty()) {
				return StaticLevelResolver.ALL;
			}
			else if (resolvers.size() == 1) {
				return Objects.requireNonNull(resolvers.iterator().next());
			}
			return CompositeLevelResolver.of(resolvers);
		}

		static LevelConfig ofStaticMap(Map<String, Level> levels) {
			if (levels.isEmpty()) {
				return StaticLevelResolver.ALL;
			}
			else if (levels.size() == 1) {
				var e = levels.entrySet().iterator().next();
				if (e.getKey().equals("")) {
					return StaticLevelResolver.of(e.getValue());
				}
				return new SingleLevelResolver(e.getKey(), allToTrace(e.getValue()));
			}
			Map<String, Level> copy = new HashMap<>();

			for (var et : levels.entrySet()) {
				copy.put(et.getKey(), allToTrace(et.getValue()));
			}
			return new MapLevelResolver(copy);
		}

		static Level allToTrace(Level level) {
			if (level == Level.ALL) {
				return Level.TRACE;
			}
			return level;
		}

		static LevelResolver cached(LevelResolver resolver) {
			if (resolver instanceof StaticLevelResolver) {
				return resolver;
			}
			return new CachedLevelResolver(resolver);
		}

	}

}

enum StaticLevelResolver implements LevelConfig {

	ALL(Level.ALL), //
	TRACE(Level.TRACE), //
	INFO(Level.INFO), //
	DEBUG(Level.DEBUG), //
	ERROR(Level.ERROR), //
	WARNING(Level.WARNING), //
	OFF(Level.OFF),; //

	private final Level level;

	private StaticLevelResolver(Level level) {
		this.level = level;
	}

	static LevelConfig of(Level level) {
		return switch (level) {
			case INFO -> StaticLevelResolver.INFO;
			case ALL -> StaticLevelResolver.TRACE; // This is on purpose
			case DEBUG -> StaticLevelResolver.DEBUG;
			case ERROR -> StaticLevelResolver.ERROR;
			case OFF -> StaticLevelResolver.OFF;
			case TRACE -> StaticLevelResolver.TRACE;
			case WARNING -> StaticLevelResolver.WARNING;
		};
	}

	@Override
	public @Nullable Level levelOrNull(String name) {
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
	public @Nullable Level levelOrNull(String name) {
		return levels.get(name);
	}
}

@SuppressWarnings("ArrayRecordComponent")
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

@SuppressWarnings("ArrayRecordComponent")
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
		@SuppressWarnings("null") // TODO eclipse bug
		LevelResolver @NonNull [] array = resolved.toArray(new LevelResolver[] {});
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

	@Override
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
/*
 * Resolves against resolvers in order and returns the first non ALL result. This gives
 * earlier resolvers priority per logger name rather than merging all resolvers into one
 * combined prefix walk, where a more specific match in a lower priority resolver could
 * otherwise win over a coarser match in a higher priority one regardless of which is
 * meant to take precedence.
 */
@SuppressWarnings("ArrayRecordComponent")
record PriorityLevelResolver(LevelResolver[] resolvers) implements LevelResolver {

	public static LevelResolver of(Collection<? extends LevelResolver> resolvers) {
		List<LevelResolver> resolved = new ArrayList<>();
		for (var r : resolvers) {
			if (r instanceof PriorityLevelResolver pr) {
				for (var j : pr.resolvers()) {
					resolved.add(j);
				}
			}
			else {
				resolved.add(r);
			}
		}
		if (resolved.isEmpty()) {
			return StaticLevelResolver.ALL;
		}
		if (resolved.size() == 1) {
			return resolved.get(0);
		}
		@SuppressWarnings("null") // TODO eclipse bug
		LevelResolver @NonNull [] array = resolved.toArray(new LevelResolver[] {});
		return new PriorityLevelResolver(array);
	}

	@Override
	public Level resolveLevel(String name) {
		for (var resolver : resolvers) {
			var level = resolver.resolveLevel(name);
			if (level != Level.ALL) {
				return level;
			}
		}
		return Level.ALL;
	}

	@Override
	public void clear() {
		for (var resolver : resolvers) {
			resolver.clear();
		}
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + Arrays.asList(resolvers);
	}

}

final class CachedLevelResolver implements LevelResolver {

	private final LevelResolver levelResolver;

	private final ConcurrentHashMap<String, Level> levelCache = new ConcurrentHashMap<>();

	public CachedLevelResolver(LevelResolver levelResolver) {
		super();
		this.levelResolver = levelResolver;
	}

	@Override
	public Level resolveLevel(String name) {
		return levelCache.computeIfAbsent(name, n -> levelResolver.resolveLevel(n));
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

final class GroupLevelResolver implements LevelConfig {

	private final LogProperties properties;

	private final String groupLevelPrefix;

	private volatile LevelConfig levelResolver;

	public static GroupLevelResolver of(LogProperties properties) {
		return new GroupLevelResolver(properties, LogProperties.LEVEL_PREFIX);
	}

	public static GroupLevelResolver of(LogProperties properties, String groupLevelPrefix) {
		return new GroupLevelResolver(properties, groupLevelPrefix);
	}

	GroupLevelResolver(LogProperties properties, String groupLevelPrefix) {
		super();
		this.properties = properties;
		this.groupLevelPrefix = groupLevelPrefix;
		this.levelResolver = populate(properties, this.groupLevelPrefix);
	}

	static final String GROUPS_PROPERTY = LogProperties.GROUPS_PROPERTY;
	static final String GROUP_PROPERTY = LogProperties.GROUP_PROPERTY;

	@Override
	public Level resolveLevel(String name) {
		return levelResolver.resolveLevel(name);
	}

	@Override
	public @Nullable Level levelOrNull(String name) {
		return levelResolver.levelOrNull(name);
	}

	@Override
	public void clear() {
		this.levelResolver = populate(properties, this.groupLevelPrefix);
	}

	static LevelConfig populate(LogProperties properties, String groupLevelPrefix) {

		Map<String, List<String>> groupToLoggers = Property.builder().ofList().build(GROUPS_PROPERTY).map(_groups -> {
			Map<String, List<String>> m = new LinkedHashMap<>();
			for (String g : _groups) {
				Property.builder()
					.ofList() //
					.buildWithName(GROUP_PROPERTY, g) //
					.get(properties) //
					.optional() //
					.filter(loggers -> !loggers.isEmpty()) //
					.ifPresent(loggers -> m.put(g, loggers));
			}
			return m;
		}).get(properties).value(Map.of());

		Map<String, Level> groupToLevels = new LinkedHashMap<>();
		for (var e : groupToLoggers.entrySet()) {
			String group = e.getKey();
			Property.builder()
				.withPrefix(groupLevelPrefix)
				.map(LevelResolver::parseLevel) //
				.build(group)
				.get(properties) //
				.optional() //
				.ifPresent(level -> groupToLevels.put(group, level));
		}
		Map<String, Level> loggerToLevels = new LinkedHashMap<>();
		for (var e : groupToLevels.entrySet()) {
			List<String> loggers = groupToLoggers.get(e.getKey());
			if (loggers == null || loggers.isEmpty()) {
				continue;
			}
			for (var logger : loggers) {
				loggerToLevels.put(logger, e.getValue());
			}
		}
		return new MapLevelResolver(Map.copyOf(loggerToLevels));
	}

	@Override
	public String toString() {
		return "GroupLevelResolver[groupLevelPrefix=" + groupLevelPrefix + ", properties=" + properties + "]";
	}

}

final class ConfigLevelResolver implements LevelConfig {

	private final LogProperties properties;

	private final String prefix;

	private final PropertyGetter<Level> levelExtractor;

	public static ConfigLevelResolver of(LogProperties properties) {
		return of(properties, LogProperties.LEVEL_PREFIX);
	}

	public static ConfigLevelResolver of(LogProperties properties, String prefix) {
		var levelExtractor = PropertyGetter.of().withPrefix(prefix).map(LevelResolver::parseLevel);
		return new ConfigLevelResolver(properties, prefix, levelExtractor);
	}

	private ConfigLevelResolver(LogProperties properties, String prefix, PropertyGetter<Level> levelExtractor) {
		super();
		this.properties = properties;
		this.prefix = prefix;
		this.levelExtractor = levelExtractor;
	}

	@Override
	public @Nullable Level levelOrNull(String name) {
		return levelExtractor.build(name).get(properties).valueOrNull();
	}

	@Override
	public String toString() {
		return "ConfigLevelResolver[prefix=" + prefix + ", properties=" + properties + "]";
	}

}