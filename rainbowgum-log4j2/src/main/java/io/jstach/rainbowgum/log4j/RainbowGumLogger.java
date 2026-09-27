package io.jstach.rainbowgum.log4j;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.message.MessageFactory;
import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogRouter;

/**
 * Used for a logger name that <strong>is</strong> level changeable (see
 * {@code io.jstach.rainbowgum.LogConfig.ChangePublisher#allowedChanges(String)}): unlike
 * {@link LevelLogger}, the level is re-resolved against
 * {@link LogRouter.RootRouter#levelResolver()} on every {@link #isEnabled(Level)} call,
 * so a level change made after this logger was created is picked up on the next call.
 * Deliberately simple compared to {@code rainbowgum-slf4j}'s changeable logger
 * ({@code ReplaceableLogger}, which caches the level and only updates it via an explicit
 * change subscription): re-resolving here is just one more
 * {@link LogRouter.RootRouter#route(String, java.lang.System.Logger.Level)} call, no
 * subscription bookkeeping.
 */
final class RainbowGumLogger extends AbstractLog4jLogger {

	RainbowGumLogger(String name, @Nullable MessageFactory messageFactory) {
		super(name, messageFactory, LogRouter.global());
	}

	/*
	 * Most verbose first: RainbowGum's Route only answers isEnabled() for a level queried
	 * on demand (no stored "configured level" to read back), so getLevel() below walks
	 * this list and reports the most verbose level still enabled (the effective
	 * threshold), assuming (as RainbowGum's own level filtering guarantees) enabling a
	 * level implies every less-verbose level is enabled too.
	 */
	private static final Level[] LEVELS_MOST_VERBOSE_FIRST = { Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN,
			Level.ERROR, Level.FATAL };

	@Override
	public Level getLevel() {
		for (Level level : LEVELS_MOST_VERBOSE_FIRST) {
			if (isEnabled(level)) {
				return level;
			}
		}
		return Level.OFF;
	}

	@Override
	public boolean isEnabled(Level level) {
		return router.route(getName(), AbstractLog4jLogger.translate(level)).isEnabled();
	}

}
