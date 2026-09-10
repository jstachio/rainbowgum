package io.jstach.rainbowgum.jcl;

import org.apache.commons.logging.Log;

import io.jstach.rainbowgum.LogRouter;

/**
 * Rainbow Gum's {@link Log} implementation, created by {@link RainbowGumLogFactory}.
 */
final class RainbowGumLog implements ForwardingLog {

	private final Log delegate;

	RainbowGumLog(String loggerName) {
		this(loggerName, LogRouter.global());
	}

	/**
	 * Allows a specific router to be used instead of the {@linkplain LogRouter#global()
	 * global router} - mainly so tests are not coupled to (and do not have to synchronize
	 * around) the global router's static, JVM-wide state.
	 * @param loggerName logger name.
	 * @param router router to use instead of the global router.
	 */
	RainbowGumLog(String loggerName, LogRouter.RootRouter router) {
		var level = router.levelResolver().resolveLevel(loggerName);
		boolean changeable = router.isChangeable(loggerName);
		if (changeable) {
			this.delegate = new ChangeableRainbowGumLog(loggerName, router);
		}
		else {
			var eventLogger = router.eventLogger(loggerName);
			Log delegate = switch (level) {
				case ALL -> new LevelLog.TraceLevelLog(loggerName, eventLogger);
				case TRACE -> new LevelLog.TraceLevelLog(loggerName, eventLogger);
				case DEBUG -> new LevelLog.DebugLevelLog(loggerName, eventLogger);
				case INFO -> new LevelLog.InfoLevelLog(loggerName, eventLogger);
				case WARNING -> new LevelLog.WarnLevelLog(loggerName, eventLogger);
				case ERROR -> new LevelLog.ErrorLevelLog(loggerName, eventLogger);
				case OFF -> new LevelLog.OffLevelLog(loggerName, eventLogger);
			};
			this.delegate = delegate;
		}
	}

	@Override
	public Log delegate() {
		return this.delegate;
	}

}
