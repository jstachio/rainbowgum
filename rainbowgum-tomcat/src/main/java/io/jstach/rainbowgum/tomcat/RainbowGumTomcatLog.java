package io.jstach.rainbowgum.tomcat;

import java.lang.System.Logger.Level;

import org.apache.juli.logging.Log;

import io.jstach.rainbowgum.LogRouter;
import io.jstach.svc.ServiceProvider;

/**
 * Tomcat facade implementation.
 */
@ServiceProvider(Log.class)
public final class RainbowGumTomcatLog implements ForwardingTomcatLog {

	private final Log delegate;

	/**
	 * Tomcat does not actually call this constructor but it is required for ServiceLoader
	 * anyway.
	 */
	public RainbowGumTomcatLog() {
		/*
		 * The Tomcat gang does not understand how the service loader is supposed to be
		 * used in modern java.
		 */
		this("");
	}

	/**
	 * Tomcat will call this constructor with the logger name sadly through reflection.
	 * @param loggerName logger name.
	 */
	public RainbowGumTomcatLog(String loggerName) {
		var router = LogRouter.global();
		var level = router.levelResolver().resolveLevel(loggerName);
		boolean changeable = router.isChangeable(loggerName);
		if (changeable) {
			this.delegate = new ChangeableRainbowGumTomcatLog(loggerName, router);
		}
		else {
			// We want a logger that can handle all events.
			// TODO this is a common need and perhaps a method on LogRouter like
			// "eventLogger"
			var eventLogger = router.route(loggerName, Level.TRACE);
			Log delegate = switch (level) {
				case ALL -> new TomcatLevelLog.TraceLevelLog(loggerName, eventLogger);
				case TRACE -> new TomcatLevelLog.TraceLevelLog(loggerName, eventLogger);
				case DEBUG -> new TomcatLevelLog.DebugLevelLog(loggerName, eventLogger);
				case INFO -> new TomcatLevelLog.InfoLevelLog(loggerName, eventLogger);
				case WARNING -> new TomcatLevelLog.WarnLevelLog(loggerName, eventLogger);
				case ERROR -> new TomcatLevelLog.ErrorLevelLog(loggerName, eventLogger);
				case OFF -> new TomcatLevelLog.OffLevelLog(loggerName, eventLogger);
			};
			this.delegate = delegate;
		}
	}

	@Override
	public Log delegate() {
		return this.delegate;
	}

}