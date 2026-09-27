package io.jstach.rainbowgum.log4j;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.message.MessageFactory;
import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogRouter;
import io.jstach.rainbowgum.LogRouter.RootRouter;

/**
 * Used for a logger name that is <strong>not</strong> level changeable (see
 * {@code io.jstach.rainbowgum.LogConfig.ChangePublisher#allowedChanges(String)}): the
 * common case. {@link RootRouter#levelResolver()} is consulted exactly once, in
 * {@link #of(String, MessageFactory)}, and the result picks one of the small, level
 * specific subclasses below, each comparing {@link #isEnabled(Level)}'s argument against
 * its own {@code private static final} {@link Level} constant instead of asking the
 * router again. A logger vended here never picks up a level change made after it was
 * created; {@link RainbowGumLogger} is used instead whenever the name is changeable, and
 * re-resolves the level on every call.
 */
sealed abstract class LevelLogger extends AbstractLog4jLogger permits LevelLogger.ErrorLogger, LevelLogger.WarnLogger,
		LevelLogger.InfoLogger, LevelLogger.DebugLogger, LevelLogger.TraceLogger, LevelLogger.OffLogger {

	private LevelLogger(String name, @Nullable MessageFactory messageFactory, RootRouter router) {
		super(name, messageFactory, router);
	}

	/**
	 * Resolves {@code name}'s effective level once against {@link LogRouter#global()} and
	 * returns the matching level specific logger.
	 * @param name logger name.
	 * @param messageFactory message factory, or <code>null</code> for the default.
	 * @return new logger, never <code>null</code>.
	 */
	static LevelLogger of(String name, @Nullable MessageFactory messageFactory) {
		var router = LogRouter.global();
		var level = router.levelResolver().resolveLevel(name);
		return switch (level) {
			case ERROR -> new ErrorLogger(name, messageFactory, router);
			case WARNING -> new WarnLogger(name, messageFactory, router);
			case INFO -> new InfoLogger(name, messageFactory, router);
			case DEBUG -> new DebugLogger(name, messageFactory, router);
			case TRACE, ALL -> new TraceLogger(name, messageFactory, router);
			case OFF -> new OffLogger(name, messageFactory, router);
		};
	}

	/**
	 * This logger's fixed threshold, resolved once by
	 * {@link #of(String, MessageFactory)}.
	 * @return threshold level.
	 */
	abstract Level threshold();

	@Override
	public final Level getLevel() {
		return threshold();
	}

	@Override
	public final boolean isEnabled(Level level) {
		return level.intLevel() <= threshold().intLevel();
	}

	static final class ErrorLogger extends LevelLogger {

		private static final Level THRESHOLD = Level.ERROR;

		ErrorLogger(String name, @Nullable MessageFactory messageFactory, RootRouter router) {
			super(name, messageFactory, router);
		}

		@Override
		Level threshold() {
			return THRESHOLD;
		}

	}

	static final class WarnLogger extends LevelLogger {

		private static final Level THRESHOLD = Level.WARN;

		WarnLogger(String name, @Nullable MessageFactory messageFactory, RootRouter router) {
			super(name, messageFactory, router);
		}

		@Override
		Level threshold() {
			return THRESHOLD;
		}

	}

	static final class InfoLogger extends LevelLogger {

		private static final Level THRESHOLD = Level.INFO;

		InfoLogger(String name, @Nullable MessageFactory messageFactory, RootRouter router) {
			super(name, messageFactory, router);
		}

		@Override
		Level threshold() {
			return THRESHOLD;
		}

	}

	static final class DebugLogger extends LevelLogger {

		private static final Level THRESHOLD = Level.DEBUG;

		DebugLogger(String name, @Nullable MessageFactory messageFactory, RootRouter router) {
			super(name, messageFactory, router);
		}

		@Override
		Level threshold() {
			return THRESHOLD;
		}

	}

	static final class TraceLogger extends LevelLogger {

		private static final Level THRESHOLD = Level.TRACE;

		TraceLogger(String name, @Nullable MessageFactory messageFactory, RootRouter router) {
			super(name, messageFactory, router);
		}

		@Override
		Level threshold() {
			return THRESHOLD;
		}

	}

	/*
	 * No isEnabled(Level) override needed: OFF's intLevel() is 0, and every real level
	 * (FATAL upward) has a strictly greater intLevel, so the shared
	 * "level.intLevel() <= threshold().intLevel()" comparison inherited from LevelLogger
	 * already evaluates to false for every level a caller could actually pass.
	 */
	static final class OffLogger extends LevelLogger {

		private static final Level THRESHOLD = Level.OFF;

		OffLogger(String name, @Nullable MessageFactory messageFactory, RootRouter router) {
			super(name, messageFactory, router);
		}

		@Override
		Level threshold() {
			return THRESHOLD;
		}

	}

}
