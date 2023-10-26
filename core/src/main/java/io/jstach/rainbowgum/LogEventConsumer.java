package io.jstach.rainbowgum;

@FunctionalInterface
interface LogEventConsumer {

	void append(LogEvent event);

}
