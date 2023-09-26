package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

public interface LevelResolver {

	public Level resolveLevel(String name);

	default boolean isEnabled(String loggerName, Level level) {
		return resolveLevel(loggerName).getSeverity() <= level.getSeverity();
	}

	public static Builder builder() {
		return new Builder();
	}
	
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
	
	public static LevelResolver of(Map<String, Level> levels) {
		return new MapLevelResolver(levels);
	}
	
	public static LevelResolver of(Level level) {
		return new LevelResolver() {
			
			@Override
			public Level resolveLevel(
					String name) {
				return level;
			}
		};
	}
	
	public static LevelResolver of(LogProperties config, String prefix) {
		return new ConfigLevelResolver() {

			@Override
			public LogProperties properties() {
				return config;
			}
			
			@Override
			public String levelPropertyPrefix() {
				return prefix;
			}
		};
	}
	
	abstract class AbstractBuilder<T> {
		protected List<LevelResolver> resolvers = new ArrayList<>();
		protected Map<String, Level> levels = new LinkedHashMap<>();
		
		public T level(String loggerName, Level level) {
			levels.put(loggerName, level);
			return self();
		}
		
		public T add(LevelResolver resolver) {
			resolvers.add(resolver);
			return self();
		}
		
		protected LevelResolver buildLevelResolver() {
			if (! levels.isEmpty()) {
				resolvers.add(LevelResolver.of(levels));
			}
			return LevelResolver.of(resolvers);
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
	
	public static Level resolveLevel(LevelAware levelBindings, String name, Level fallback) {
		String tempName = name;
		Level level = null;
		int indexOfLastDot = tempName.length();
		while ((level == null) && (indexOfLastDot > -1)) {
			tempName = tempName.substring(0, indexOfLastDot);
			level = levelBindings.levelOrNull(tempName);
			indexOfLastDot = String.valueOf(tempName).lastIndexOf(".");
		}
		if (level != null) {
			return level;
		}
		return fallback;
	}
	

}

record MapLevelResolver(Map<String, Level> levels) implements LevelResolver {

	@Override
	public Level resolveLevel(
			String name) {
		var level = levels.get(name);
		if (level == null) {
			return Level.OFF;
		}
		return levels.get(name);
	}
	
}

record CompositeLevelResolver(LevelResolver[] resolvers) implements LevelResolver {

	@Override
	public Level resolveLevel(
			String name) {
		var lowestLevel = Level.OFF;
		for (var resolver : resolvers) {
			var level = resolver.resolveLevel(name);
			if (level == Level.ALL || level == Level.TRACE) {
				return level;
			}
			if (lowestLevel.getSeverity() > level.getSeverity()) {
				lowestLevel = level;
			}
		}
		return lowestLevel;
	}
	
}

interface ConfigLevelResolver extends LevelResolver, LevelAware {

	LogProperties properties();
	
	String levelPropertyPrefix();

	default @Nullable Level levelOrNull(String name) {
		String key = levelPropertyPrefix() + name;
		return Property.of(properties(), key) //
			.mapString(s -> s.toUpperCase(Locale.ROOT)) //
			.map(Level::valueOf) //
			.value();
	}


	default Level resolveLevel(String name) {
		return LevelResolver.resolveLevel(this, name, defaultLevel());
	}

	default Level defaultLevel() {
		return Level.INFO;
	}

}