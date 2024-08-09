package io.jstach.rainbowgum;

import java.util.ArrayList;
import java.util.List;

sealed interface LogAction {

	enum StandardAction implements LogAction {

		REOPEN, FLUSH, STATUS;

	}

}

interface Actor {

	List<LogResponse> act(LogAction action);

	static <A extends Actor> List<LogResponse> act(Iterable<A> actors, LogAction action) {

		List<LogResponse> responses = new ArrayList<>();
		for (var appender : actors) {
			responses.addAll(appender.act(action));
		}
		return responses;
	}

	static <T extends Actor> List<LogResponse> act(T[] actors, LogAction action) {

		List<LogResponse> responses = new ArrayList<>();
		for (var appender : actors) {
			responses.addAll(appender.act(action));
		}
		return responses;
	}

}
