package io.jstach.rainbowgum;

import java.io.PrintStream;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.annotation.EnumAlias;

/**
 * Alerts (sometimes called errors or status events elsewhere) are for reporting problems
 * with the logging system itself rather than application logging - for example an
 * appender failing to write, or an async publisher's queue overflowing. Because logging
 * itself may be broken these are not routed through the normal {@link LogRouter} but are
 * instead kept in a small in memory ring buffer (see {@link #dump()} and
 * {@link #stats()}). Warning and error alerts are also reported immediately (currently to
 * stderr); informational alerts remain available for diagnostics without producing output
 * by default.
 * <p>
 * An instance is available from every {@link LogConfig#alerts()}. Components that are
 * {@linkplain LogProvider provided} config, or that are
 * {@linkplain LogLifecycle#start( LogConfig) started} with config, should prefer
 * capturing {@code config.alerts()} over reaching for global state.
 * <p>
 * {@link LogAlerts} is itself a {@link LogLifecycle}: {@link #start(LogConfig)} is called
 * exactly once, after every
 * {@link io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator} has run, and is
 * where {@link UnobservedErrorsAction} (already resolved at construction, same as
 * capacity) gets applied against whatever has been recorded by then - see that enum for
 * what "nobody is listening yet" means and why it matters specifically at that moment
 * rather than for the life of the process.
 *
 * @see LogConfig#alerts()
 */
public sealed interface LogAlerts extends LogLifecycle permits DefaultLogAlerts, SwappableLogAlerts {

	/**
	 * Default capacity of the alert ring buffer.
	 */
	static final int DEFAULT_CAPACITY = 512;

	/**
	 * Records an alert.
	 * @param event event describing the alert.
	 */
	public void alert(LogEvent event);

	/**
	 * Records an alert.
	 * @param loggerName usually the class where the alert originated.
	 * @param throwable cause of the alert.
	 */
	default void error(Class<?> loggerName, Throwable throwable) {
		String m = Objects.requireNonNullElse(throwable.getMessage(), "exception");
		error(loggerName, m, throwable);
	}

	/**
	 * Records an alert.
	 * @param loggerName usually the class where the alert originated.
	 * @param message alert message.
	 * @param throwable cause of the alert.
	 */
	default void error(Class<?> loggerName, String message, Throwable throwable) {
		alert(event(loggerName, Level.ERROR, message, throwable));
	}

	/**
	 * Records a warning alert.
	 * @param loggerName usually the class where the alert originated.
	 * @param message alert message.
	 */
	default void warn(Class<?> loggerName, String message) {
		alert(event(loggerName, Level.WARNING, message, null));
	}

	/**
	 * Records an informational alert.
	 * @param loggerName usually the class where the alert originated.
	 * @param message alert message.
	 */
	default void info(Class<?> loggerName, String message) {
		alert(event(loggerName, Level.INFO, message, null));
	}

	private static LogEvent event(Class<?> loggerName, Level level, String message, @Nullable Throwable throwable) {
		var currentThread = Thread.currentThread();
		return LogEvent.of(Instant.now(), currentThread.getName(), currentThread.threadId(), level,
				loggerName.getName(), message, KeyValues.of(), throwable);
	}

	/**
	 * A snapshot of the alerts currently held in the ring buffer, oldest first. Alerts
	 * are evicted oldest first once the buffer is at {@link Stats#capacity()}.
	 * @return immutable snapshot.
	 */
	public List<LogEvent> dump();

	/**
	 * Clears the ring buffer. Does not reset {@link Stats#total()} - like a
	 * Prometheus/Micrometer counter, that is meant to be monotonically increasing for the
	 * life of the process; a downstream metrics system computes rate of change rather
	 * than relying on the counter itself being reset. Clearing diagnostics does not undo
	 * an alert that fails startup under {@link FailLevel}.
	 */
	public void clear();

	/**
	 * Stats about the alerts recorded so far.
	 * @return stats.
	 */
	public Stats stats();

	/**
	 * Registers a listener that is notified synchronously, in addition to the alert being
	 * recorded in the ring buffer, every time {@link #alert(LogEvent)} is called.
	 * <p>
	 * <strong>Listeners are held with a normal (strong) reference and are not
	 * automatically removed.</strong> This is deliberate: alert listeners are expected to
	 * be few and long lived - typically registered once (e.g. a metrics bridge or an
	 * ops/paging integration) for as long as the owning {@link LogConfig} is - rather
	 * than one per short lived object. A weak reference would risk silently dropping a
	 * listener (a lambda with no other strong reference could be collected almost
	 * immediately) which is the wrong failure mode for something whose entire job is not
	 * losing alerts. Call {@link AutoCloseable#close()} on the returned registration to
	 * unregister deterministically once the caller's own lifecycle ends.
	 * @param listener listener to register.
	 * @return registration; {@link AutoCloseable#close()} unregisters the listener.
	 */
	public AutoCloseable addListener(Listener listener);

	/**
	 * Notified of alerts as they happen. See {@link #addListener(Listener)}.
	 */
	@FunctionalInterface
	interface Listener {

		/**
		 * Called synchronously every time an alert is recorded. Should not throw -
		 * exceptions are caught and reported separately so a broken listener cannot
		 * disrupt alert recording or other listeners.
		 * @param event the alert.
		 */
		void onAlert(LogEvent event);

	}

	/**
	 * Stats about alerts recorded.
	 *
	 * @param total total number of alerts ever recorded, including ones since evicted
	 * from the ring buffer.
	 * @param size number of alerts currently held in the ring buffer.
	 * @param capacity maximum number of alerts the ring buffer holds.
	 */
	record Stats(long total, int size, int capacity) {
	}

	/**
	 * What building LogConfig and starting Rainbow Gum do with alerts recorded while
	 * starting, set by {@value LogProperties#ALERTS_FAIL_PROPERTY}. Checked once when
	 * {@link LogConfig} is built and once right after Rainbow Gum starts; alerts after
	 * that never fail anything. Evicting or clearing an alert from the diagnostic queue
	 * does not prevent it from failing startup.
	 */
	enum FailLevel {

		/**
		 * Default. Alerts never fail startup.
		 */
		@EnumAlias("false")
		OFF,
		/**
		 * Fail if an error alert is recorded while starting.
		 */
		ERROR,
		/**
		 * Fail if a warning or error alert is recorded while starting.
		 */
		@EnumAlias("true")
		WARNING;

		static FailLevel parse(String value) {
			return switch (value.toLowerCase(java.util.Locale.ROOT)) {
				case "true" -> WARNING;
				case "false" -> OFF;
				default -> LogProperty.enumValue(FailLevel.class, value, "true", "false");
			};
		}

		boolean fails(Level level) {
			return switch (this) {
				case OFF -> false;
				case ERROR -> level == Level.ERROR;
				case WARNING -> level == Level.ERROR || level == Level.WARNING;
			};
		}

	}

	/**
	 * What {@link #start(LogConfig)} does if, at that moment, at least one error alert
	 * has been recorded and still zero {@link Listener}s are registered - the situation
	 * Logback's {@code StatusManager} calls an unobserved status: nothing is watching, so
	 * whatever went wrong during property loading or a
	 * {@link io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator} would
	 * otherwise only ever have reached the individual, easy to miss stderr lines each
	 * {@link #alert(LogEvent)} call already produces.
	 * <p>
	 * Deliberately keyed as
	 * {@value LogProperties#ALERTS_UNOBSERVED_ERRORS_ACTION_PROPERTY} (an enum, not a
	 * boolean) since "fail" without "dump" first would throw away the only diagnostic
	 * evidence of why it failed.
	 */
	enum UnobservedErrorsAction {

		/**
		 * Do nothing beyond the immediate reporting of warning and error events.
		 */
		NONE,
		/**
		 * Default. Report the whole backlog to {@code MetaLog} as one clearly labeled
		 * block, the same way Logback auto-installs a console listener and prints its
		 * accumulated status if nothing else is watching by the end of configuration -
		 * RainbowGum still starts.
		 */
		DUMP,
		/**
		 * Same reporting as {@link #DUMP}, but then throws, so
		 * {@link LogConfig.Builder#build()} (and therefore whatever is building a
		 * {@link RainbowGum} from it) never completes. Goes further than Logback ever
		 * does - an explicit, opt-in choice of integrity over resilience for deployments
		 * where a silently-broken bootstrap is worse than refusing to start.
		 */
		FAIL;

		static UnobservedErrorsAction parse(String value) {
			return LogProperty.enumValue(UnobservedErrorsAction.class, value);
		}

	}

}

/*
 * Handed to PropertiesProviders. Buffers alerts until the configured LogAlerts exists,
 * then drainTo replays the buffer into it and forwards every later call, so a provider
 * may keep this instance and alert after provideProperties returns. Replay and swap
 * happen under one lock so a concurrent alert is neither lost nor reordered. Listener
 * registration is never supported: providers record alerts but cannot observe them.
 */
final class SwappableLogAlerts implements LogAlerts {

	private final List<LogEvent> events = new ArrayList<>();

	private long total;

	private volatile @Nullable LogAlerts delegate;

	@Override
	public void alert(LogEvent event) {
		var d = delegate;
		if (d == null) {
			synchronized (this) {
				d = delegate;
				if (d == null) {
					events.add(event.freeze());
					total++;
					return;
				}
			}
		}
		d.alert(event);
	}

	synchronized void drainTo(LogAlerts target) {
		if (delegate != null) {
			throw new IllegalStateException("Alerts have already been drained");
		}
		for (var event : events) {
			target.alert(event);
		}
		events.clear();
		delegate = target;
	}

	@Override
	public synchronized List<LogEvent> dump() {
		var d = delegate;
		return d != null ? d.dump() : List.copyOf(events);
	}

	@Override
	public synchronized void clear() {
		var d = delegate;
		if (d != null) {
			d.clear();
		}
		else {
			events.clear();
		}
	}

	@Override
	public synchronized Stats stats() {
		var d = delegate;
		return d != null ? d.stats() : new Stats(total, events.size(), Integer.MAX_VALUE);
	}

	@Override
	public AutoCloseable addListener(Listener listener) {
		throw new UnsupportedOperationException("Properties providers cannot register alert listeners");
	}

	@Override
	public void start(LogConfig config) {
		throw new UnsupportedOperationException("Properties provider alerts cannot be started");
	}

	@Override
	public synchronized void close() {
		// The drained delegate belongs to LogConfig, so only an undrained buffer is
		// dropped.
		if (delegate == null) {
			events.clear();
		}
	}

}

final class DefaultLogAlerts implements LogAlerts {

	private final LogEvent[] ring;

	private int start = 0;

	private int size = 0;

	private final AtomicLong total = new AtomicLong();

	private final AtomicLong errors = new AtomicLong();

	private final ReentrantLock lock = new ReentrantLock();

	private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

	/*
	 * Kept apart from listeners.isEmpty() deliberately: DefaultLogConfig's own
	 * metrics-bridge listener (see its constructor) is always registered before
	 * start(...) ever runs, and a passive in-process counter nobody has wired an exporter
	 * to is not "someone is watching" in the sense UnobservedErrorsAction cares about -
	 * only a real addListener(Listener) call (this class's public API) flips this.
	 */
	private volatile boolean hasExternalListener = false;

	private final LogEventFactory eventFactory = LogEventFactory.of(DefaultLogAlerts.class.getName());

	/*
	 * Resolved once, at construction (same as capacity) rather than inside start(...):
	 * unlike capacity this does not shape any constructor-time state, but LogProperties
	 * is available just as early, and start(LogConfig) is really about announcing "I have
	 * started" (config is there because a few edge cases needed it, not because this
	 * needed to be a start-time lookup).
	 */
	private final UnobservedErrorsAction unobservedErrorsAction;

	/*
	 * Private and deliberately not defensive - of(LogProperties) below is the only
	 * caller, and it has already validated both fields (capacity > 0, a resolvable
	 * UnobservedErrorsAction) before ever reaching this constructor. Re-checking here
	 * would be defensive programming duplicating what was already a real, reported
	 * validation failure one call up - see of(...)'s own comment.
	 */
	private final FailLevel failLevel;

	/*
	 * Names what set the fail level, for the failure message: the property, or
	 * logging.debug=help raising it.
	 */
	private final String failLevelSource;

	/*
	 * Startup failure is independent of the bounded diagnostic queue. Keep the first
	 * failure and the count until startup ends, even if clear() or eviction removes it
	 * from the queue. All three fields are protected by lock.
	 */
	private long startupFailures;

	private @Nullable LogEvent firstStartupFailure;

	private boolean startupComplete;

	private DefaultLogAlerts(int capacity, UnobservedErrorsAction unobservedErrorsAction, FailLevel failLevel,
			String failLevelSource) {
		this.ring = new LogEvent[capacity];
		this.unobservedErrorsAction = unobservedErrorsAction;
		this.failLevel = failLevel;
		this.failLevelSource = failLevelSource;
	}

	FailLevel failLevel() {
		return failLevel;
	}

	/*
	 * Checks the startup failure count and lists retained failing alerts, including the
	 * first failure even if it left the queue. Called when LogConfig is built and right
	 * after Rainbow Gum starts.
	 */
	void failIfAlerted(String phase) {
		checkStartup(phase, false);
	}

	void finishStartup() {
		checkStartup("starting", true);
	}

	private void checkStartup(String phase, boolean complete) {
		lock.lock();
		try {
			if (startupComplete) {
				return;
			}
			startupComplete = complete;
			var first = firstStartupFailure;
			if (first == null) {
				return;
			}
			var failing = new ArrayList<>(dump().stream().filter(e -> failLevel.fails(e.level())).toList());
			if (!failing.contains(first)) {
				failing.addFirst(first);
			}
			var sb = new StringBuilder();
			sb.append(startupFailures)
				.append(" alert(s) at ")
				.append(failLevel.name().toLowerCase(Locale.ROOT))
				.append(" or above were recorded while ")
				.append(phase)
				.append(" and ")
				.append(failLevelSource)
				.append(":");
			for (var e : failing) {
				sb.append("\n[").append(e.level()).append("] ").append(e.message());
			}
			if (startupFailures > failing.size()) {
				sb.append("\n")
					.append(startupFailures - failing.size())
					.append(" additional failing alert(s) are no longer retained.");
			}
			throw new IllegalStateException(sb.toString());
		}
		finally {
			if (complete) {
				firstStartupFailure = null;
			}
			lock.unlock();
		}
	}

	/*
	 * The fail level as resolved from properties, before LogAlerts exists, so LogConfig
	 * can decide whether to record property reads. Resolved again, and validated, by
	 * of(...).
	 */
	static FailLevel failLevelOr(LogProperties properties, FailLevel fallback) {
		var value = properties.valueOrNull(LogProperties.ALERTS_FAIL_PROPERTY);
		try {
			return value == null ? fallback : FailLevel.parse(value);
		}
		catch (RuntimeException e) {
			return fallback;
		}
	}

	/*
	 * Package-friend static factory: resolves and validates both
	 * LogProperties#ALERTS_CAPACITY_PROPERTY and
	 * LogProperties#ALERTS_UNOBSERVED_ERRORS_ACTION_PROPERTY against one shared Validator
	 * - a mistake in both at once is reported together instead of only the first one
	 * found - then constructs. requirePositiveCapacity is a plain int -> int validating
	 * transform (not an object construction) specifically so it can sit inside map(...)
	 * without tripping CheckerFramework's Initialization Checker, which does not refine a
	 * `new Foo(...)` expression back to @Initialized when it appears directly inside a
	 * lambda body passed through a generic method like map(...) - the one and only `new
	 * DefaultLogAlerts(...)` call stays a plain, unconditional statement here instead.
	 */
	static DefaultLogAlerts of(LogProperties properties, FailLevel failLevelFallback, boolean help) {
		var validator = LogProperty.Validator.of(LogAlerts.class);
		var failLevelResult = properties.forKey(LogProperties.ALERTS_FAIL_PROPERTY)
			.ofString()
			.map(FailLevel::parse)
			.or(failLevelFallback)
			.validateIfError(validator);
		var unobservedErrorsActionResult = properties.forKey(LogProperties.ALERTS_UNOBSERVED_ERRORS_ACTION_PROPERTY)
			.ofString()
			.map(UnobservedErrorsAction::parse)
			.or(UnobservedErrorsAction.DUMP)
			.validateIfError(validator);
		var capacityResult = properties.forKey(LogProperties.ALERTS_CAPACITY_PROPERTY)
			.ofInt()
			.or(LogAlerts.DEFAULT_CAPACITY)
			.map(DefaultLogAlerts::requirePositiveCapacity)
			.validateIfError(validator);
		validator.validate();
		var failLevel = failLevelResult.value();
		String failLevelSource = LogProperties.ALERTS_FAIL_PROPERTY + "=" + failLevel.name().toLowerCase(Locale.ROOT);
		if (help && failLevel != FailLevel.WARNING) {
			failLevel = FailLevel.WARNING;
			failLevelSource = "logging.debug=help";
		}
		return new DefaultLogAlerts(capacityResult.value(), unobservedErrorsActionResult.value(), failLevel,
				failLevelSource);
	}

	private static int requirePositiveCapacity(int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity should be greater than 0");
		}
		return capacity;
	}

	@Override
	public void alert(LogEvent event) {
		var frozen = event.freeze();
		total.incrementAndGet();
		if (frozen.level() == Level.ERROR) {
			errors.incrementAndGet();
		}
		lock.lock();
		try {
			if (!startupComplete && failLevel.fails(frozen.level())) {
				startupFailures++;
				if (firstStartupFailure == null) {
					firstStartupFailure = frozen;
				}
			}
			if (size < ring.length) {
				ring[(start + size) % ring.length] = frozen;
				size++;
			}
			else {
				ring[start] = frozen;
				start = (start + 1) % ring.length;
			}
		}
		finally {
			lock.unlock();
		}
		for (var listener : listeners) {
			try {
				listener.onAlert(frozen);
			}
			catch (Exception e) {
				MetaLog.INSTANCE.log(eventFactory.eventNoArg(Level.ERROR, "LogAlerts.Listener threw", e));
			}
		}
		if (frozen.level() == Level.WARNING || frozen.level() == Level.ERROR) {
			MetaLog.INSTANCE.log(frozen);
		}
	}

	@Override
	public AutoCloseable addListener(Listener listener) {
		listeners.add(listener);
		hasExternalListener = true;
		return () -> listeners.remove(listener);
	}

	/*
	 * DefaultLogConfig's own metrics-bridge listener goes through this instead of
	 * addListener(Listener) - see hasExternalListener's own comment for why.
	 */
	void addInternalListener(Listener listener) {
		listeners.add(listener);
	}

	@Override
	public void start(LogConfig config) {
		if (unobservedErrorsAction == UnobservedErrorsAction.NONE || hasExternalListener || errors.get() == 0) {
			return;
		}
		var backlog = dump();
		MetaLog.error(eventFactory.eventNoArg(Level.ERROR,
				backlog.size() + " alert(s) were recorded before any LogAlerts.Listener was registered - "
						+ "dumping the backlog now since nothing else will see it:",
				null));
		for (var event : backlog) {
			MetaLog.error(event);
		}
		if (unobservedErrorsAction == UnobservedErrorsAction.FAIL) {
			throw new IllegalStateException(
					backlog.size() + " alert(s) were recorded before any LogAlerts.Listener was registered and "
							+ LogProperties.ALERTS_UNOBSERVED_ERRORS_ACTION_PROPERTY + "=FAIL - refusing to start.");
		}
	}

	@Override
	public void close() {
		listeners.clear();
		lock.lock();
		try {
			startupComplete = true;
			firstStartupFailure = null;
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public List<LogEvent> dump() {
		lock.lock();
		try {
			List<LogEvent> list = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				var e = ring[(start + i) % ring.length];
				if (e != null) {
					list.add(e);
				}
			}
			return List.copyOf(list);
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public void clear() {
		lock.lock();
		try {
			Arrays.fill(ring, null);
			start = 0;
			size = 0;
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public Stats stats() {
		lock.lock();
		try {
			return new Stats(total.get(), size, ring.length);
		}
		finally {
			lock.unlock();
		}
	}

}

/**
 * Logging about logging. This is the static, always-available entry point used by code
 * that cannot easily reach a {@link LogConfig} (e.g. before a {@link RainbowGum} is
 * bound), and always reports directly (currently to stderr) rather than forwarding to a
 * bound {@link RainbowGum}'s {@link LogConfig#alerts()} - alerts is a small bounded ring
 * buffer meant to hold a curated, recent view of noteworthy logging-system problems, and
 * {@link MetaLog} is used from enough different low level failure paths that routing it
 * there risked flooding/evicting genuine alerts with whatever volume of things end up
 * calling this class.
 * <p>
 * Components that already have (or can easily capture) a {@link LogConfig} - for example
 * via {@link LogProvider} or {@link LogLifecycle#start(LogConfig)} - should prefer
 * {@link LogConfig#alerts()} directly instead of this class.
 *
 * @author agentgt
 */
@SuppressWarnings("ImmutableEnumChecker")
enum MetaLog implements LogEventLogger {

	INSTANCE;

	static Supplier<? extends @Nullable PrintStream> output = () -> System.err;

	private final LogFormatter formatter = LogFormatter.builder() //
		.text("[")
		.level()
		.text("] - RAINBOW_GUM")
		.text(" - ")
		.add(LogFormatter.of((b, e) -> {
			b.append(gumLoggerName(e.loggerName()));
		}))
		.text(" - ")
		.message()
		.textIfThrowable(" ")
		.throwable()
		.newline()
		.build();

	/**
	 * Reports a logging system event at its original level.
	 * @param event event to log.
	 */
	static void error(LogEvent event) {
		INSTANCE.log(event);
	}

	/**
	 * Logs an error in the logging system.
	 * @param loggerName derived from class.
	 * @param throwable error to log.
	 */
	static void error(Class<?> loggerName, Throwable throwable) {
		String m = Objects.requireNonNullElse(throwable.getMessage(), "exception");
		error(loggerName, m, throwable);
	}

	/**
	 * Logs an error in the logging system.
	 * @param loggerName derived from class.
	 * @param message error message.
	 * @param throwable error to log.
	 */
	static void error(Class<?> loggerName, String message, Throwable throwable) {
		var currentThread = Thread.currentThread();
		var event = LogEvent.of(Instant.now(), currentThread.getName(), currentThread.threadId(), Level.ERROR,
				loggerName.getName(), message, KeyValues.of(), throwable);
		error(event);
	}

	private static String gumLoggerName(String value) {
		String prefix = "io.jstach.rainbowgum.";
		if (value.startsWith(prefix)) {
			value = value.substring(prefix.length());
		}
		return value;
	}

	@Override
	public void log(LogEvent event) {
		var err = output.get();
		if (err != null) {
			var sb = new StringBuilder();
			formatter.format(sb, event);
			err.append(sb);
		}
	}

}
