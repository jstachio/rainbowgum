package io.jstach.rainbowgum;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * The enabled groups of logging.groups and logging.group.{name}, by logger name, for
 * per logger properties other than levels (GroupLevelResolver handles levels). A group
 * applies wherever the name it lists is on a logger's path, like a key with that name
 * would.
 */
record LoggerGroups(Map<String, List<String>> loggerToGroups) {

	static final LoggerGroups EMPTY = new LoggerGroups(Map.of());

	/*
	 * Malformed group properties are reported by GroupLevelResolver when the config is
	 * built, so here they just mean no groups.
	 */
	static LoggerGroups of(LogProperties properties) {
		List<String> groups;
		try {
			groups = properties.forKey(LogProperties.GROUPS_PROPERTY)
				.ofList()
				.or(List.of())
				.validateNow(LoggerGroups.class);
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
					.validateNow(LoggerGroups.class);
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
		return new LoggerGroups(Map.copyOf(loggerToGroups));
	}

	/*
	 * Candidate keys for one step of the logger path: the key for the path itself first,
	 * then the keys of the groups listing the path.
	 */
	List<String> keys(String prefix, String path) {
		String direct = DefaultLoggerKey.key(prefix, path);
		var groups = path.isEmpty() ? null : loggerToGroups.get(path);
		if (groups == null) {
			return List.of(direct);
		}
		var keys = new ArrayList<String>(groups.size() + 1);
		keys.add(direct);
		for (String group : groups) {
			keys.add(DefaultLoggerKey.key(prefix, group));
		}
		return keys;
	}

}
