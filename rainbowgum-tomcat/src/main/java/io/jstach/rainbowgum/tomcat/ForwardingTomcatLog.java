package io.jstach.rainbowgum.tomcat;

import org.apache.juli.logging.Log;
import org.jspecify.annotations.Nullable;

interface ForwardingTomcatLog extends Log {

	public Log delegate();

	@Override
	default boolean isDebugEnabled() {
		return delegate().isDebugEnabled();
	}

	@Override
	default boolean isErrorEnabled() {
		return delegate().isErrorEnabled();
	}

	@Override
	default boolean isFatalEnabled() {
		return delegate().isFatalEnabled();
	}

	@Override
	default boolean isInfoEnabled() {
		return delegate().isInfoEnabled();
	}

	@Override
	default boolean isTraceEnabled() {
		return delegate().isTraceEnabled();
	}

	@Override
	default boolean isWarnEnabled() {
		return delegate().isWarnEnabled();
	}

	@Override
	default void trace(@Nullable Object message) {
		delegate().trace(message);
	}

	@Override
	default void trace(@Nullable Object message, @Nullable Throwable t) {
		delegate().trace(message, t);
	}

	@Override
	default void debug(@Nullable Object message) {
		delegate().debug(message);
	}

	@Override
	default void debug(@Nullable Object message, @Nullable Throwable t) {
		delegate().debug(message, t);
	}

	@Override
	default void info(@Nullable Object message) {
		delegate().info(message);
	}

	@Override
	default void info(@Nullable Object message, @Nullable Throwable t) {
		delegate().info(message, t);
	}

	@Override
	default void warn(@Nullable Object message) {
		delegate().warn(message);
	}

	@Override
	default void warn(@Nullable Object message, @Nullable Throwable t) {
		delegate().warn(message, t);
	}

	@Override
	default void error(@Nullable Object message) {
		delegate().error(message);
	}

	@Override
	default void error(@Nullable Object message, @Nullable Throwable t) {
		delegate().error(message, t);
	}

	@Override
	default void fatal(@Nullable Object message) {
		delegate().fatal(message);
	}

	@Override
	default void fatal(@Nullable Object message, @Nullable Throwable t) {
		delegate().fatal(message, t);
	}

}
