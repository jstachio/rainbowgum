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
	 * Candidate names for one step of the logger path: the path itself first, then the
	 * groups listing the path.
	 */
	List<String> names(String path) {
		var groups = path.isEmpty() ? null : loggerToGroups.get(path);
		// Skip the default's alias as a direct key, but keep groups for literal root.
		if (path.equals(DefaultLoggerKey.ROOT_ALIAS)) {
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
