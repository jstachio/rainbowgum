package io.jstach.rainbowgum.filter;

import java.lang.System.Logger.Level;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.jstach.rainbowgum.LevelResolver;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogRouter.Router.Filter;
import io.jstach.rainbowgum.annotation.LogConfigurable;

final class RemapFilter implements Filter {

	/** none, so every logger */
	static final List<String> DEFAULT_LOGGERS = List.of();

	private final Map<Level, Level> levels;

	private final List<String> loggers;

	private RemapFilter(Map<Level, Level> levels, List<String> loggers) {
		this.levels = levels;
		this.loggers = loggers;
	}

	/**
	 * Moves events from one level to another, for example to make a noisy dependency's
	 * warnings info. The route drops an event moved to a level it does not enable for the
	 * event's logger.
	 * @param name route name.
	 * @param filter name the filter is selected with.
	 * @param levels <code>from:to</code> level pairs, for example
	 * <code>warning:info,error:warning</code>. Levels not listed are kept.
	 * @param loggers logger names whose events are moved, including their children.
	 * @return filter.
	 */
	@LogConfigurable(name = "RemapFilterBuilder", prefix = LogProperties.FILTER_PREFIX)
	static Filter of(@LogConfigurable.KeyParameter String name, @LogConfigurable.KeyParameter String filter,
			List<String> levels, @LogConfigurable.DefaultParameter("DEFAULT_LOGGERS") List<String> loggers) {
		return new RemapFilter(parseLevels(levels), loggers.stream().map(String::strip).toList());
	}

	private static Map<Level, Level> parseLevels(List<String> pairs) {
		if (pairs.isEmpty()) {
			throw new IllegalArgumentException("levels must list at least one from:to pair.");
		}
		Map<Level, Level> levels = new EnumMap<>(Level.class);
		for (String pair : pairs) {
			int colon = pair.indexOf(':');
			if (colon < 0) {
				throw new IllegalArgumentException("levels must be from:to pairs but has '" + pair.strip() + "'.");
			}
			var from = LevelResolver.parseLevel(pair.substring(0, colon));
			var to = LevelResolver.parseLevel(pair.substring(colon + 1));
			if (from == Level.OFF || to == Level.OFF) {
				throw new IllegalArgumentException("levels cannot use OFF but was '" + pair.strip() + "'.");
			}
			if (levels.put(from, to) != null) {
				throw new IllegalArgumentException("levels lists " + from + " more than once.");
			}
		}
		return levels;
	}

	@Override
	public LogEvent filterOrNull(LogEvent event) {
		var to = levels.get(event.level());
		if (to == null || !matches(event.loggerName())) {
			return event;
		}
		return LogEvent.withLevel(event, to);
	}

	private boolean matches(String loggerName) {
		if (loggers.isEmpty()) {
			return true;
		}
		for (String logger : loggers) {
			if (loggerName.startsWith(logger)
					&& (loggerName.length() == logger.length() || loggerName.charAt(logger.length()) == '.')) {
				return true;
			}
		}
		return false;
	}

}
