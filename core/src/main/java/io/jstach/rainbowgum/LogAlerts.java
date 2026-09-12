package io.jstach.rainbowgum;

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

/**
 * Alerts (sometimes called errors or status events elsewhere) are for reporting problems
 * with the logging system itself rather than application logging - for example an
 * appender failing to write, or an async publisher's queue overflowing. Because logging
 * itself may be broken these are not routed through the normal {@link LogRouter} but are
 * instead kept in a small in memory ring buffer (see {@link #dump()} and
 * {@link #stats()}) as well as immediately reported (currently to stderr).
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
public sealed interface LogAlerts extends LogLifecycle permits DefaultLogAlerts {

	/**
	 * Default capacity of the alert ring buffer.
	 */
	static final int DEFAULT_CAPACITY = 128;

	/**
	 * Records an alert.
	 * @param event event describing the alert. {@link Level#ERROR} or higher is expected
	 * but not enforced.
	 */
	public void error(LogEvent event);

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
		var currentThread = Thread.currentThread();
		error(LogEvent.of(Instant.now(), currentThread.getName(), currentThread.threadId(), Level.ERROR,
				loggerName.getName(), message, KeyValues.of(), throwable));
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
	 * than relying on the counter itself being reset.
	 */
	public void clear();

	/**
	 * Stats about the alerts recorded so far.
	 * @return stats.
	 */
	public Stats stats();

	/**
	 * Registers a listener that is notified synchronously, in addition to the alert being
	 * recorded in the ring buffer, every time {@link #error(LogEvent)} is called.
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
	 * What {@link #start(LogConfig)} does if, at that moment, at least one alert has been
	 * recorded and still zero {@link Listener}s are registered - the situation Logback's
	 * {@code StatusManager} calls an unobserved status: nothing is watching, so whatever
	 * went wrong during property loading or a
	 * {@link io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator} would
	 * otherwise only ever have reached the individual, easy to miss stderr lines each
	 * {@link #error(LogEvent)} call already produces.
	 * <p>
	 * Deliberately keyed as
	 * {@value LogProperties#ALERTS_UNOBSERVED_ERRORS_ACTION_PROPERTY} (an enum, not a
	 * boolean) since "fail" without "dump" first would throw away the only diagnostic
	 * evidence of why it failed.
	 */
	enum UnobservedErrorsAction {

		/**
		 * Do nothing beyond what {@link #error(LogEvent)} already does per event.
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
			return UnobservedErrorsAction.valueOf(value.toUpperCase(Locale.ROOT));
		}

	}

}

final class DefaultLogAlerts implements LogAlerts {

	private final LogEvent[] ring;

	private int start = 0;

	private int size = 0;

	private final AtomicLong total = new AtomicLong();

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
	private DefaultLogAlerts(int capacity, UnobservedErrorsAction unobservedErrorsAction) {
		this.ring = new LogEvent[capacity];
		this.unobservedErrorsAction = unobservedErrorsAction;
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
	static LogAlerts of(LogProperties properties) {
		var validator = LogProperty.Validator.of(LogAlerts.class);
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
		return new DefaultLogAlerts(capacityResult.value(), unobservedErrorsActionResult.value());
	}

	private static int requirePositiveCapacity(int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity should be greater than 0");
		}
		return capacity;
	}

	@Override
	public void error(LogEvent event) {
		var frozen = event.freeze();
		total.incrementAndGet();
		lock.lock();
		try {
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
				FailsafeAppender.INSTANCE.log(eventFactory.eventNoArg(Level.ERROR, "LogAlerts.Listener threw", e));
			}
		}
		FailsafeAppender.INSTANCE.log(frozen);
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
		if (unobservedErrorsAction == UnobservedErrorsAction.NONE || hasExternalListener || total.get() == 0) {
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
