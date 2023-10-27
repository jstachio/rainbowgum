package io.jstach.rainbowgum;

/**
 * A component that has a start and stop.
 */
public interface LogLifecycle extends AutoCloseable {

	/**
	 * Starts a component.
	 * @param config log config.
	 */
	public void start(LogConfig config);

	public void close();

}
