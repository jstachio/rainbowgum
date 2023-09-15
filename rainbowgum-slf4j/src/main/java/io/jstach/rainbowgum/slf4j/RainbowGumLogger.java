package io.jstach.rainbowgum.slf4j;

import java.util.Collections;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.spi.MDCAdapter;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogEvent;

public class RainbowGumLogger extends LegacyAbstractLogger {

	private static final long serialVersionUID = 1L;
	protected static final int LOG_LEVEL_TRACE = java.lang.System.Logger.Level.TRACE.getSeverity();
	protected static final int LOG_LEVEL_DEBUG = java.lang.System.Logger.Level.DEBUG.getSeverity();
	protected static final int LOG_LEVEL_INFO = java.lang.System.Logger.Level.INFO.getSeverity();
	protected static final int LOG_LEVEL_WARN = java.lang.System.Logger.Level.WARNING.getSeverity();
	protected static final int LOG_LEVEL_ERROR = java.lang.System.Logger.Level.ERROR.getSeverity();
	
	// The OFF level can only be used in configuration files to disable logging.
	// It has
	// no printing method associated with it in o.s.Logger interface.
	protected static final int LOG_LEVEL_OFF = LOG_LEVEL_ERROR + 10;
	
	
	private final String name;
	private final LogAppender appender;
	private final int currentLogLevel;
	
	

	public RainbowGumLogger(
			String name,
			LogAppender appender,
			java.lang.System.Logger.Level level) {
		super();
		this.currentLogLevel = level.getSeverity();
		this.name = name;
		this.appender = appender;
	}


	@Override
	protected void handleNormalizedLoggingCall(
			Level level,
			Marker marker,
			String messagePattern,
			Object[] arguments,
			Throwable throwable) {


		String formattedMessage = MessageFormatter.basicArrayFormat(messagePattern, arguments);

		String loggerName = this.name;

		MDCAdapter adapter = MDC.getMDCAdapter();
		Map<String, @Nullable String> keyValues = Collections.emptyMap();
		if (adapter instanceof RainbowGumMDCAdapter simpleAdapter) {
			var m = simpleAdapter.getPropertyMap();
			if (m != null) {
				keyValues = m;
			}
		}

		LogEvent logEvent = LogEvent
			.of(Levels.toSystemLevel(level), loggerName, formattedMessage, keyValues, throwable);
		
		appender.append(logEvent);
	}
	
	
	/**
	 * Is the given log level currently enabled?
	 *
	 * @param logLevel
	 *            is this level enabled?
	 * @return whether the logger is enabled for the given level
	 */
	protected boolean isLevelEnabled(
			int logLevel) {
		// log level are numerically ordered so can use simple numeric
		// comparison
		return (logLevel >= currentLogLevel);
	}

	/** Are {@code trace} messages currently enabled? */
	public boolean isTraceEnabled() {
		return isLevelEnabled(LOG_LEVEL_TRACE);
	}

	/** Are {@code debug} messages currently enabled? */
	public boolean isDebugEnabled() {
		return isLevelEnabled(LOG_LEVEL_DEBUG);
	}

	/** Are {@code info} messages currently enabled? */
	public boolean isInfoEnabled() {
		return isLevelEnabled(LOG_LEVEL_INFO);
	}

	/** Are {@code warn} messages currently enabled? */
	public boolean isWarnEnabled() {
		return isLevelEnabled(LOG_LEVEL_WARN);
	}

	/** Are {@code error} messages currently enabled? */
	public boolean isErrorEnabled() {
		return isLevelEnabled(LOG_LEVEL_ERROR);
	}

	@Override
	protected String getFullyQualifiedCallerName() {
		return null;
	}


}
