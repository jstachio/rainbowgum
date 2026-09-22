package io.jstach.rainbowgum.jbosslogging;

import java.lang.StackWalker.StackFrame;

import org.jboss.logging.Logger;
import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogMessageFormatter;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogRouter;

/**
 * Routes JBoss Logging calls straight into RainbowGum's {@link LogRouter#global()}, with
 * correct caller info: {@code loggerClassName} is handed to every
 * {@link #doLog(Level, String, Object, Object[], Throwable) doLog}/
 * {@link #doLogf(Level, String, String, Object[], Throwable) doLogf} call by JBoss
 * Logging's own {@link Logger} base class (unlike SLF4J's own bridges, no fqcn search is
 * needed to find it - it is simply the boundary this class is walked past).
 * <p>
 * Parameter substitution for {@link #doLog} follows {@code java.text.MessageFormat}
 * ("<code>{0}</code>" style), matching JBoss Logging's own
 * {@code Slf4jLocationAwareLogger} and {@code JDKLogger}, done lazily via
 * {@link StandardMessageFormatter#JUL} rather than eagerly like upstream, consistent with
 * how {@code rainbowgum-systemlogger} handles the same JUL-style convention for
 * {@code System.Logger}.
 */
final class RainbowGumJBossLogger extends Logger implements LogEventFactory {

	private static final long serialVersionUID = 1L;

	private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

	/*
	 * Defensive bound so a caller whose loggerClassName never actually appears on the
	 * stack (a misconfigured wrapper, or someone calling doLog directly) cannot make this
	 * walk unbounded. Mirrors rainbowgum-slf4j's LocationAwareForwardingLogger - see
	 * todo.md's Commons Logging item for why this is duplicated rather than shared: the
	 * extraction into common depth/caller-info machinery hasn't happened yet.
	 */
	private static final int MAX_FRAMES = 32;

	private final LogRouter router;

	private final RainbowGumJBossLoggerProvider provider;

	RainbowGumJBossLogger(String name, RainbowGumJBossLoggerProvider provider) {
		super(name);
		this.router = LogRouter.global();
		this.provider = provider;
	}

	@Override
	public String loggerName() {
		return getName();
	}

	@Override
	public LogMessageFormatter messageFormatter() {
		return StandardMessageFormatter.JUL;
	}

	@Override
	public KeyValues defaultKeyValues() {
		var mdc = provider.mdcSnapshot();
		return mdc.isEmpty() ? KeyValues.of() : KeyValues.of(mdc);
	}

	@Override
	public boolean isEnabled(@Nullable Level level) {
		return router.route(getName(), translate(level)).isEnabled();
	}

	@Override
	protected void doLog(@Nullable Level level, String loggerClassName, @Nullable Object message,
			Object @Nullable [] parameters, @Nullable Throwable thrown) {
		var sysLevel = translate(level);
		var route = router.route(getName(), sysLevel);
		if (!route.isEnabled()) {
			return;
		}
		String msg = message == null ? null : message.toString();
		LogEvent event = eventArgs(sysLevel, msg, parameters, thrown);
		route.log(withCaller(event, loggerClassName));
	}

	@Override
	protected void doLogf(@Nullable Level level, String loggerClassName, @Nullable String format,
			Object @Nullable [] parameters, @Nullable Throwable thrown) {
		var sysLevel = translate(level);
		var route = router.route(getName(), sysLevel);
		if (!route.isEnabled()) {
			return;
		}
		String msg = format == null ? null
				: (parameters == null ? String.format(format) : String.format(format, parameters));
		LogEvent event = eventNoArg(sysLevel, msg, thrown);
		route.log(withCaller(event, loggerClassName));
	}

	private static LogEvent withCaller(LogEvent event, String loggerClassName) {
		var caller = findCaller(loggerClassName);
		return caller == null ? event : LogEvent.withCaller(event, caller);
	}

	private static java.lang.System.Logger.Level translate(@Nullable Level level) {
		if (level == null) {
			return java.lang.System.Logger.Level.INFO;
		}
		return switch (level) {
			case FATAL, ERROR -> java.lang.System.Logger.Level.ERROR;
			case WARN -> java.lang.System.Logger.Level.WARNING;
			case INFO -> java.lang.System.Logger.Level.INFO;
			case DEBUG -> java.lang.System.Logger.Level.DEBUG;
			case TRACE -> java.lang.System.Logger.Level.TRACE;
		};
	}

	/*
	 * Mirrors rainbowgum-slf4j's LocationAwareForwardingLogger.findCaller: skip frames
	 * until one's class name matches loggerClassName (the fqcn JBoss Logging's own Logger
	 * base class already computed for us), then skip the contiguous run of matching
	 * frames, and report the first frame after that run as the caller.
	 */
	private static @Nullable Caller findCaller(String loggerClassName) {
		return STACK_WALKER.walk(frames -> {
			var it = frames.limit(MAX_FRAMES).iterator();
			while (it.hasNext()) {
				if (loggerClassName.equals(it.next().getClassName())) {
					break;
				}
			}
			while (it.hasNext()) {
				StackFrame frame = it.next();
				if (!loggerClassName.equals(frame.getClassName())) {
					return Caller.of(frame);
				}
			}
			return null;
		});
	}

	@Override
	public String toString() {
		return "RainbowGumJBossLogger [name=" + getName() + "]";
	}

}
