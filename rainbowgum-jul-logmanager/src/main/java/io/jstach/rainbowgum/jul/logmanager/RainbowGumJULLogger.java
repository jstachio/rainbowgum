package io.jstach.rainbowgum.jul.logmanager;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.jul.JULBridge;

/**
 * A {@code java.util.logging.Logger} whose logging methods route directly to Rainbow Gum
 * via {@link JULBridge}, instead of the normal JUL dispatch (build a {@link LogRecord},
 * walk up the parent chain, hand it to each attached {@link java.util.logging.Handler}).
 * Only {@link #isLoggable(Level)} and {@link #log(LogRecord)} need overriding for
 * correctness: every other {@code Logger} convenience method ({@code info(String)},
 * {@code log(Level, String)}, etc.) already funnels through those two once
 * {@link #isLoggable(Level)} says a level is enabled, so there is no separate fast path
 * to wire up for each one individually.
 */
final class RainbowGumJULLogger extends Logger {

	RainbowGumJULLogger(String name) {
		super(name, null);
		// The inherited field is otherwise null (meaning "inherit from parent"),
		// which would make isLoggable's *default* implementation (unused here, but
		// still reachable via getLevel()/reflection) walk a parent chain that does
		// not exist for these loggers. ALL means "never used as a threshold since
		// isLoggable(Level) is overridden below to ask Rainbow Gum instead.
		super.setLevel(Level.ALL);
	}

	@Override
	public boolean isLoggable(Level level) {
		// getName() is @Nullable on the Logger base class in general (an anonymous
		// logger could have no name), but never actually null for a logger this class
		// vends: RainbowGumLogManager#getLogger(String) always passes a real name.
		String name = Objects.requireNonNullElse(getName(), "");
		return JULBridge.isLoggable(name, level);
	}

	@Override
	public void log(LogRecord record) {
		if (!isLoggable(record.getLevel())) {
			return;
		}
		JULBridge.publish(record);
	}

	/**
	 * Unsupported: level is resolved dynamically through Rainbow Gum's own LevelResolver
	 * on every {@link #isLoggable(Level)} call, not cached on this object, so there is
	 * nothing for a fixed level to override. Matches
	 * {@code org.apache.logging.log4j.jul.ApiLogger}'s handling of the same method.
	 */
	@Override
	public void setLevel(@Nullable Level newLevel) throws SecurityException {
	}

	/**
	 * Unsupported: logger hierarchy is name-based in Rainbow Gum (dotted-name prefix
	 * matching), not this object graph, so there is no parent {@code Logger} instance to
	 * set.
	 */
	@Override
	public void setParent(Logger parent) {
	}

}
