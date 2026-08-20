package io.jstach.rainbowgum.tomcat;

import org.apache.juli.logging.Log;

interface ForwardingTomcatLog extends Log {

	public Log delegate();

	default boolean isDebugEnabled() {
		return delegate().isDebugEnabled();
	}

	default boolean isErrorEnabled() {
		return delegate().isErrorEnabled();
	}

	default boolean isFatalEnabled() {
		return delegate().isFatalEnabled();
	}

	default boolean isInfoEnabled() {
		return delegate().isInfoEnabled();
	}

	default boolean isTraceEnabled() {
		return delegate().isTraceEnabled();
	}

	default boolean isWarnEnabled() {
		return delegate().isWarnEnabled();
	}

	default void trace(Object message) {
		delegate().trace(message);
	}

	default void trace(Object message, Throwable t) {
		delegate().trace(message, t);
	}

	default void debug(Object message) {
		delegate().debug(message);
	}

	default void debug(Object message, Throwable t) {
		delegate().debug(message, t);
	}

	default void info(Object message) {
		delegate().info(message);
	}

	default void info(Object message, Throwable t) {
		delegate().info(message, t);
	}

	default void warn(Object message) {
		delegate().warn(message);
	}

	default void warn(Object message, Throwable t) {
		delegate().warn(message, t);
	}

	default void error(Object message) {
		delegate().error(message);
	}

	default void error(Object message, Throwable t) {
		delegate().error(message, t);
	}

	default void fatal(Object message) {
		delegate().fatal(message);
	}

	default void fatal(Object message, Throwable t) {
		delegate().fatal(message, t);
	}

}
