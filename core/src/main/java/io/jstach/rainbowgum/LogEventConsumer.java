package io.jstach.rainbowgum;

@FunctionalInterface
public interface LogEventConsumer {

	void append(LogEvent event);

}
