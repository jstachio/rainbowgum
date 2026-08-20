package io.jstach.rainbowgum.tomcat;

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
		Log delegate = changeable ? new ChangeableRainbowGumTomcatLog(loggerName, router) : switch (level) {
			case ALL -> new TomcatLevelLog.TraceLevelLog(loggerName, router);
			case TRACE -> new TomcatLevelLog.TraceLevelLog(loggerName, router);
			case DEBUG -> new TomcatLevelLog.DebugLevelLog(loggerName, router);
			case INFO -> new TomcatLevelLog.InfoLevelLog(loggerName, router);
			case WARNING -> new TomcatLevelLog.WarnLevelLog(loggerName, router);
			case ERROR -> new TomcatLevelLog.ErrorLevelLog(loggerName, router);
			case OFF -> new TomcatLevelLog.OffLevelLog(loggerName, router);
		};
		this.delegate = delegate;

	}

	@Override
	public Log delegate() {
		return this.delegate;
	}

}