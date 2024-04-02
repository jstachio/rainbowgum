package io.jstach.rainbowgum;

/**
 * Logs events.
 */
public interface LogEventLogger {

	/**
	 * Logs events usually without filtering. <strong> There is no guarantee this call
	 * will check if the event should be filtered (threshold is too low) and in general
	 * does not on purpose! </strong>
	 * @param event event.
	 */
	public void log(LogEvent event);

}
