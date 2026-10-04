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
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.LogRouter;
import io.jstach.rainbowgum.LogRouter.RootRouter;

/**
 * Everything {@link LevelLogger} (level resolved once, cached) and
 * {@link RainbowGumLogger} (level resolved fresh on every call, for a changeable logger
 * name) share: {@link AbstractLogger}'s three real abstract methods minus
 * {@link #isEnabled(Level)} itself (each subclass's whole reason to exist), caller info,
 * MDC, and Log4j2's own combinatorial {@code isEnabled}/{@code logMessage} overload
 * surface.
 * <p>
 * {@link AbstractLogger} declares no abstract methods of its own (confirmed by
 * inspection: it tracks only a name and message factories, no level). The two
 * {@link #isEnabled(Level)}/{@link #isEnabled(Level, Marker)} overloads it exposes are
 * meaningless no-ops without a real backend, and every other {@code isEnabled}/
 * {@code logMessage} overload declared on
 * {@link org.apache.logging.log4j.spi.ExtendedLogger} ultimately funnels through those
 * two plus
 * {@link #logMessage(Level, Marker, String, StackTraceElement, Message, Throwable)}, so
 * those three (the last two implemented here, {@link #isEnabled(Level)} left abstract)
 * are the only methods overridden across this class and its two subclasses.
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
abstract class AbstractLog4jLogger extends AbstractLogger implements LogEventFactory {

	private static final long serialVersionUID = 1L;

	private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

	/*
	 * Defensive bound so a caller whose fqcn never actually appears on the stack (a
	 * misconfigured wrapper) cannot make this walk unbounded. Mirrors
	 * rainbowgum-jboss-logging's RainbowGumJBossLogger#findCaller.
	 */
	private static final int MAX_FRAMES = 32;

	/*
	 * Every other registered context store goes underneath ThreadContext.
	 */
	private static final KeyValuesContributor OTHER_KEY_VALUES = KeyValuesContributor
		.global(RainbowGumLog4jProvider.KEY_VALUES_CONTRIBUTOR_NAME);

	final RootRouter router;

	AbstractLog4jLogger(String name, @Nullable MessageFactory messageFactory, RootRouter router) {
		super(name, messageFactory);
		this.router = router;
	}

	@Override
	public final String loggerName() {
		return getName();
	}

	@Override
	public final KeyValues defaultKeyValues() {
		Map<String, String> mdc = ThreadContext.getImmutableContext();
		return KeyValues.merge(OTHER_KEY_VALUES.keyValues(), mdc.isEmpty() ? KeyValues.of() : KeyValues.of(mdc));
	}

	@Override
	public final boolean isEnabled(Level level, @Nullable Marker marker) {
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
	public final boolean isEnabled(Level level, @Nullable Marker marker, @Nullable Message message,
			@Nullable Throwable throwable) {
		return isEnabled(level, marker);
	}

	@Override
	public final boolean isEnabled(Level level, @Nullable Marker marker, @Nullable CharSequence message,
			@Nullable Throwable throwable) {
		return isEnabled(level, marker);
	}

	@Override
	public final boolean isEnabled(Level level, @Nullable Marker marker, @Nullable Object message,
			@Nullable Throwable throwable) {
		return isEnabled(level, marker);
	}

	@Override
	public final boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message,
			@Nullable Throwable throwable) {
		return isEnabled(level, marker);
	}

	@Override
	public final boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message) {
		return isEnabled(level, marker);
	}

	@Override
	public final boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message,
			@Nullable Object @Nullable ... params) {
		return isEnabled(level, marker);
	}

	@Override
	public final boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message,
			@Nullable Object p0) {
		return isEnabled(level, marker);
	}

	@Override
	public final boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1) {
		return isEnabled(level, marker);
	}

	@Override
	public final boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1, @Nullable Object p2) {
		return isEnabled(level, marker);
	}

	@Override
	public final boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1, @Nullable Object p2, @Nullable Object p3) {
		return isEnabled(level, marker);
	}

	@Override
	public final boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4) {
		return isEnabled(level, marker);
	}

	@Override
	public final boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4, @Nullable Object p5) {
		return isEnabled(level, marker);
	}

	@Override
	public final boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4, @Nullable Object p5,
			@Nullable Object p6) {
		return isEnabled(level, marker);
	}

	@Override
	public final boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4, @Nullable Object p5,
			@Nullable Object p6, @Nullable Object p7) {
		return isEnabled(level, marker);
	}

	@Override
	public final boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4, @Nullable Object p5,
			@Nullable Object p6, @Nullable Object p7, @Nullable Object p8) {
		return isEnabled(level, marker);
	}

	@Override
	public final boolean isEnabled(Level level, @Nullable Marker marker, @Nullable String message, @Nullable Object p0,
			@Nullable Object p1, @Nullable Object p2, @Nullable Object p3, @Nullable Object p4, @Nullable Object p5,
			@Nullable Object p6, @Nullable Object p7, @Nullable Object p8, @Nullable Object p9) {
		return isEnabled(level, marker);
	}

	@Override
	public final void logMessage(Level level, @Nullable Marker marker, String fqcn,
			@Nullable StackTraceElement location, Message message, @Nullable Throwable throwable) {
		log(level, fqcn, message, throwable);
	}

	/*
	 * ExtendedLogger's own abstract contract method (no location parameter). The 6-arg
	 * LocationAwareLogger#logMessage above just delegates here, since neither needs the
	 * location: caller info is resolved via findCaller(fqcn) instead.
	 */
	@Override
	public final void logMessage(String fqcn, Level level, @Nullable Marker marker, Message message,
			@Nullable Throwable throwable) {
		log(level, fqcn, message, throwable);
	}

	/*
	 * isEnabled(level) here is a defensive re-check, not the primary gate:
	 * AbstractLogger's own logIfEnabled(...) machinery already calls isEnabled(...)
	 * before ever reaching logMessage(...) for every ordinary call path.
	 * eventLogger(name), not route(name, level), is used for the sink deliberately:
	 * gating already happened above, so a second, redundant level check at the sink would
	 * be wasted work. See RootRouter#eventLogger's own javadoc for this exact "level
	 * gating already handled independently" use case.
	 */
	private void log(Level level, String fqcn, Message message, @Nullable Throwable throwable) {
		if (!isEnabled(level)) {
			return;
		}
		var sysLevel = translate(level);
		LogEvent event = eventNoArg(sysLevel, message.getFormattedMessage(), throwable);
		router.eventLogger(getName()).log(withCaller(event, fqcn));
	}

	private static LogEvent withCaller(LogEvent event, String fqcn) {
		var caller = findCaller(fqcn);
		return caller == null ? event : LogEvent.withCaller(event, caller);
	}

	static java.lang.System.Logger.Level translate(Level level) {
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
		return getClass().getSimpleName() + " [name=" + getName() + "]";
	}

}
