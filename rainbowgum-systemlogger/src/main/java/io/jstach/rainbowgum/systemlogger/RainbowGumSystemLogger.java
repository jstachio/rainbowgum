package io.jstach.rainbowgum.systemlogger;

import static java.util.Objects.requireNonNullElse;

import java.time.Instant;
import java.util.ResourceBundle;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogRouter.RootRouter;

/**
 * Rainbow Gum System Logger implementation. As noted in the module doc this can be used
 * by others without worry of it registering a {@link System.LoggerFinder}.
 *
 * @apiNote This class is final and cannot be extended. If you think it would be useful to
 * extend file an issue.
 */
public final class RainbowGumSystemLogger implements System.Logger {

	private final String loggerName;

	private final RootRouter router;

	/**
	 * Provides a system logger that will use the given root router.
	 * @param loggerName standard dotted logger name.
	 * @param router root router usually the global.
	 * @return un-cached system logger.
	 */
	public static RainbowGumSystemLogger of(String loggerName, RootRouter router) {
		return new RainbowGumSystemLogger(loggerName, router);
	}

	RainbowGumSystemLogger(String loggerName, RootRouter router) {
		super();
		this.loggerName = loggerName;
		this.router = router;
	}

	private static Level fixLevel(Level level) {
		if (level == Level.ALL) {
			return Level.TRACE;
		}
		return level;
	}

	@Override
	public String getName() {
		return this.loggerName;
	}

	@Override
	public boolean isLoggable(Level level) {
		return router.route(loggerName, fixLevel(level)).isEnabled();
	}

	@Override
	public void log(Level level, @Nullable String msg) {
		this.log(level, msg, (Throwable) null);
	}

	@SuppressWarnings("exports")
	@Override
	public void log(Level level, Supplier<@Nullable String> msgSupplier) {
		this.log(level, msgSupplier, (Throwable) null);
	}

	@Override
	public void log(Level level, Object obj) {
		level = fixLevel(level);
		var route = router.route(loggerName, level);
		if (route.isEnabled()) {
			String formattedMessage = obj == null ? "" : obj.toString();
			LogEvent event = LogEvent.of(level, loggerName, formattedMessage, null);
			route.log(event);
		}
	}

	@Override
	public void log(Level level, @Nullable String msg, @Nullable Throwable throwable) {
		level = fixLevel(level);
		var route = router.route(loggerName, level);
		if (route.isEnabled()) {
			String formattedMessage = requireNonNullElse(msg, "");
			LogEvent event = LogEvent.of(level, loggerName, formattedMessage, throwable);
			route.log(event);
		}
	}

	@SuppressWarnings("exports")
	@Override
	public void log(Level level, Supplier<@Nullable String> msgSupplier, @Nullable Throwable throwable) {
		level = fixLevel(level);
		var route = router.route(loggerName, level);
		if (route.isEnabled()) {
			String formattedMessage = requireNonNullElse(msgSupplier.get(), "");
			LogEvent event = LogEvent.of(level, loggerName, formattedMessage, throwable);
			route.log(event);
		}
	}

	@Override
	public void log(Level level, @Nullable ResourceBundle bundle, @Nullable String msg, @Nullable Throwable throwable) {
		/*
		 * TODO handle resource bundle
		 */
		this.log(level, msg, throwable);
	}

	@Override
	public void log(Level level, @Nullable ResourceBundle bundle, @Nullable String format, @Nullable Object... args) {
		/*
		 * TODO handle resource bundle
		 */
		level = fixLevel(level);
		var route = router.route(loggerName, level);
		if (route.isEnabled()) {
			Instant timestamp = Instant.now();
			String threadName = Thread.currentThread().getName();
			long threadId = Thread.currentThread().threadId();
			String message = requireNonNullElse(format, "");
			Throwable throwable = null;
			LogEvent event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, message, KeyValues.of(),
					throwable, StandardMessageFormatter.JUL, args);
			route.log(event);
		}
	}

}