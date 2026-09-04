package io.jstach.rainbowgum;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogResponse.Status;
import io.jstach.rainbowgum.annotation.CaseChanging;

/**
 * Appenders are guaranteed to be written synchronously much like an actor in actor
 * concurrency. They safely hold onto and communicate with the encoder and output.
 * Appenders largely deal with correct locking, buffer reuse and flushing.
 * {@linkplain LogAppender.AppenderFlag Flags } can be set to control the behavior the
 * appenders and publishers can request different appender behavior through the flags.
 *
 * @see LogAppender.AppenderFlag
 * @apiNote because appenders require complicated implementation and to guarantee
 * integrity the implementations are encapsulated (sealed).
 */
public sealed interface LogAppender extends LogLifecycle, LogEventConsumer {

	/**
	 * Default Console appender name.
	 */
	static final String CONSOLE_APPENDER_NAME = "console";

	/**
	 * Default output file appender name.
	 */
	static final String FILE_APPENDER_NAME = "file";

	/**
	 * Output appender property.
	 */
	static final String APPENDER_OUTPUT_PROPERTY = LogProperties.APPENDER_OUTPUT_PROPERTY;

	/**
	 * Encoder appender property.
	 */
	static final String APPENDER_ENCODER_PROPERTY = LogProperties.APPENDER_ENCODER_PROPERTY;

	/**
	 * Appender flags. A list of flags (usually comma separated).
	 * @see AppenderFlag
	 */
	static final String APPENDER_FLAGS_PROPERTY = LogProperties.APPENDER_FLAGS_PROPERTY;

	/**
	 * Batch of events. <strong>DO NOT MODIFY THE ARRAY</strong>. Do not use the
	 * <code>length</code> of the passed in array but instead use <code>count</code>
	 * parameter.
	 * @param events an array guaranteed to be smaller than count.
	 * @param count the number of items.
	 */
	public void append(LogEvent[] events, int count);

	@Override
	public void append(LogEvent event);

	/**
	 * Boolean like flags for appender that can be set with
	 * {@link LogAppender#APPENDER_FLAGS_PROPERTY}. Publisher may choose to add flags to
	 * the appenders and will be added if no flags are set on the appenders. Consequently
	 * great care should be taken when setting flags as performance maybe greatly impacted
	 * if a publisher is not designed for the flag.
	 */
	@CaseChanging
	public enum AppenderFlag {

		/**
		 * The appender will create a single buffer that will be reused and will be
		 * protected by the appenders locking.
		 */
		REUSE_BUFFER,
		/**
		 * The appender will give each thread its own reusable buffer (a
		 * {@link ThreadLocal}) instead of allocating a new buffer per event. Unlike
		 * {@link #REUSE_BUFFER} the encoding is done <strong>outside</strong> the
		 * appender's lock (the thread's buffer is only visited by that thread so no
		 * protection is needed while encoding) with the lock only held for the final
		 * write to the output, which is the same trade-off {@link #REUSE_BUFFER} makes
		 * except without serializing the encoding step itself.
		 * <p>
		 * This flag is ignored if {@link #REUSE_BUFFER} is also set.
		 * <p>
		 * The same {@link ThreadLocal} buffer is used regardless of whether the calling
		 * thread is a platform or virtual thread. A virtual thread's entry becomes
		 * collectible once the thread itself terminates, and a typical unit of work (e.g.
		 * one HTTP request) logs several times on the same thread, so reusing the buffer
		 * across those calls still pays off even for short-lived virtual threads.
		 * <p>
		 * This is the strategy an appender uses <strong>even when no flag is explicitly
		 * set</strong> - see {@code DirectLogAppender#defaultAppender} for the default
		 * selection, and {@link #SYNCHRONIZED_THREAD_LOCAL_BUFFER} for the alternative
		 * lock-kind opt-in.
		 */
		LOCK_THREAD_LOCAL_BUFFER,
		/**
		 * Like {@link #LOCK_THREAD_LOCAL_BUFFER} (a reused per-thread buffer, encoding
		 * done outside any lock) except the final write to the output is protected by a
		 * plain {@code synchronized} block (the JVM's intrinsic monitor) instead of a
		 * {@link ReentrantLock}.
		 * <p>
		 * The Java language has no way to acquire a monitor in one method call and
		 * release it in another, so this appender's critical sections are written as
		 * literal {@code synchronized} blocks rather than going through a shared lock
		 * abstraction the way every other flag combination does. {@link #REENTRY_DROP}
		 * and {@link #REENTRY_LOG} are still honored though -
		 * {@link Thread#holdsLock(Object)} is the {@code synchronized} equivalent of
		 * {@code ReentrantLock}'s {@code isHeldByCurrentThread()}, so reentrancy is
		 * detected the same way.
		 * <p>
		 * Motivated by Log4j2's own garbage-free appenders using {@code synchronized}
		 * rather than a {@code java.util.concurrent} lock around their buffer-transfer
		 * step, and confirmed by real-workload benchmarking to outperform
		 * {@link ReentrantLock} under platform-thread contention - this was the default
		 * for a time. Real-workload benchmarking under virtual threads found the
		 * opposite, a large and reproducible loss versus {@link ReentrantLock} for
		 * reasons not fully understood (classic JEP 491 pinning was checked and ruled
		 * out), so {@link #LOCK_THREAD_LOCAL_BUFFER} is the default now and this flag is
		 * an opt-in for platform-thread-heavy deployments that want the edge. Takes
		 * precedence over {@link #LOCK_THREAD_LOCAL_BUFFER} (redundant if both are set)
		 * but not {@link #REUSE_BUFFER}.
		 * <p>
		 * <strong>Explicitly setting this flag honors it</strong> even on a JDK where
		 * {@code synchronized} still pins the carrier platform thread when called from a
		 * virtual thread (before <a href="https://openjdk.org/jeps/491">JEP 491</a>,
		 * finalized in JDK 24) - the one exception is
		 * {@code LogProperties#GLOBAL_APPENDER_REENTRANT_LOCK_PROPERTY}: when that global
		 * property is active it downgrades even an explicit request for this flag to
		 * {@link #LOCK_THREAD_LOCAL_BUFFER}, since its whole point is a hard guarantee
		 * independent of anything else in the configuration.
		 */
		SYNCHRONIZED_THREAD_LOCAL_BUFFER,
		/**
		 * By default the appender will call flush on each item appended or if in async
		 * batch mode for each batch. This flag disables that behavior so that flushing is
		 * left up to the output (or an external mechanism) instead.
		 */
		DISABLE_IMMEDIATE_FLUSH,
		/**
		 * The appender will drop events on reentry which happens if an appender during
		 * its append causes recursive appending in the same thread. This is an analog to
		 * what
		 * <a href="https://logback.qos.ch/manual/appenders.html#AppenderBase">Logback
		 * does by default</a>. Note that this is done using {@link ReentrantLock} and not
		 * ThreadLocal like logback <strong>and is not done by default hence the
		 * flag!</strong>
		 * <p>
		 * This flag is to allow outputs that do logging themselves. For performance
		 * reasons and to allow async publishers it is recommended that you fix the output
		 * code such that it does not do logging. This flag is ignored if
		 * {@link #REENTRY_LOG} is set.
		 * <p>
		 * <strong>This flag will not fix outputs causing lool like logging if an async
		 * publisher is used!</strong> That is why it is recommended you fix the output by
		 * dropping events that would cause infinite loop like logging.
		 * @see #REENTRY_LOG
		 */
		REENTRY_DROP,
		/**
		 * The appender will log events as errors to std error on reentry which happens if
		 * an appender during its append causes recursive appending in the same thread.
		 * This is an analog to what
		 * <a href="https://logback.qos.ch/manual/appenders.html#AppenderBase">Logback
		 * does by default</a>. Note that this is done using {@link ReentrantLock} and not
		 * ThreadLocal like logback <strong>and is not done by default hence the
		 * flag!</strong> This flag is to resolve failures of outputs that then do
		 * logging.
		 * <p>
		 * This flag takes precedence over {@link #REENTRY_DROP}.
		 */
		REENTRY_LOG;

		static Set<AppenderFlag> parse(Collection<String> value) {
			if (value.isEmpty()) {
				return EnumSet.noneOf(AppenderFlag.class);
			}
			var s = EnumSet.noneOf(AppenderFlag.class);
			for (var v : value) {
				s.add(parse(v));
			}
			return s;
		}

		static AppenderFlag parse(String value) {
			String v = value.toUpperCase(Locale.ROOT);
			return AppenderFlag.valueOf(v);
		}

	}

	/**
	 * Creates a builder.
	 * @param name appender name.
	 * @return builder.
	 */
	public static Builder builder(String name) {
		return new Builder(name);
	}

	/**
	 * Builder for creating standard appenders.
	 * <p>
	 * If the output is not set standard out will be used. If the encoder is not set a
	 * default encoder will be resolved from the output.
	 */
	public static final class Builder {

		private @Nullable LogProvider<? extends LogOutput> output = null;

		private @Nullable LogProvider<? extends LogEncoder> encoder = null;

		private @Nullable EnumSet<AppenderFlag> flags = null;

		private final String name;

		private Builder(String name) {
			this.name = name;
		}

		/**
		 * Name of the appender.
		 * @return name.
		 */
		public String name() {
			return this.name;
		}

		/**
		 * Sets output.
		 * @param output output.
		 * @return builder.
		 */
		public Builder output(LogProvider<? extends LogOutput> output) {
			this.output = output;
			return this;
		}

		/**
		 * Sets output.
		 * @param output output.
		 * @return builder.
		 */
		public Builder output(LogOutput output) {
			this.output = LogProvider.of(output);
			return this;
		}

		/**
		 * Sets formatter as encoder.
		 * @param formatter formatter to be converted to encoder.
		 * @return builder.
		 * @see LogEncoder#of(LogFormatter)
		 */
		public Builder formatter(LogFormatter formatter) {
			this.encoder = LogEncoder.of(formatter);
			return this;
		}

		/**
		 * Sets formatter as encoder.
		 * @param formatter formatter to be converted to encoder.
		 * @return builder.
		 * @see LogEncoder#of(LogFormatter)
		 */
		public Builder formatter(LogFormatter.EventFormatter formatter) {
			this.encoder = LogEncoder.of(formatter);
			return this;
		}

		/**
		 * Sets encoder.
		 * @param encoder encoder not <code>null</code>.
		 * @return builder.
		 */
		public Builder encoder(LogProvider<? extends LogEncoder> encoder) {
			this.encoder = encoder;
			return this;
		}

		/**
		 * Sets encoder.
		 * @param encoder encoder not <code>null</code>.
		 * @return builder.
		 */
		public Builder encoder(LogEncoder encoder) {
			this.encoder = LogProvider.of(encoder);
			return this;
		}

		/**
		 * Sets appender flags.
		 * @param flags flags will replace all flags currently set.
		 * @return this.
		 */
		public Builder flags(Collection<AppenderFlag> flags) {
			_flags().addAll(flags);
			return this;
		}

		private EnumSet<AppenderFlag> _flags() {
			EnumSet<AppenderFlag> flags = this.flags;
			if (flags == null) {
				this.flags = flags = EnumSet.noneOf(AppenderFlag.class);
			}
			return flags;
		}

		/**
		 * Adds a flag.
		 * @param flag flag.
		 * @return this.
		 */
		public Builder flag(AppenderFlag flag) {
			_flags().add(flag);
			return this;
		}

		/**
		 * Builds.
		 * @return an appender factory.
		 */
		public LogProvider<LogAppender> build() {
			/*
			 * We need to capture parameters since appender creation needs to be lazy.
			 */
			var _name = name;
			var _output = output;
			var _encoder = encoder;
			var _flags = flags;
			/*
			 * TODO should we use the parent name for resolution?
			 */
			return (n, config) -> {
				AppenderConfig a = new AppenderConfig(_name, LogProvider.provideOrNull(_output, _name, config),
						LogProvider.provideOrNull(_encoder, _name, config), _flags);
				return DefaultAppenderRegistry.appender(a, config);
			};
		}

	}

	/**
	 * Provides appenders safely to the publisher. The providing calls of
	 * <code>asXXX</code> can only be called once as they register the appenders.
	 */
	class Appenders {

		private final AtomicBoolean created = new AtomicBoolean();

		private final String name;

		private final LogConfig config;

		private final List<LogProvider<LogAppender>> appenders;

		private Set<LogAppender.AppenderFlag> flags = EnumSet.noneOf(LogAppender.AppenderFlag.class);

		Appenders(String name, LogConfig config, List<LogProvider<LogAppender>> appenders) {
			super();
			this.name = name;
			this.config = config;
			this.appenders = appenders;
		}

		/**
		 * Sets flags for the appenders which should be done prior to <code>asXXX</code>.
		 * @param flags appender flags.
		 * @return this;
		 */
		public Appenders flags(Set<LogAppender.AppenderFlag> flags) {
			this.flags = flags;
			return this;
		}

		/**
		 * Return the appenders as a list.
		 * @return list of appenders.
		 * @throws IllegalStateException if appenders are already registered.
		 */
		public List<? extends LogAppender> asList() throws IllegalStateException {
			if (created.compareAndSet(false, true)) {
				var apps = appenders();
				List<LogAppender> appenders = new ArrayList<>();
				for (var a : apps) {
					appenders.add(register(a));
				}
				return appenders;
			}
			else {
				throw new IllegalStateException("Appenders already provided.");
			}

		}

		/**
		 * Consolidate the appenders as a single appender, appended synchronously. If more
		 * than one appender is combined, each keeps its own independent lock and is
		 * appended to directly - see {@link CompositeLogAppender}.
		 * @return single appender.
		 * @throws IllegalStateException if appenders are already registered.
		 */
		public LogAppender asSingle() throws IllegalStateException {
			if (created.compareAndSet(false, true)) {
				var apps = appenders();
				var appender = composite(apps);
				return register(appender);
			}
			else {
				throw new IllegalStateException("Appenders already provided.");
			}
		}

		private LogAppender register(LogAppender appender) {
			return switch (appender) {
				case DirectLogAppender ia -> {
					var _a = ia.withFlags(flags);
					config.serviceRegistry().put(LogAppender.class, name + "." + _a.name(), _a);
					yield _a;
				}
				case CompositeLogAppender ca -> {
					var _a = ca.withFlags(flags);
					config.serviceRegistry().put(LogAppender.class, name, _a);
					yield _a;
				}
				default -> {
					throw new IllegalStateException();
				}
			};
		}

		private List<LogAppender> appenders() {
			return LogProvider.flatten(appenders)
				.describe(n -> "Appenders for route: '" + n + "'")
				.provide(name, config);
		}

		/**
		 * Creates a composite log appender from many, each keeping its own independent
		 * lock.
		 * @param appenders appenders.
		 * @return appender.
		 */
		private static LogAppender composite(List<? extends LogAppender> appenders) {
			if (appenders.isEmpty()) {
				throw new IllegalArgumentException("A single appender is required");
			}
			if (appenders.size() == 1) {
				return Objects.requireNonNull(appenders.get(0));
			}
			return CompositeLogAppender.of(appenders, Set.of());
		}

	}

	@Override
	public void close();

}

interface AppenderVisitor {

	boolean consume(DirectLogAppender appender);

}

/**
 * This is a JAVADOC BUG
 */
sealed interface InternalLogAppender extends LogAppender, Actor {

	static InternalLogAppender of(LogAppender appender) {
		return Objects.requireNonNull((InternalLogAppender) appender); // TODO eclipse
																		// bug.
	}

	/**
	 * An appender can act on actions. One of the key actions is reopening files.
	 * @param action action to run.
	 * @return responses.
	 */
	@Override
	public List<LogResponse> act(LogAction action);

}

sealed interface DirectLogAppender extends InternalLogAppender {

	String name();

	LogOutput output();

	LogEncoder encoder();

	default List<LogResponse> _request(LogAction action) {
		List<LogResponse> r = switch (action) {
			case LogAction.StandardAction a -> switch (a) {
				case LogAction.StandardAction.REOPEN -> List.of(reopen());
				case LogAction.StandardAction.FLUSH -> List.of(flush());
			};
		};
		return r;
	}

	default LogResponse reopen() {
		var status = output().reopen();
		return new Response(LogOutput.class, name(), status);
	}

	default LogResponse flush() {
		output().flush();
		return new Response(LogOutput.class, name(), LogResponse.Status.StandardStatus.OK);
	}

	static DirectLogAppender of(String name, LogOutput output, LogEncoder encoder, Set<LogAppender.AppenderFlag> flags,
			LogAlerts alerts, LogMetrics metrics) {
		flags = AbstractLogAppender.guardSynchronizedFlag(flags);
		if (flags.contains(AppenderFlag.REUSE_BUFFER)) {
			return new ReuseBufferLogAppender(name, output, encoder, flags, new ReentrantLock(), alerts, metrics);
		}
		if (flags.contains(AppenderFlag.SYNCHRONIZED_THREAD_LOCAL_BUFFER)) {
			return new SynchronizedThreadLocalBufferLogAppender(name, output, encoder, flags, alerts, metrics);
		}
		if (flags.contains(AppenderFlag.LOCK_THREAD_LOCAL_BUFFER)) {
			return new LockThreadLocalBufferLogAppender(name, output, encoder, flags, new ReentrantLock(), alerts,
					metrics);
		}
		return defaultAppender(name, output, encoder, flags, alerts, metrics);
	}

	/**
	 * Picks the appender used when no {@link AppenderFlag} explicitly requests a
	 * buffer/lock strategy: {@link AppenderFlag#LOCK_THREAD_LOCAL_BUFFER}, the same
	 * appender a caller gets from setting that flag explicitly. Supports
	 * {@link AppenderFlag#REENTRY_DROP}/{@link AppenderFlag#REENTRY_LOG} directly (see
	 * {@link AbstractLogAppender#shouldDropForReentry}), so no fallback to a third
	 * appender is needed here for those flags.
	 * <p>
	 * {@link AppenderFlag#SYNCHRONIZED_THREAD_LOCAL_BUFFER} measured faster under
	 * platform-thread contention in real-workload benchmarking and was the default for a
	 * time, but real-workload benchmarking under virtual threads found the opposite - a
	 * large, reproducible win for {@code LOCK_THREAD_LOCAL_BUFFER} there, for reasons not
	 * fully understood (checked and ruled out classic JEP 491 pinning as the cause).
	 * Given RainbowGum's own audience skews toward newer JDKs and virtual-thread
	 * workloads, {@code LOCK_THREAD_LOCAL_BUFFER} is the safer default;
	 * {@code synchronized} remains available as an explicit opt-in for
	 * platform-thread-heavy deployments that want that edge.
	 */
	static DirectLogAppender defaultAppender(String name, LogOutput output, LogEncoder encoder,
			Set<LogAppender.AppenderFlag> flags, LogAlerts alerts, LogMetrics metrics) {
		return new LockThreadLocalBufferLogAppender(name, output, encoder, flags, new ReentrantLock(), alerts, metrics);
	}

	// @Override
	DirectLogAppender withFlags(Set<LogAppender.AppenderFlag> flags);

}

/**
 * An abstract appender to help create custom appenders.
 */
sealed abstract class AbstractLogAppender implements DirectLogAppender {

	/*
	 * Set once from LogProperties#GLOBAL_APPENDER_REENTRANT_LOCK_PROPERTY during
	 * LogConfig construction (see DefaultLogConfig) - a global, process-wide guarantee
	 * that no appender will ever use `synchronized`, for deployments that want that
	 * guaranteed even when something explicitly requests
	 * SYNCHRONIZED_THREAD_LOCAL_BUFFER. Global (not per-route/per-appender) by design,
	 * matching the property's own scope.
	 */
	static volatile boolean forceReentrantLockAppenders = false;

	/**
	 * Downgrades an explicit
	 * {@link LogAppender.AppenderFlag#SYNCHRONIZED_THREAD_LOCAL_BUFFER} to
	 * {@link LogAppender.AppenderFlag#LOCK_THREAD_LOCAL_BUFFER} if
	 * {@link #forceReentrantLockAppenders} is active - the enforcement point that makes
	 * the global no-synchronized guarantee a real guarantee rather than just a changed
	 * default, since an explicit flag would otherwise bypass
	 * {@link DirectLogAppender#defaultAppender} entirely.
	 * @param flags flags as given to an appender factory method.
	 * @return {@code flags} unchanged, unless the guarantee is active and
	 * {@code SYNCHRONIZED_THREAD_LOCAL_BUFFER} was requested, in which case a copy with
	 * that flag replaced by {@code LOCK_THREAD_LOCAL_BUFFER}.
	 */
	static Set<LogAppender.AppenderFlag> guardSynchronizedFlag(Set<LogAppender.AppenderFlag> flags) {
		if (!forceReentrantLockAppenders
				|| !flags.contains(LogAppender.AppenderFlag.SYNCHRONIZED_THREAD_LOCAL_BUFFER)) {
			return flags;
		}
		var copy = EnumSet.copyOf(flags);
		copy.remove(LogAppender.AppenderFlag.SYNCHRONIZED_THREAD_LOCAL_BUFFER);
		copy.add(LogAppender.AppenderFlag.LOCK_THREAD_LOCAL_BUFFER);
		return copy;
	}

	/**
	 * Whether an appender should drop (or drop-and-log) an append call because it is
	 * reentrant - i.e. the current thread is already inside a previous call to the same
	 * appender's write path, which happens if an output does logging itself during its
	 * own write. Shared by every appender that can detect reentrancy, regardless of
	 * whether it does so via a {@link ReentrantLock} (
	 * {@code lock.isHeldByCurrentThread()}) or a {@code synchronized} block (
	 * {@link Thread#holdsLock(Object)}) - callers pass in whichever check applies to
	 * them. Whenever this returns {@code true}, {@link LogMetrics#EVENTS_DROPPED_METRIC}
	 * is incremented by {@code count} regardless of whether
	 * {@link AppenderFlag#REENTRY_LOG} is set - counting and logging/alerting are
	 * separate concerns, so the count still happens even when the drop itself is silent.
	 * @param reentrant whether the current thread already holds this appender's
	 * lock/monitor.
	 * @param flags the appender's flags.
	 * @param metrics where to record {@link LogMetrics#EVENTS_DROPPED_METRIC} if events
	 * are dropped.
	 * @param count number of events that would be dropped - {@code 1} for a single event
	 * append, or the batch size for a batch append.
	 * @return {@code true} if the caller should drop the event(s) without appending.
	 */
	static boolean shouldDropForReentry(boolean reentrant, Set<LogAppender.AppenderFlag> flags, LogMetrics metrics,
			int count) {
		if (!reentrant) {
			return false;
		}
		if (flags.contains(LogAppender.AppenderFlag.REENTRY_LOG)) {
			Exception exception = new Exception("reentrant appender");
			MetaLog.error(LogAppender.class, exception);
			metrics.errorCounter(LogMetrics.EVENTS_DROPPED_METRIC, count);
			return true;
		}
		if (flags.contains(LogAppender.AppenderFlag.REENTRY_DROP)) {
			metrics.errorCounter(LogMetrics.EVENTS_DROPPED_METRIC, count);
			return true;
		}
		return false;
	}

	/**
	 * name.
	 */
	protected final String name;

	/**
	 * output
	 */
	protected final LogOutput output;

	/**
	 * encoder
	 */
	protected final LogEncoder encoder;

	protected final Set<LogAppender.AppenderFlag> flags;

	protected final boolean immediateFlush;

	/**
	 * alerts for reporting encode/write failures that this appender catches so they never
	 * propagate back to whatever application thread called logger.info(...).
	 */
	protected final LogAlerts alerts;

	/**
	 * metrics for recording counters like {@link LogMetrics#EVENTS_DROPPED_METRIC}.
	 */
	protected final LogMetrics metrics;

	/**
	 * Creates an appender from an output and encoder.
	 * @param output set the output field and will be started and closed with the
	 * appender.
	 * @param encoder set the encoder field.
	 * @param alerts alerts for reporting encode/write failures.
	 * @param metrics metrics for recording counters.
	 */
	protected AbstractLogAppender(String name, LogOutput output, LogEncoder encoder,
			Set<LogAppender.AppenderFlag> flags, LogAlerts alerts, LogMetrics metrics) {
		super();
		this.name = name;
		this.output = output;
		this.encoder = encoder;
		this.flags = flags;
		this.immediateFlush = !flags.contains(LogAppender.AppenderFlag.DISABLE_IMMEDIATE_FLUSH);
		this.alerts = alerts;
		this.metrics = metrics;
	}

	@Override
	public void start(LogConfig config) {
		output.start(config);
	}

	@Override
	public void close() {
		output.close();
	}

	@Override
	public String toString() {
		return getClass().getName() + "[name=" + name + " encoder=" + encoder + ", " + "output=" + output + ", flags="
				+ flags + "]";
	}

	@Override
	public String name() {
		return this.name;
	}

	@Override
	public LogOutput output() {
		return this.output;
	}

	@Override
	public LogEncoder encoder() {
		return this.encoder;
	}

}

/**
 * Combines more than one appender on a route into one {@link LogAppender}. Each appender
 * keeps the independent lock it was already constructed with, and
 * {@link #append(LogEvent)}/{@link #append(LogEvent[], int)} skip locking at the
 * composite level entirely and append to every component directly - so e.g. a console
 * appender and a file appender under the same route never contend on the same lock for
 * unrelated I/O. {@link #start(LogConfig)}/{@link #close()}/{@link #act(LogAction)} do
 * the same - there is no composite-owned mutable state to protect, only a loop over
 * components that already handle their own synchronization where it matters.
 */
@SuppressWarnings("ArrayRecordComponent")
record CompositeLogAppender(DirectLogAppender[] appenders) implements InternalLogAppender {

	public static CompositeLogAppender of(List<? extends LogAppender> appenders, Set<LogAppender.AppenderFlag> flags) {
		@SuppressWarnings("null") // TODO Eclipse issue here
		DirectLogAppender @NonNull [] array = appenders.stream()
			.map(CompositeLogAppender::cast)
			.map(a -> a.withFlags(flags))
			.toArray(i -> new DirectLogAppender[i]);
		return new CompositeLogAppender(array);
	}

	private static DirectLogAppender cast(LogAppender appender) {
		return (DirectLogAppender) appender;
	}

	@Override
	public void append(LogEvent event) {
		for (var appender : appenders) {
			appender.append(event);
		}
	}

	@Override
	public void append(LogEvent[] event, int count) {
		for (var appender : appenders) {
			appender.append(event, count);
		}
	}

	@Override
	public void close() {
		for (var appender : appenders) {
			appender.close();
		}
	}

	@Override
	public void start(LogConfig config) {
		for (var appender : appenders) {
			appender.start(config);
		}
	}

	@Override
	public List<LogResponse> act(LogAction action) {
		return Actor.act(appenders, action);
	}

	public CompositeLogAppender withFlags(Set<LogAppender.AppenderFlag> flags) {
		if (flags.isEmpty()) {
			return this;
		}
		return of(List.of(appenders), flags);
	}

	@Override
	public String toString() {
		return getClass().getName() + "[appenders=" + Arrays.toString(appenders) + "]";
	}

}

sealed abstract class LockLogAppender extends AbstractLogAppender implements InternalLogAppender {

	protected final ReentrantLock lock;

	public LockLogAppender(String name, LogOutput output, LogEncoder encoder, Set<LogAppender.AppenderFlag> flags,
			ReentrantLock lock, LogAlerts alerts, LogMetrics metrics) {
		super(name, output, encoder, flags, alerts, metrics);
		this.lock = lock;
	}

	@Override
	public List<LogResponse> act(LogAction action) {
		lock.lock();
		try {
			return _request(action);
		}
		catch (UncheckedIOException ioe) {
			return List.of(new Response(LogOutput.class, name, Status.ErrorStatus.of(ioe)));
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public void close() {
		lock.lock();
		try {
			super.close();
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public DirectLogAppender withFlags(Set<LogAppender.AppenderFlag> flags) {
		if (flags.isEmpty()) {
			return this;
		}
		if (this.flags.containsAll(flags)) {
			return this;
		}
		flags = EnumSet.copyOf(flags);
		flags.addAll(this.flags);
		flags = guardSynchronizedFlag(flags);
		if (flags.contains(LogAppender.AppenderFlag.REUSE_BUFFER)) {
			return new ReuseBufferLogAppender(name, output, encoder, flags, lock, alerts, metrics);
		}
		if (flags.contains(LogAppender.AppenderFlag.SYNCHRONIZED_THREAD_LOCAL_BUFFER)) {
			return new SynchronizedThreadLocalBufferLogAppender(name, output, encoder, flags, alerts, metrics);
		}
		if (flags.contains(LogAppender.AppenderFlag.LOCK_THREAD_LOCAL_BUFFER)) {
			return new LockThreadLocalBufferLogAppender(name, output, encoder, flags, lock, alerts, metrics);
		}
		return DirectLogAppender.defaultAppender(name, output, encoder, flags, alerts, metrics);
	}

}

/*
 * The idea here is to reuse the buffer trading lock contention for less GC.
 */
final class ReuseBufferLogAppender extends LockLogAppender implements InternalLogAppender {

	private final LogEncoder.Buffer buffer;

	ReuseBufferLogAppender(String name, LogOutput output, LogEncoder encoder, Set<LogAppender.AppenderFlag> flags,
			ReentrantLock lock, LogAlerts alerts, LogMetrics metrics) {
		super(name, output, encoder, flags, lock, alerts, metrics);
		this.buffer = encoder.buffer(output.bufferHints());
	}

	@Override
	public final void append(LogEvent event) {
		if (shouldDropForReentry(lock.isHeldByCurrentThread(), flags, metrics, 1)) {
			return;
		}
		try {
			lock.lock();
			try {
				buffer.clear();
				encoder.encode(event, buffer);
				output.write(event, buffer);
				if (immediateFlush) {
					output.flush();
				}
			}
			finally {
				lock.unlock();
			}
		}
		catch (Exception e) {
			alerts.error(getClass(), "appender '" + name + "' failed to append event", e);
		}
	}

	@Override
	public void append(LogEvent[] events, int count) {
		if (shouldDropForReentry(lock.isHeldByCurrentThread(), flags, metrics, count)) {
			return;
		}
		try {
			lock.lock();
			try {
				output.write(events, count, encoder, buffer);
				if (immediateFlush) {
					output.flush();
				}
			}
			finally {
				lock.unlock();
			}
		}
		catch (Exception e) {
			alerts.error(getClass(), "appender '" + name + "' failed to append batch of " + count + " event(s)", e);
		}
	}

	@Override
	public void close() {
		lock.lock();
		try {
			super.close();
			buffer.close();
		}
		finally {
			lock.unlock();
		}
	}

}

/*
 * The idea here is to encode outside the lock. Instead of allocating a fresh buffer per
 * event or sharing (and thus serializing access to) a single buffer
 * (ReuseBufferLogAppender), each thread gets its own buffer that only it will ever touch,
 * so encoding never needs to be guarded by the lock at all - only the final write to the
 * output does.
 */
final class LockThreadLocalBufferLogAppender extends LockLogAppender implements InternalLogAppender {

	/*
	 * There is no way to enumerate every thread's buffer to close it on appender close so
	 * we rely on Buffer implementations not holding onto real resources (today they are
	 * all just wrapped in-memory builders) and let the ThreadLocal itself (and
	 * consequently the per-thread entries) become collectible once this appender is
	 * discarded.
	 */
	// CheckerFramework's ThreadLocal stub declares T as inherently @Nullable since get()
	// can return null before initialValue() runs, but withInitial(...) below guarantees
	// it never does here.
	@SuppressWarnings("nullness:type.argument")
	private final ThreadLocal<LogEncoder.Buffer> bufferThreadLocal;

	LockThreadLocalBufferLogAppender(String name, LogOutput output, LogEncoder encoder,
			Set<LogAppender.AppenderFlag> flags, ReentrantLock lock, LogAlerts alerts, LogMetrics metrics) {
		super(name, output, encoder, flags, lock, alerts, metrics);
		this.bufferThreadLocal = ThreadLocal.withInitial(() -> encoder.buffer(output.bufferHints()));
	}

	@Override
	public final void append(LogEvent event) {
		try {
			var buffer = bufferThreadLocal.get();
			buffer.clear();
			encoder.encode(event, buffer);
			writeLocked(event, buffer);
		}
		catch (Exception e) {
			alerts.error(getClass(), "appender '" + name + "' failed to append event", e);
		}
	}

	private void writeLocked(LogEvent event, LogEncoder.Buffer buffer) {
		if (shouldDropForReentry(lock.isHeldByCurrentThread(), flags, metrics, 1)) {
			return;
		}
		lock.lock();
		try {
			output.write(event, buffer);
			if (immediateFlush) {
				output.flush();
			}
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public void append(LogEvent[] events, int count) {
		if (shouldDropForReentry(lock.isHeldByCurrentThread(), flags, metrics, count)) {
			return;
		}
		try {
			lock.lock();
			try {
				output.write(events, count, encoder, bufferThreadLocal.get());
				if (immediateFlush) {
					output.flush();
				}
			}
			finally {
				lock.unlock();
			}
		}
		catch (Exception e) {
			alerts.error(getClass(), "appender '" + name + "' failed to append batch of " + count + " event(s)", e);
		}
	}

}

/*
 * Like LockThreadLocalBufferLogAppender (per-thread reused buffer, encode outside any
 * lock) but the final write is protected by a plain `synchronized` block on this
 * appender's own monitor instead of a ReentrantLock. Does not extend LockLogAppender -
 * there is no way to acquire a monitor in one method call and release it in another, so
 * this appender's critical sections are written as literal synchronized blocks instead.
 */
final class SynchronizedThreadLocalBufferLogAppender extends AbstractLogAppender implements InternalLogAppender {

	private final Object monitor = new Object();

	// See LockThreadLocalBufferLogAppender's identical field for why this suppression is
	// needed.
	@SuppressWarnings("nullness:type.argument")
	private final ThreadLocal<LogEncoder.Buffer> bufferThreadLocal;

	SynchronizedThreadLocalBufferLogAppender(String name, LogOutput output, LogEncoder encoder,
			Set<LogAppender.AppenderFlag> flags, LogAlerts alerts, LogMetrics metrics) {
		super(name, output, encoder, flags, alerts, metrics);
		this.bufferThreadLocal = ThreadLocal.withInitial(() -> encoder.buffer(output.bufferHints()));
	}

	@Override
	public void append(LogEvent event) {
		try {
			var buffer = bufferThreadLocal.get();
			buffer.clear();
			encoder.encode(event, buffer);
			writeSynchronized(event, buffer);
		}
		catch (Exception e) {
			alerts.error(getClass(), "appender '" + name + "' failed to append event", e);
		}
	}

	private void writeSynchronized(LogEvent event, LogEncoder.Buffer buffer) {
		if (shouldDropForReentry(Thread.holdsLock(monitor), flags, metrics, 1)) {
			return;
		}
		synchronized (monitor) {
			output.write(event, buffer);
			if (immediateFlush) {
				output.flush();
			}
		}
	}

	@Override
	public void append(LogEvent[] events, int count) {
		if (shouldDropForReentry(Thread.holdsLock(monitor), flags, metrics, count)) {
			return;
		}
		try {
			synchronized (monitor) {
				output.write(events, count, encoder, bufferThreadLocal.get());
				if (immediateFlush) {
					output.flush();
				}
			}
		}
		catch (Exception e) {
			alerts.error(getClass(), "appender '" + name + "' failed to append batch of " + count + " event(s)", e);
		}
	}

	@Override
	public void close() {
		synchronized (monitor) {
			super.close();
		}
	}

	@Override
	public List<LogResponse> act(LogAction action) {
		synchronized (monitor) {
			try {
				return _request(action);
			}
			catch (UncheckedIOException ioe) {
				return List.of(new Response(LogOutput.class, name, Status.ErrorStatus.of(ioe)));
			}
		}
	}

	@Override
	public DirectLogAppender withFlags(Set<LogAppender.AppenderFlag> flags) {
		if (flags.isEmpty()) {
			return this;
		}
		if (this.flags.containsAll(flags)) {
			return this;
		}
		flags = EnumSet.copyOf(flags);
		flags.addAll(this.flags);
		flags = guardSynchronizedFlag(flags);
		if (flags.contains(LogAppender.AppenderFlag.REUSE_BUFFER)) {
			return new ReuseBufferLogAppender(name, output, encoder, flags, new ReentrantLock(), alerts, metrics);
		}
		if (flags.contains(LogAppender.AppenderFlag.SYNCHRONIZED_THREAD_LOCAL_BUFFER)) {
			return new SynchronizedThreadLocalBufferLogAppender(name, output, encoder, flags, alerts, metrics);
		}
		if (flags.contains(LogAppender.AppenderFlag.LOCK_THREAD_LOCAL_BUFFER)) {
			return new LockThreadLocalBufferLogAppender(name, output, encoder, flags, new ReentrantLock(), alerts,
					metrics);
		}
		return DirectLogAppender.defaultAppender(name, output, encoder, flags, alerts, metrics);
	}

}
