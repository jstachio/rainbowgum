package io.jstach.rainbowgum.systemlogger;

import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogRouter;

/**
 * Rainbow Gum System Logger implementation. As noted in the module doc this can be used
 * by others without worry of it registering a {@link System.LoggerFinder}.
 *
 * @apiNote This class is final and cannot be extended. If you think it would be useful to
 * extend file an issue.
 */
public final class RainbowGumSystemLogger implements System.Logger {

	private final String loggerName;

	private final LogRouter router;

	private final SystemLoggerEventFactory eventFactory;

	/**
	 * Provides a system logger that will use the given root router.
	 * @param loggerName standard dotted logger name.
	 * @param router root router usually the global.
	 * @return un-cached system logger.
	 */
	public static RainbowGumSystemLogger of(String loggerName, LogRouter router) {
		return new RainbowGumSystemLogger(loggerName, router);
	}

	RainbowGumSystemLogger(String loggerName, LogRouter router) {
		super();
		this.loggerName = loggerName;
		this.router = router;
		this.eventFactory = new SystemLoggerEventFactory(loggerName);
	}

	private LogEvent event(Level level, @Nullable String formattedMessage, @Nullable Throwable throwable) {
		return eventFactory.eventNoArg(level, formattedMessage, throwable);
	}

	@Override
	public String getName() {
		return this.loggerName;
	}

	@Override
	public boolean isLoggable(Level level) {
		return router.route(loggerName, SystemLoggerEventFactory.fixLevel(level)).isEnabled();
	}

	@Override
	public void log(Level level, @Nullable String msg) {
		this._log(level, msg, (Throwable) null);
	}

	/*
	 * java.lang.System.Logger#log(Level, Supplier<String>) declares Supplier<String>, not
	 * Supplier<@Nullable String>, but like the single-String log(Level, String) overload
	 * right above, a supplier returning null is treated the same as no message - accepted
	 * wider than the JDK's own unannotated interface requires, same reasoning as
	 * rainbowgum-slf4j's MDCAdapter/LoggingEventBuilder astubs.
	 */
	@SuppressWarnings({ "exports", "NullAway" })
	@Override
	public void log(Level level, Supplier<@Nullable String> msgSupplier) {
		this._log(level, msgSupplier, (Throwable) null);
	}

	@Override
	public void log(Level level, Object obj) {
		Objects.requireNonNull(obj, "obj");
		level = SystemLoggerEventFactory.fixLevel(level);
		var route = router.route(loggerName, level);
		if (route.isEnabled()) {
			String formattedMessage = obj == null ? "" : obj.toString();
			LogEvent event = event(level, formattedMessage, null);
			route.log(event);
		}
	}

	@Override
	public void log(Level level, @Nullable String msg, @Nullable Throwable throwable) {
		this._log(level, msg, throwable);
	}

	// To keep call depth consistent
	private void _log(Level level, @Nullable String msg, @Nullable Throwable throwable) {
		level = SystemLoggerEventFactory.fixLevel(level);
		var route = router.route(loggerName, level);
		if (route.isEnabled()) {
			LogEvent event = event(level, msg, throwable);
			route.log(event);
		}
	}

	@SuppressWarnings({ "exports", "NullAway" })
	@Override
	public void log(Level level, Supplier<@Nullable String> msgSupplier, @Nullable Throwable throwable) {
		this._log(level, msgSupplier, throwable);
	}

	// To keep call depth consistent
	private void _log(Level level, Supplier<@Nullable String> msgSupplier, @Nullable Throwable throwable) {
		level = SystemLoggerEventFactory.fixLevel(level);
		var route = router.route(loggerName, level);
		if (route.isEnabled()) {
			String formattedMessage = msgSupplier.get();
			LogEvent event = event(level, formattedMessage, throwable);
			route.log(event);
		}
	}

	@Override
	public void log(Level level, @Nullable ResourceBundle bundle, @Nullable String msg, @Nullable Throwable throwable) {
		_log(level, bundle, msg, throwable);
	}

	private void _log(Level level, @Nullable ResourceBundle bundle, @Nullable String msg,
			@Nullable Throwable throwable) {
		level = SystemLoggerEventFactory.fixLevel(level);
		var route = router.route(loggerName, level);
		if (route.isEnabled()) {
			String formattedMessage = eventFactory.message(bundle, msg);
			LogEvent event = event(level, formattedMessage, throwable);
			route.log(event);
		}
	}

	@Override
	public void log(Level level, @Nullable ResourceBundle bundle, @Nullable String format, @Nullable Object... args) {
		this._log(level, bundle, format, args);
	}

	// To keep call depth consistent.
	private void _log(Level level, @Nullable ResourceBundle bundle, @Nullable String format, @Nullable Object... args) {
		level = SystemLoggerEventFactory.fixLevel(level);
		var route = router.route(loggerName, level);
		if (route.isEnabled()) {
			String message = eventFactory.message(bundle, format);
			LogEvent event = eventFactory.eventArgs(level, message, args);
			route.log(event);
		}
	}

}