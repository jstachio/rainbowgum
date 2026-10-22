package io.jstach.rainbowgum;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.jspecify.annotations.Nullable;

/*
 * Internal support for properties keyed by logger name.
 */
final class Loggers {

	private Loggers() {
	}

	/*
	 * The root logger's alias, so a nested format can hold the root logger's value next
	 * to other logger names, and for consistency with Spring Boot.
	 */
	static final String ROOT_ALIAS = "root";

	/*
	 * A property key that ends with a logger name, such as logging.level.com.example. The
	 * part after the prefix is one logger name, not more of the key path.
	 */
	record Key(String prefix, String loggerName) {

		private static final String ROUTE_START = LogProperties.ROOT_PREFIX + "route" + LogProperties.SEP;

		private static final String ROUTE_LEVEL = LogProperties.SEP + "level";

		/*
		 * The key for a logger name. The name is appended as is, so a name with a leading
		 * dot or any other character parses back to the same name.
		 */
		static String key(String prefix, String loggerName) {
			return loggerName.isEmpty() ? prefix : prefix + LogProperties.SEP + loggerName;
		}

		static @Nullable Key parseOrNull(String key) {
			var k = parse(LogProperties.LEVEL_PREFIX, key);
			if (k == null) {
				k = parse(LogProperties.CHANGE_LOGGER_PREFIX, key);
			}
			if (k == null) {
				k = parse(LogProperties.CALLER_LOGGER_PREFIX, key);
			}
			if (k == null && key.startsWith(ROUTE_START)) {
				int routeEnd = key.indexOf(LogProperties.SEP, ROUTE_START.length());
				if (routeEnd > ROUTE_START.length()) {
					k = parse(key.substring(0, routeEnd) + ROUTE_LEVEL, key);
				}
			}
			return k;
		}

		private static @Nullable Key parse(String prefix, String key) {
			if (!key.startsWith(prefix)) {
				return null;
			}
			if (key.length() == prefix.length()) {
				return new Key(prefix, "");
			}
			if (!key.startsWith(LogProperties.SEP, prefix.length())) {
				return null;
			}
			return new Key(prefix, key.substring(prefix.length() + LogProperties.SEP.length()));
		}

	}

	/*
	 * Reads the root logger's key and then its alias from each source in turn, so a
	 * higher priority source wins whichever of the two it uses.
	 */
	record RootProperties(LogProperties properties) implements LogProperties {

		private static String alias(String key) {
			return key + LogProperties.SEP + ROOT_ALIAS;
		}

		@Override
		public @Nullable String valueOrNull(String key) {
			var value = properties.valueOrNull(key);
			return value != null ? value : properties.valueOrNull(alias(key));
		}

		@Override
		public <R extends @Nullable Object> @Nullable R visit(String key,
				BiFunction<LogProperties, String, @Nullable R> visitor) {
			return properties.visit(key, (p, k) -> {
				var r = visitor.apply(p, k);
				return r != null ? r : visitor.apply(p, alias(k));
			});
		}

		@Override
		public String description(String key) {
			return properties.description(key);
		}

		@Override
		public int order() {
			return properties.order();
		}

	}

	/*
	 * The enabled groups of logging.groups and logging.group.{name}, by logger name, for
	 * per logger properties other than levels (GroupLevelResolver handles levels). A
	 * group applies wherever the name it lists is on a logger's path, like a key with
	 * that name would.
	 */
	record Groups(Map<String, List<String>> loggerToGroups) {

		static final Groups EMPTY = new Groups(Map.of());

		/*
		 * Malformed group properties are reported by GroupLevelResolver when the config
		 * is built, so here they just mean no groups.
		 */
		static Groups of(LogProperties properties) {
			List<String> groups;
			try {
				groups = properties.forKey(LogProperties.GROUPS_PROPERTY)
					.ofList()
					.or(List.of())
					.validateNow(Groups.class);
			}
			catch (LogProperty.ValidationException e) {
				return EMPTY;
			}
			Map<String, List<String>> loggerToGroups = new LinkedHashMap<>();
			for (String group : groups) {
				List<String> loggers;
				try {
					loggers = properties.forKey(LogProperties.GROUP_PROPERTY, group)
						.ofList()
						.or(List.of())
						.validateNow(Groups.class);
				}
				catch (LogProperty.ValidationException e) {
					continue;
				}
				for (String logger : loggers) {
					/*
					 * Later groups first, so like level groups the last group listed in
					 * logging.groups wins when a logger name is in more than one.
					 */
					loggerToGroups.computeIfAbsent(logger, k -> new ArrayList<>()).add(0, group);
				}
			}
			return new Groups(Map.copyOf(loggerToGroups));
		}

		/*
		 * Candidate names for one step of the logger path: the path itself first, then
		 * the groups listing the path.
		 */
		List<String> names(String path) {
			var groups = path.isEmpty() ? null : loggerToGroups.get(path);
			// Skip the default's alias as a direct key, but keep groups for literal root.
			if (path.equals(ROOT_ALIAS)) {
				return groups == null ? List.of() : groups;
			}
			if (groups == null) {
				return List.of(path);
			}
			var names = new ArrayList<String>(groups.size() + 1);
			names.add(path);
			names.addAll(groups);
			return names;
		}

	}

}
