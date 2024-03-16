package io.jstach.rainbowgum.slf4j;

import static org.slf4j.event.EventConstants.DEBUG_INT;
import static org.slf4j.event.EventConstants.ERROR_INT;
import static org.slf4j.event.EventConstants.INFO_INT;
import static org.slf4j.event.EventConstants.TRACE_INT;
import static org.slf4j.event.EventConstants.WARN_INT;

import java.lang.StackWalker.Option;

import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.slf4j.spi.NOPLoggingEventBuilder;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogEventLogger;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAware;

class ChangeableLogger implements BaseLogger, DepthAware {

	private final String loggerName;

	private final LogEventLogger eventLogger;

	private final RainbowGumMDCAdapter mdc;

	private volatile int level;

	private volatile boolean callerInfo;

	private static final int DEPTH_DELTA = 7;

	private int depth = DEPTH_DELTA;

	ChangeableLogger(String loggerName, LogEventLogger eventLogger, RainbowGumMDCAdapter mdc, int level,
			boolean callerInfo) {
		super();
		this.loggerName = loggerName;
		this.eventLogger = eventLogger;
		this.mdc = mdc;
		this.level = level;
		this.callerInfo = callerInfo;
	}

	@Override
	public RainbowGumMDCAdapter mdc() {
		return mdc;
	}

	@Override
	public String loggerName() {
		return this.loggerName;
	}

	@Override
	public void handle(LogEvent event) {
		/*
		 * TODO perhaps we wrap callerInfo here instead.
		 */
		eventLogger.log(event);
	}

	@Override
	public void handle(LogEvent event, int depth) {
		var e = addCallerInfo(event, depth);
		handle(e);
	}

	void setLevel(int level) {
		this.level = level;
	}

	@Override
	public void setDepth(int index, int depth) {
		this.depth = index + DEPTH_DELTA;

	}

	@Override
	public String toString() {
		return "ChangeableLogger[loggerName=" + loggerName + ", level=" + level + "]";
	}

	private static final StackWalker stackWalker = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);

	private @Nullable Caller caller(int depth) {
		return stackWalker.walk(s -> s.skip(depth).limit(1).map(f -> Caller.of(f)).findFirst().orElse(null));

	}

	@Override
	public LogEvent event(Level level, String formattedMessage, @Nullable Throwable throwable) {
		return addCallerInfo(BaseLogger.super.event(level, formattedMessage, throwable));
	}

	LogEvent addCallerInfo(LogEvent e) {
		return addCallerInfo(e, this.depth);
	}

	LogEvent addCallerInfo(LogEvent e, int depth) {
		if (callerInfo) {
			var found = caller(depth);
			if (found != null) {
				return LogEvent.withCaller(e, found);
			}
		}
		return e;
	}

	@Override
	public LogEvent event0(Level level, String formattedMessage) {
		return addCallerInfo(BaseLogger.super.event0(level, formattedMessage));
	}

	@Override
	public LogEvent event1(Level level, String message, Object arg1) {
		return addCallerInfo(BaseLogger.super.event1(level, message, arg1));
	}

	@Override
	public LogEvent event2(Level level, String message, Object arg1, Object arg2) {
		return addCallerInfo(BaseLogger.super.event2(level, message, arg1, arg2));
	}

	@Override
	public LogEvent eventArray(Level level, String message, Object[] args) {
		return addCallerInfo(BaseLogger.super.eventArray(level, message, args));
	}

	@Override
	public LoggingEventBuilder atError() {
		if (isErrorEnabled()) {
			return makeLoggingEventBuilder(Level.ERROR);
		}
		else {
			return NOPLoggingEventBuilder.singleton();
		}
	}

	@Override
	public boolean isErrorEnabled() {
		return this.level <= ERROR_INT;
	}

	@Override
	public void error(String msg) {
		if (isErrorEnabled()) {
			handle(Level.ERROR, msg);
		}
	}

	@Override
	public void error(String format, Object arg) {
		if (isErrorEnabled()) {
			handle(Level.ERROR, format, arg);
		}
	}

	@Override
	public void error(String format, Object arg1, Object arg2) {
		if (isErrorEnabled()) {
			handle(Level.ERROR, format, arg1, arg2);
		}
	}

	@Override
	public void error(String format, Object... arguments) {
		if (isErrorEnabled()) {
			handleArray(Level.ERROR, format, arguments);
		}
	}

	@Override
	public boolean isErrorEnabled(Marker marker) {
		return isErrorEnabled();
	}

	@Override
	public void error(Marker marker, String msg) {
		if (isErrorEnabled()) {
			handle(Level.ERROR, msg);
		}
	}

	@Override
	public void error(Marker marker, String format, Object arg) {
		if (isErrorEnabled()) {
			handle(Level.ERROR, format, arg);
		}
	}

	@Override
	public void error(Marker marker, String format, Object arg1, Object arg2) {
		if (isErrorEnabled()) {
			handle(Level.ERROR, format, arg1, arg2);
		}
	}

	@Override
	public void error(Marker marker, String format, Object... argArray) {
		if (isErrorEnabled()) {
			handleArray(Level.ERROR, format, argArray);
		}
	}

	@Override
	public void error(Marker marker, String msg, Throwable t) {
		if (isErrorEnabled()) {
			handle(Level.ERROR, msg, t);
		}
	}

	@Override
	public LoggingEventBuilder atWarn() {
		if (isWarnEnabled()) {
			return makeLoggingEventBuilder(Level.WARN);
		}
		else {
			return NOPLoggingEventBuilder.singleton();
		}
	}

	@Override
	public boolean isWarnEnabled() {
		return this.level <= WARN_INT;
	}

	@Override
	public void warn(String msg) {
		if (isWarnEnabled()) {
			handle(Level.WARN, msg);
		}
	}

	@Override
	public void warn(String format, Object arg) {
		if (isWarnEnabled()) {
			handle(Level.WARN, format, arg);
		}
	}

	@Override
	public void warn(String format, Object arg1, Object arg2) {
		if (isWarnEnabled()) {
			handle(Level.WARN, format, arg1, arg2);
		}
	}

	@Override
	public void warn(String format, Object... arguments) {
		if (isWarnEnabled()) {
			handleArray(Level.WARN, format, arguments);
		}
	}

	@Override
	public boolean isWarnEnabled(Marker marker) {
		return isWarnEnabled();
	}

	@Override
	public void warn(Marker marker, String msg) {
		if (isWarnEnabled()) {
			handle(Level.WARN, msg);
		}
	}

	@Override
	public void warn(Marker marker, String format, Object arg) {
		if (isWarnEnabled()) {
			handle(Level.WARN, format, arg);
		}
	}

	@Override
	public void warn(Marker marker, String format, Object arg1, Object arg2) {
		if (isWarnEnabled()) {
			handle(Level.WARN, format, arg1, arg2);
		}
	}

	@Override
	public void warn(Marker marker, String format, Object... argArray) {
		if (isWarnEnabled()) {
			handleArray(Level.WARN, format, argArray);
		}
	}

	@Override
	public void warn(Marker marker, String msg, Throwable t) {
		if (isWarnEnabled()) {
			handle(Level.WARN, msg, t);
		}
	}

	@Override
	public LoggingEventBuilder atInfo() {
		if (isInfoEnabled()) {
			return makeLoggingEventBuilder(Level.INFO);
		}
		else {
			return NOPLoggingEventBuilder.singleton();
		}
	}

	@Override
	public boolean isInfoEnabled() {
		return this.level <= INFO_INT;
	}

	@Override
	public void info(String msg) {
		if (isInfoEnabled()) {
			handle(Level.INFO, msg);
		}
	}

	@Override
	public void info(String format, Object arg) {
		if (isInfoEnabled()) {
			handle(Level.INFO, format, arg);
		}
	}

	@Override
	public void info(String format, Object arg1, Object arg2) {
		if (isInfoEnabled()) {
			handle(Level.INFO, format, arg1, arg2);
		}
	}

	@Override
	public void info(String format, Object... arguments) {
		if (isInfoEnabled()) {
			handleArray(Level.INFO, format, arguments);
		}
	}

	@Override
	public boolean isInfoEnabled(Marker marker) {
		return isInfoEnabled();
	}

	@Override
	public void info(Marker marker, String msg) {
		if (isInfoEnabled()) {
			handle(Level.INFO, msg);
		}
	}

	@Override
	public void info(Marker marker, String format, Object arg) {
		if (isInfoEnabled()) {
			handle(Level.INFO, format, arg);
		}
	}

	@Override
	public void info(Marker marker, String format, Object arg1, Object arg2) {
		if (isInfoEnabled()) {
			handle(Level.INFO, format, arg1, arg2);
		}
	}

	@Override
	public void info(Marker marker, String format, Object... argArray) {
		if (isInfoEnabled()) {
			handleArray(Level.INFO, format, argArray);
		}
	}

	@Override
	public void info(Marker marker, String msg, Throwable t) {
		if (isInfoEnabled()) {
			handle(Level.INFO, msg, t);
		}
	}

	@Override
	public LoggingEventBuilder atDebug() {
		if (isDebugEnabled()) {
			return makeLoggingEventBuilder(Level.DEBUG);
		}
		else {
			return NOPLoggingEventBuilder.singleton();
		}
	}

	@Override
	public boolean isDebugEnabled() {
		return this.level <= DEBUG_INT;
	}

	@Override
	public void debug(String msg) {
		if (isDebugEnabled()) {
			handle(Level.DEBUG, msg);
		}
	}

	@Override
	public void debug(String format, Object arg) {
		if (isDebugEnabled()) {
			handle(Level.DEBUG, format, arg);
		}
	}

	@Override
	public void debug(String format, Object arg1, Object arg2) {
		if (isDebugEnabled()) {
			handle(Level.DEBUG, format, arg1, arg2);
		}
	}

	@Override
	public void debug(String format, Object... arguments) {
		if (isDebugEnabled()) {
			handleArray(Level.DEBUG, format, arguments);
		}
	}

	@Override
	public boolean isDebugEnabled(Marker marker) {
		return isDebugEnabled();
	}

	@Override
	public void debug(Marker marker, String msg) {
		if (isDebugEnabled()) {
			handle(Level.DEBUG, msg);
		}
	}

	@Override
	public void debug(Marker marker, String format, Object arg) {
		if (isDebugEnabled()) {
			handle(Level.DEBUG, format, arg);
		}
	}

	@Override
	public void debug(Marker marker, String format, Object arg1, Object arg2) {
		if (isDebugEnabled()) {
			handle(Level.DEBUG, format, arg1, arg2);
		}
	}

	@Override
	public void debug(Marker marker, String format, Object... argArray) {
		if (isDebugEnabled()) {
			handleArray(Level.DEBUG, format, argArray);
		}
	}

	@Override
	public void debug(Marker marker, String msg, Throwable t) {
		if (isDebugEnabled()) {
			handle(Level.DEBUG, msg, t);
		}
	}

	@Override
	public LoggingEventBuilder atTrace() {
		if (isTraceEnabled()) {
			return makeLoggingEventBuilder(Level.TRACE);
		}
		else {
			return NOPLoggingEventBuilder.singleton();
		}
	}

	@Override
	public boolean isTraceEnabled() {
		return this.level <= TRACE_INT;
	}

	@Override
	public void trace(String msg) {
		if (isTraceEnabled()) {
			handle(Level.TRACE, msg);
		}
	}

	@Override
	public void trace(String format, Object arg) {
		if (isTraceEnabled()) {
			handle(Level.TRACE, format, arg);
		}
	}

	@Override
	public void trace(String format, Object arg1, Object arg2) {
		if (isTraceEnabled()) {
			handle(Level.TRACE, format, arg1, arg2);
		}
	}

	@Override
	public void trace(String format, Object... arguments) {
		if (isTraceEnabled()) {
			handleArray(Level.TRACE, format, arguments);
		}
	}

	@Override
	public boolean isTraceEnabled(Marker marker) {
		return isTraceEnabled();
	}

	@Override
	public void trace(Marker marker, String msg) {
		if (isTraceEnabled()) {
			handle(Level.TRACE, msg);
		}
	}

	@Override
	public void trace(Marker marker, String format, Object arg) {
		if (isTraceEnabled()) {
			handle(Level.TRACE, format, arg);
		}
	}

	@Override
	public void trace(Marker marker, String format, Object arg1, Object arg2) {
		if (isTraceEnabled()) {
			handle(Level.TRACE, format, arg1, arg2);
		}
	}

	@Override
	public void trace(Marker marker, String format, Object... argArray) {
		if (isTraceEnabled()) {
			handleArray(Level.TRACE, format, argArray);
		}
	}

	@Override
	public void trace(Marker marker, String msg, Throwable t) {
		if (isTraceEnabled()) {
			handle(Level.TRACE, msg, t);
		}
	}

}
