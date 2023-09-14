package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.eclipse.jdt.annotation.Nullable;

public interface LogRouter {

	boolean isEnabled(
			String loggerName,
			java.lang.System.Logger.Level level);

	default void log(
			String loggerName,
			java.lang.System.Logger.Level level,
			String message,
			@Nullable Throwable cause) {
		log(LogEvent.of(level, loggerName, message, cause));
	}

	void log(
			LogEvent event);
	
	public static void setRouter(LogRouter router) {
		GlobalLogRouter.INSTANCE.drain(router);
	}
	
	public static LogRouter of() {
		return GlobalLogRouter.INSTANCE;
	}
	
	public static LogRouter of(List<? extends LogAppender> appenders) {
		return new DefaultLogRouter(appenders);
	}
}

class DefaultLogRouter implements LogRouter {
	
	private final List<? extends LogAppender> logAppenders;

	public DefaultLogRouter(
			List<? extends LogAppender> logAppenders) {
		super();
		this.logAppenders = logAppenders;
	}

	@Override
	public boolean isEnabled(
			String loggerName,
			Level level) {
		return true;
	}


	@Override
	public void log(
			LogEvent event) {
		for (var a : logAppenders) {
			a.append(event);
		}
	}
	
}
enum GlobalLogRouter implements LogRouter {
	INSTANCE;

	private final ConcurrentLinkedQueue<LogEvent> events = new ConcurrentLinkedQueue<>();
	private volatile @Nullable LogRouter delegate = null;

	public boolean isEnabled(
			String loggerName,
			java.lang.System.Logger.Level level) {
		LogRouter d = delegate;
		if (d != null) {
			// Logger logger = LoggerFactory.getILoggerFactory()
			// .getLogger(loggerName);
			// var slevel = toSlf4jLevel(level);
			// return isEnabled(logger, slevel);
			return d.isEnabled(loggerName, level);
		}
		return level.compareTo(System.Logger.Level.DEBUG) > 0;
	}

	public synchronized void log(
			String loggerName,
			java.lang.System.Logger.Level level,
			String message,
			@Nullable Throwable cause) {
		LogRouter d = delegate;
		if (d != null) {
			d.log(loggerName, level, message, cause);
		}
		else {
			events.add(LogEvent.of(level, loggerName, message, cause));
		}

	}

	public synchronized void drain(
			LogRouter delegate) {
		this.delegate = Objects.requireNonNull(delegate);
		LogEvent e;
		while ((e = this.events.poll()) != null) {
			delegate.log(e);
		}
	}

	@Override
	public void log(
			LogEvent event) {
		LogRouter d = delegate;
		if (d != null) {
			d.log(event);
		}
		else {
			events.add(event);
		}
	}
}



