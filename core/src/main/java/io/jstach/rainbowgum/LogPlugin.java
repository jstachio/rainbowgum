package io.jstach.rainbowgum;

import static java.util.Objects.requireNonNull;

import java.util.List;

public interface LogPlugin {

	default List<String> getNames() {
		return List.of(requireNonNull(getClass().getCanonicalName()));
	}

	default boolean isEnabledByDefault() {
		return true;
	}

	public List<LogAppender> createLogAppenders(LogConfig config);

}
