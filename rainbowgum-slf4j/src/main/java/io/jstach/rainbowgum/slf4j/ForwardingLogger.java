package io.jstach.rainbowgum.slf4j;

import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * A logger that forwards calls to the {@link #delegate()} logger.
 */
public interface ForwardingLogger extends Logger, WrappingLogger {

	/**
	 * The downstream logger to forward calls to.
	 * @return delegate.
	 */
	public Logger delegate();

	@Override
	default String getName() {
		return delegate().getName();
	}

	@Override
	default boolean isErrorEnabled() {
		return delegate().isErrorEnabled();
	}

	@Override
	default void error(String msg) {
		delegate().error(msg);
	}

	@Override
	default void error(String format, Object arg) {
		delegate().error(format, arg);
	}

	@Override
	default void error(String format, Object arg1, Object arg2) {
		delegate().error(format, arg1, arg2);
	}

	@Override
	default void error(String format, Object... arguments) {
		delegate().error(format, arguments);
	}

	@Override
	default boolean isErrorEnabled(Marker marker) {
		return delegate().isErrorEnabled(marker);
	}

	@Override
	default void error(Marker marker, String msg) {
		delegate().error(marker, msg);
	}

	@Override
	default void error(Marker marker, String format, Object arg) {
		delegate().error(marker, format, arg);
	}

	@Override
	default void error(Marker marker, String format, Object arg1, Object arg2) {
		delegate().error(marker, format, arg1, arg2);
	}

	@Override
	default void error(Marker marker, String format, Object... argArray) {
		delegate().error(marker, format, argArray);
	}

	@Override
	default void error(Marker marker, String msg, Throwable t) {
		delegate().error(marker, msg, t);
	}

	@Override
	default boolean isWarnEnabled() {
		return delegate().isWarnEnabled();
	}

	@Override
	default void warn(String msg) {
		delegate().warn(msg);
	}

	@Override
	default void warn(String format, Object arg) {
		delegate().warn(format, arg);
	}

	@Override
	default void warn(String format, Object arg1, Object arg2) {
		delegate().warn(format, arg1, arg2);
	}

	@Override
	default void warn(String format, Object... arguments) {
		delegate().warn(format, arguments);
	}

	@Override
	default boolean isWarnEnabled(Marker marker) {
		return delegate().isWarnEnabled(marker);
	}

	@Override
	default void warn(Marker marker, String msg) {
		delegate().warn(marker, msg);
	}

	@Override
	default void warn(Marker marker, String format, Object arg) {
		delegate().warn(marker, format, arg);
	}

	@Override
	default void warn(Marker marker, String format, Object arg1, Object arg2) {
		delegate().warn(marker, format, arg1, arg2);
	}

	@Override
	default void warn(Marker marker, String format, Object... argArray) {
		delegate().warn(marker, format, argArray);
	}

	@Override
	default void warn(Marker marker, String msg, Throwable t) {
		delegate().warn(marker, msg, t);
	}

	@Override
	default boolean isInfoEnabled() {
		return delegate().isInfoEnabled();
	}

	@Override
	default void info(String msg) {
		delegate().info(msg);
	}

	@Override
	default void info(String format, Object arg) {
		delegate().info(format, arg);
	}

	@Override
	default void info(String format, Object arg1, Object arg2) {
		delegate().info(format, arg1, arg2);
	}

	@Override
	default void info(String format, Object... arguments) {
		delegate().info(format, arguments);
	}

	@Override
	default boolean isInfoEnabled(Marker marker) {
		return delegate().isInfoEnabled(marker);
	}

	@Override
	default void info(Marker marker, String msg) {
		delegate().info(marker, msg);
	}

	@Override
	default void info(Marker marker, String format, Object arg) {
		delegate().info(marker, format, arg);
	}

	@Override
	default void info(Marker marker, String format, Object arg1, Object arg2) {
		delegate().info(marker, format, arg1, arg2);
	}

	@Override
	default void info(Marker marker, String format, Object... argArray) {
		delegate().info(marker, format, argArray);
	}

	@Override
	default void info(Marker marker, String msg, Throwable t) {
		delegate().info(marker, msg, t);
	}

	@Override
	default boolean isDebugEnabled() {
		return delegate().isDebugEnabled();
	}

	@Override
	default void debug(String msg) {
		delegate().debug(msg);
	}

	@Override
	default void debug(String format, Object arg) {
		delegate().debug(format, arg);
	}

	@Override
	default void debug(String format, Object arg1, Object arg2) {
		delegate().debug(format, arg1, arg2);
	}

	@Override
	default void debug(String format, Object... arguments) {
		delegate().debug(format, arguments);
	}

	@Override
	default boolean isDebugEnabled(Marker marker) {
		return delegate().isDebugEnabled(marker);
	}

	@Override
	default void debug(Marker marker, String msg) {
		delegate().debug(marker, msg);
	}

	@Override
	default void debug(Marker marker, String format, Object arg) {
		delegate().debug(marker, format, arg);
	}

	@Override
	default void debug(Marker marker, String format, Object arg1, Object arg2) {
		delegate().debug(marker, format, arg1, arg2);
	}

	@Override
	default void debug(Marker marker, String format, Object... argArray) {
		delegate().debug(marker, format, argArray);
	}

	@Override
	default void debug(Marker marker, String msg, Throwable t) {
		delegate().debug(marker, msg, t);
	}

	@Override
	default boolean isTraceEnabled() {
		return delegate().isTraceEnabled();
	}

	@Override
	default void trace(String msg) {
		delegate().trace(msg);
	}

	@Override
	default void trace(String format, Object arg) {
		delegate().trace(format, arg);
	}

	@Override
	default void trace(String format, Object arg1, Object arg2) {
		delegate().trace(format, arg1, arg2);
	}

	@Override
	default void trace(String format, Object... arguments) {
		delegate().trace(format, arguments);
	}

	@Override
	default boolean isTraceEnabled(Marker marker) {
		return delegate().isTraceEnabled(marker);
	}

	@Override
	default void trace(Marker marker, String msg) {
		delegate().trace(marker, msg);
	}

	@Override
	default void trace(Marker marker, String format, Object arg) {
		delegate().trace(marker, format, arg);
	}

	@Override
	default void trace(Marker marker, String format, Object arg1, Object arg2) {
		delegate().trace(marker, format, arg1, arg2);
	}

	@Override
	default void trace(Marker marker, String format, Object... argArray) {
		delegate().trace(marker, format, argArray);
	}

	@Override
	default void trace(Marker marker, String msg, Throwable t) {
		delegate().trace(marker, msg, t);
	}

}
