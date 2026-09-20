package io.jstach.rainbowgum;

import java.util.ArrayList;
import java.util.List;

sealed interface LogAction {

	enum StandardAction implements LogAction {

		REOPEN, FLUSH;

	}

}

interface Actor {

	List<LogEvent> act(LogAction action);

	static <A extends Actor> List<LogEvent> act(Iterable<A> actors, LogAction action) {

		List<LogEvent> events = new ArrayList<>();
		for (var appender : actors) {
			events.addAll(appender.act(action));
		}
		return events;
	}

	static <T extends Actor> List<LogEvent> act(T[] actors, LogAction action) {

		List<LogEvent> events = new ArrayList<>();
		for (var appender : actors) {
			events.addAll(appender.act(action));
		}
		return events;
	}

}
