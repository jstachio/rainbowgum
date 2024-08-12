package io.jstach.rainbowgum;

/**
 * Logs events.
 */
public interface LogEventLogger {

	/*
	 * TODO rename this to LogEventSink to avoid confusion with slf4j loggers.
	 */

	/**
	 * Logs events usually without filtering. <strong> There is no guarantee this call
	 * will check if the event should be filtered (threshold is too low) and in general
	 * does not on purpose! </strong>
	 * @param event event.
	 */
	public void log(LogEvent event);

	// /**
	// * Logs events usually without filtering. Just like {@link #log(LogEvent)}
	// * but the consumer will be called when this sink is done with the event.
	// * This is for asynchronous loggers but calling this method does not guarantee
	// * non-blocking. The default implementation assume synchronous behavior
	// * of {@link #log(LogEvent)}.
	// * @param event event.
	// * @param onComplete
	// */
	// default void log(LogEvent event, Consumer<? super LogEvent> onComplete) {
	// log(event);
	// onComplete.accept(event);
	// }

}
