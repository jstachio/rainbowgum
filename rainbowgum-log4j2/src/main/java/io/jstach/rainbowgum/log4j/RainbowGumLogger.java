package io.jstach.rainbowgum.log4j;

import java.lang.StackWalker.StackFrame;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogRouter;

/**
 * Routes Log4j2 API calls straight into RainbowGum's {@link LogRouter#global()}.
 * <p>
 * {@link AbstractLogger} declares no abstract methods of its own (confirmed by
 * inspection: it tracks only a name and message factories, no level). The two
 * {@link #isEnabled(Level)}/{@link #isEnabled(Level, Marker)} overloads it exposes are
 * meaningless no-ops without a real backend, and every other {@code isEnabled}/
 * {@code logMessage} overload declared on
 * {@link org.apache.logging.log4j.spi.ExtendedLogger} ultimately funnels through those
 * two plus
 * {@link #logMessage(Level, Marker, String, StackTraceElement, Message, Throwable)}, so
 * those three are the only methods overridden here.
 * <p>
 * The {@code location} {@link StackTraceElement} Log4j2 hands to {@code logMessage} is
 * only populated when a caller explicitly opts into location-aware logging (Log4j2's
 * fluent {@code atLevel().log()} builder); ordinary {@code logger.info(...)} calls leave
 * it <code>null</code>. Caller info here is instead resolved the same way
 * {@code rainbowgum-jboss-logging}'s {@code RainbowGumJBossLogger} does it: a bounded
 * {@link StackWalker} walk past the {@code fqcn} boundary Log4j2 itself always supplies.
 * <p>
 * Log4j2's {@link Marker} is accepted (required by the interfaces overridden here) but
 * otherwise ignored. RainbowGum's {@link LogRouter} has no marker-based filtering or
 * routing concept, matching {@code rainbowgum-jboss-logging}'s own treatment of markers.
 */
final class RainbowGumLogger extends AbstractLogger implements LogEventFactory {

	private static final long serialVersionUID = 1L;

	private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

	/*
	 * Defensive bound so a caller whose fqcn never actually appears on the stack (a
	 * misconfigured wrapper) cannot make this walk unbounded. Mirrors
	 * rainbowgum-jboss-logging's RainbowGumJBossLogger#findCaller.
	 */
	private static final int MAX_FRAMES = 32;

	private final LogRouter router;

	RainbowGumLogger(String name, @Nullable MessageFactory messageFactory) {
		super(name, messageFactory);
		this.router = LogRouter.global();
	}

	@Override
	public String loggerName() {
		return getName();
	}

	@Override
	public KeyValues defaultKeyValues() {
		Map<String, String> mdc = ThreadContext.getImmutableContext();
		return mdc.isEmpty() ? KeyValues.of() : KeyValues.of(mdc);
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
		return router.route(getName(), translate(level)).isEnabled();
	}

	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker) {
		return isEnabled(level);
	}

	/*
	 * ExtendedLogger declares ~15 further isEnabled(Level, Marker, <message-type>,
	 * Throwable...) overloads as abstract. None are implemented concretely by
	 * AbstractLogger itself (confirmed against org.apache.logging.log4j:log4j-to-slf4j's
	 * own SLF4JLogger, the upstream reference implementation for bridging AbstractLogger
	 * to a backend with only level-based enablement: it implements the exact same set,
	 * every one delegating to a single level+marker check, ignoring the message
	 * entirely). RainbowGum's LogRouter has no message-content-based filtering, so all of
	 * these just delegate to isEnabled(Level, Marker) too.
	 */
	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable Message message,
			@Nullable Throwable throwable) {
		return isEnabled(level, marker);
	}

	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable CharSequence message,
			@Nullable Throwable throwable) {
		return isEnabled(level, marker);
	}

	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable Object message,
			@Nullable Throwable throwable) {
		return isEnabled(level, marker);
	}

	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message,
			@Nullable Throwable throwable) {
		return isEnabled(level, marker);
	}

	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message) {
		return isEnabled(level, marker);
	}

	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message,
			@Nullable Object @Nullable ... params) {
		return isEnabled(level, marker);
	}

	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0) {
		return isEnabled(level, marker);
	}

	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1) {
		return isEnabled(level, marker);
	}

	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1, @Nullable Object p2) {
		return isEnabled(level, marker);
	}

	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1, @Nullable Object p2, @Nullable Object p3) {
		return isEnabled(level, marker);
	}

	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4) {
		return isEnabled(level, marker);
	}

	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4, @Nullable Object p5) {
		return isEnabled(level, marker);
	}

	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4, @Nullable Object p5,
			@Nullable Object p6) {
		return isEnabled(level, marker);
	}

	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4, @Nullable Object p5,
			@Nullable Object p6, @Nullable Object p7) {
		return isEnabled(level, marker);
	}

	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4, @Nullable Object p5,
			@Nullable Object p6, @Nullable Object p7, @Nullable Object p8) {
		return isEnabled(level, marker);
	}

	@Override
	public boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4, @Nullable Object p5,
			@Nullable Object p6, @Nullable Object p7, @Nullable Object p8, @Nullable Object p9) {
		return isEnabled(level, marker);
	}

	@Override
	public void logMessage(Level level, @Nullable Marker marker, String fqcn, @Nullable StackTraceElement location,
			Message message, @Nullable Throwable throwable) {
		log(level, marker, fqcn, message, throwable);
	}

	/*
	 * ExtendedLogger's own abstract contract method (no location parameter). The 6-arg
	 * LocationAwareLogger#logMessage above just delegates here, since neither needs the
	 * location: caller info is resolved via findCaller(fqcn) instead.
	 */
	@Override
	public void logMessage(String fqcn, Level level, @Nullable Marker marker, Message message,
			@Nullable Throwable throwable) {
		log(level, marker, fqcn, message, throwable);
	}

	private void log(Level level, @Nullable Marker marker, String fqcn, Message message,
			@Nullable Throwable throwable) {
		var sysLevel = translate(level);
		var route = router.route(getName(), sysLevel);
		if (!route.isEnabled()) {
			return;
		}
		LogEvent event = eventNoArg(sysLevel, message.getFormattedMessage(), throwable);
		route.log(withCaller(event, fqcn));
	}

	private static LogEvent withCaller(LogEvent event, String fqcn) {
		var caller = findCaller(fqcn);
		return caller == null ? event : LogEvent.withCaller(event, caller);
	}

	private static java.lang.System.Logger.Level translate(Level level) {
		int intLevel = level.intLevel();
		if (intLevel <= Level.ERROR.intLevel()) {
			return java.lang.System.Logger.Level.ERROR;
		}
		if (intLevel <= Level.WARN.intLevel()) {
			return java.lang.System.Logger.Level.WARNING;
		}
		if (intLevel <= Level.INFO.intLevel()) {
			return java.lang.System.Logger.Level.INFO;
		}
		if (intLevel <= Level.DEBUG.intLevel()) {
			return java.lang.System.Logger.Level.DEBUG;
		}
		return java.lang.System.Logger.Level.TRACE;
	}

	/*
	 * Mirrors rainbowgum-jboss-logging's RainbowGumJBossLogger#findCaller: skip frames
	 * until one's class name matches fqcn (the boundary Log4j2's own AbstractLogger
	 * machinery already computed for us), then skip the contiguous run of matching
	 * frames, and report the first frame after that run as the caller.
	 */
	private static @Nullable Caller findCaller(String fqcn) {
		return STACK_WALKER.walk(frames -> {
			var it = frames.limit(MAX_FRAMES).iterator();
			while (it.hasNext()) {
				if (fqcn.equals(it.next().getClassName())) {
					break;
				}
			}
			while (it.hasNext()) {
				StackFrame frame = it.next();
				if (!fqcn.equals(frame.getClassName())) {
					return Caller.of(frame);
				}
			}
			return null;
		});
	}

	@Override
	public String toString() {
		return "RainbowGumLogger [name=" + getName() + "]";
	}

}
