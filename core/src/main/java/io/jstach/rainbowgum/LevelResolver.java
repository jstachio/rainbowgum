package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LevelResolver.LevelConfig;

public interface LevelResolver {

	public Level resolveLevel(String name);

	default boolean isEnabled(String loggerName, Level level) {
		return resolveLevel(loggerName).getSeverity() <= level.getSeverity();
	}

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

	}

	public static Builder builder() {
		return new Builder();
	}

	public static LevelResolver off() {
		return StaticLevelResolver.OFF;
	}

	public static LevelResolver of(Collection<? extends LevelResolver> resolvers) {
		if (resolvers.isEmpty()) {
			return LevelResolver.off();
		}
		else if (resolvers.size() == 1) {
			return resolvers.iterator().next();
		}
		return CompositeLevelResolver.of(resolvers);
	}

	public static LevelResolver of(Map<String, Level> levels) {
		if (levels.isEmpty()) {
			return LevelResolver.off();
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

	// public static LevelResolver of(LogProperties config, String prefix) {
	// return new ConfigLevelResolver() {
	//
	// @Override
	// public LogProperties properties() {
	// return config;
	// }
	//
	// @Override
	// public String levelPropertyPrefix() {
	// return prefix;
	// }
	// };
	// }

	public static LevelResolver cached(LevelResolver resolver) {
		if (resolver instanceof StaticLevelResolver) {
			return resolver;
		}
		return new CachedLevelResolver(resolver);
	}

	abstract class AbstractBuilder<T> {

		protected List<LevelResolver> resolvers = new ArrayList<>();

		protected Map<String, Level> levels = new LinkedHashMap<>();

		protected boolean levelResolverCached = true;

		public T level(String loggerName, Level level) {
			levels.put(loggerName, level);
			return self();
		}

		public T level(Level level) {
			levels.put("", level);
			return self();
		}

		public T levelResolver(LevelResolver resolver) {
			resolvers.add(resolver);
			return self();
		}

		public T levelCache(boolean cached) {
			this.levelResolverCached = cached;
			return self();
		}

		protected LevelResolver buildLevelResolver() {
			if (levels.isEmpty()) {
				level(Level.INFO);
			}
			resolvers.add(0, LevelResolver.of(levels));
			var combined = LevelResolver.of(resolvers);
			if (levelResolverCached) {
				return LevelResolver.cached(combined);
			}
			return combined;
		}

		protected abstract T self();

	}

	public class Builder extends AbstractBuilder<Builder> {

		public LevelResolver build() {
			return buildLevelResolver();
		}

		@Override
		protected Builder self() {
			return this;
		}

	}

	public static Level resolveLevel(LevelConfig levelBindings, String name) {
		String tempName = name;
		Level level = null;
		int indexOfLastDot = tempName.length();
		while ((level == null) && (indexOfLastDot > -1)) {
			tempName = tempName.substring(0, indexOfLastDot);
			level = allToNull(levelBindings.levelOrNull(tempName));
			indexOfLastDot = tempName.lastIndexOf(".");
		}
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

enum StaticLevelResolver implements LevelResolver {

	INFO {
		@Override
		public Level resolveLevel(String name) {
			return Level.INFO;
		}
	},
	OFF {
		@Override
		public Level resolveLevel(String name) {
			return Level.OFF;
		}
	},
	ALL {
		@Override
		public Level resolveLevel(String name) {
			return Level.ALL;
		}
	},
	DEBUG {
		@Override
		public Level resolveLevel(String name) {
			return Level.DEBUG;
		}

	},
	ERROR {
		@Override
		public Level resolveLevel(String name) {
			return Level.ERROR;
		}
	},
	WARNING {
		@Override
		public Level resolveLevel(String name) {
			return Level.WARNING;
		}

	},
	TRACE {
		@Override
		public Level resolveLevel(String name) {
			return Level.TRACE;
		}
	};

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

record CompositeLevelResolver(LevelResolver[] resolvers) implements LevelResolver {

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
		return new CompositeLevelResolver(array);
	}

	@Override
	public Level resolveLevel(String name) {
		var lowestLevel = Level.OFF;
		for (var resolver : resolvers) {
			var level = resolver.resolveLevel(name);
			if (level == Level.ALL) {
				continue;
			}
			if (level == Level.TRACE) {
				return level;
			}
			if (lowestLevel.getSeverity() > level.getSeverity()) {
				lowestLevel = level;
			}
		}
		return lowestLevel;
	}

}

class CachedLevelResolver implements LevelResolver {

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

}

interface ConfigLevelResolver extends LevelConfig {

	LogProperties properties();

	String levelPropertyPrefix();

	private static String concatName(String prefix, String name) {
		if (name.equals("")) {
			return prefix;
		}
		return prefix + "." + name;
	}

	default @Nullable Level levelOrNull(String name) {
		String key = concatName(levelPropertyPrefix(), name);
		return Property.of(properties(), key) //
			.mapString(s -> s.toUpperCase(Locale.ROOT)) //
			.map(Level::valueOf) //
			.orNull();
	}

	default Level defaultLevel() {
		var level = levelOrNull("");
		if (level == null) {
			return Level.OFF;
		}
		return level;
	}

}