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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
	 * Appender type.
	 * @see AppenderType
	 */
	static final String APPENDER_TYPE_PROPERTY = LogProperties.APPENDER_TYPE_PROPERTY;

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
	 * {@link LogAppender#APPENDER_FLAGS_PROPERTY}. Unlike {@link AppenderType} more than
	 * one flag can be set at once.
	 */
	@CaseChanging
	public enum AppenderFlag {

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
	 * The buffer/locking strategy an appender uses. Unlike {@link AppenderFlag} exactly
	 * one is in effect for a given appender - set on the {@link LogAppender.Builder} (or
	 * via {@link LogAppender#APPENDER_TYPE_PROPERTY}) at construction time and fixed for
	 * the appender's lifetime; a publisher cannot change it afterward.
	 */
	@CaseChanging
	public enum AppenderType {

		/**
		 * The appender will create a single buffer that will be reused and will be
		 * protected by the appenders locking.
		 */
		REUSE_BUFFER,
		/**
		 * The appender will give each thread its own reusable buffer (a
		 * {@link ThreadLocal}) instead of allocating a new buffer per event. Encoding is
		 * done <strong>outside</strong> the appender's lock (the thread's buffer is only
		 * visited by that thread so no protection is needed while encoding) with the lock
		 * only held for the final write to the output.
		 * <p>
		 * The same {@link ThreadLocal} buffer is used regardless of whether the calling
		 * thread is a platform or virtual thread. A virtual thread's entry becomes
		 * collectible once the thread itself terminates, and a typical unit of work (e.g.
		 * one HTTP request) logs several times on the same thread, so reusing the buffer
		 * across those calls still pays off even for short-lived virtual threads.
		 * <p>
		 * This is the default type - see {@link #SYNCHRONIZED_THREAD_LOCAL_BUFFER} for
		 * the alternative lock-kind opt-in.
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
		 * abstraction the way {@link #LOCK_THREAD_LOCAL_BUFFER} does.
		 * {@link AppenderFlag#REENTRY_DROP} and {@link AppenderFlag#REENTRY_LOG} are
		 * still honored though - {@link Thread#holdsLock(Object)} is the
		 * {@code synchronized} equivalent of {@code ReentrantLock}'s
		 * {@code isHeldByCurrentThread()}, so reentrancy is detected the same way.
		 * <p>
		 * Motivated by Log4j2's own garbage-free appenders using {@code synchronized}
		 * rather than a {@code java.util.concurrent} lock around their buffer-transfer
		 * step, and confirmed by real-workload benchmarking to outperform
		 * {@link ReentrantLock} under platform-thread contention - this was the default
		 * for a time. Real-workload benchmarking under virtual threads found the
		 * opposite, a large and reproducible loss versus {@link ReentrantLock} for
		 * reasons not fully understood (classic JEP 491 pinning was checked and ruled
		 * out), so {@link #LOCK_THREAD_LOCAL_BUFFER} is the default now and this type is
		 * an opt-in for platform-thread-heavy deployments that want the edge.
		 * <p>
		 * <strong>Explicitly setting this type honors it</strong> even on a JDK where
		 * {@code synchronized} still pins the carrier platform thread when called from a
		 * virtual thread (before <a href="https://openjdk.org/jeps/491">JEP 491</a>,
		 * finalized in JDK 24) - the one exception is
		 * {@code LogProperties#GLOBAL_APPENDER_REENTRANT_LOCK_PROPERTY}: when that global
		 * property is active it downgrades even an explicit request for this type to
		 * {@link #LOCK_THREAD_LOCAL_BUFFER}, since its whole point is a hard guarantee
		 * independent of anything else in the configuration.
		 */
		SYNCHRONIZED_THREAD_LOCAL_BUFFER,
		/**
		 * Like {@link #LOCK_THREAD_LOCAL_BUFFER} (encoding done outside the lock, the
		 * lock only guards the final write to the output) except the buffer is allocated
		 * fresh for every single event instead of being cached in a {@link ThreadLocal}.
		 * <p>
		 * For deployments that want a hard guarantee of never using {@link ThreadLocal}
		 * anywhere in the logging path - {@link #REUSE_BUFFER} is the existing
		 * no-{@code ThreadLocal} option, but it holds its lock across the entire
		 * encode-then-write critical section (the single shared buffer must stay
		 * protected for as long as anything is writing into it), so it pays for that
		 * guarantee with lock contention proportional to encoding cost, not just I/O
		 * cost. This type keeps {@link #LOCK_THREAD_LOCAL_BUFFER}'s low-contention shape
		 * (encode into a buffer nothing else can see, lock only for the write) while
		 * dropping the {@link ThreadLocal} - the buffer is simply a local variable, not
		 * cached anywhere, so nothing needs to be evicted or leaked-if-forgotten either.
		 * The tradeoff is a fresh buffer allocation (and whatever it grows to internally)
		 * on every single event instead of amortizing that allocation across a thread's
		 * whole lifetime.
		 * @apiNote not the default - {@link #LOCK_THREAD_LOCAL_BUFFER}'s reused buffer is
		 * the better choice unless avoiding {@link ThreadLocal} entirely is a hard
		 * requirement, not just a preference.
		 */
		LOCK_NEW_BUFFER,
		/*
		 * Real-workload benchmarking under virtual threads found
		 * SYNCHRONIZED_THREAD_LOCAL_BUFFER and LOCK_THREAD_LOCAL_BUFFER each winning by a
		 * real, repeatable, double-digit-percentage margin on their own platform (native
		 * image, HotSpot respectively) and losing by a comparable margin on the other - a
		 * genuine cross-over, not just a shrinking gap, so no single fixed choice serves
		 * both well. Matters most for something distributed as more than one build of the
		 * same artifact for the same deployment - a native executable and a plain jar,
		 * say - where picking correctly per build would otherwise mean either shipping
		 * different configuration for each or sniffing the platform in application code.
		 * Detection is the same org.graalvm.nativeimage.imagecode system property check
		 * every native-image-aware framework uses to avoid a hard dependency on GraalVM's
		 * own SDK.
		 */
		/**
		 * Picks whichever concrete type actually performs best for the platform this
		 * process is currently running on, decided once, at appender construction time,
		 * not re-checked afterward. <strong>Currently</strong> that means
		 * {@link #SYNCHRONIZED_THREAD_LOCAL_BUFFER} when running as a GraalVM native
		 * image, or {@link #LOCK_THREAD_LOCAL_BUFFER} otherwise - but that specific
		 * mapping is an implementation detail of this heuristic, not a contract. The
		 * whole point of this type is to track whichever choice is actually best, so
		 * which concrete type a given platform resolves to today may change in a future
		 * release without notice, as benchmarking improves or platforms evolve.
		 * <strong>If your deployment needs a specific type to always be used, pick that
		 * type explicitly instead of relying on what {@code AUTO_DETECT} happens to
		 * resolve to right now.</strong>
		 * <p>
		 * <strong>Not the default</strong> - requires explicitly requesting this type,
		 * programmatically or via {@link LogAppender#APPENDER_TYPE_PROPERTY}. Downgraded
		 * the same as an explicit request for whichever type it currently resolves to
		 * would be: by {@code LogProperties#GLOBAL_APPENDER_REENTRANT_LOCK_PROPERTY} if
		 * it resolves to {@link #SYNCHRONIZED_THREAD_LOCAL_BUFFER}, and by
		 * {@code LogProperties#GLOBAL_THREADLOCAL_DISABLED_PROPERTY} either way.
		 */
		AUTO_DETECT;

		static AppenderType parse(String value) {
			String v = value.toUpperCase(Locale.ROOT);
			return AppenderType.valueOf(v);
		}

	}

	/**
	 * Creates a builder.
	 * @param name appender name.
	 * @return builder.
	 */
	public static Builder builder(String name) {
		/*
		 * Eagerly triggers LogProperties' own key-parameter-value validation now,
		 * matching every annotation-processor-generated Builder's own constructor, which
		 * does the same via its own eager interpolateKey call: needed because this
		 * hand-written Builder can otherwise skip every keyed property lookup entirely
		 * when every field is set explicitly, never validating the name at all. Wrapped
		 * into a ValidationException the same way a generated builder's constructor is,
		 * so a bad name reports the same exception type regardless of whether it came
		 * from logging.appenders (LogAppenderRegistry's own validateNames call) or a
		 * direct programmatic builder(name) call like this one.
		 */
		try {
			LogProperties.interpolateNamedKey(LogProperties.APPENDER_PREFIX, name);
		}
		catch (IllegalArgumentException e) {
			throw LogProperty.ValidationException.of(LogAppender.class, e);
		}
		return new Builder(name);
	}

	/**
	 * Builder for creating standard appenders. Whatever is not set explicitly is resolved
	 * from properties keyed under {@link LogProperties#APPENDER_PREFIX} for this
	 * builder's name (see {@link #fromProperties(LogProperties)}), and failing that from
	 * a small set of defaults: no encoder resolves one from the output's own type, no
	 * flags means none are set, and no appender type means
	 * {@link AppenderType#LOCK_THREAD_LOCAL_BUFFER}. There is deliberately no such
	 * default for output, since a required source is not something a generic named
	 * appender can safely guess, except for the well known {@code "console"}/
	 * {@code "file"} names, which register their own fallback via
	 * {@link #outputDefault(LogProvider)}.
	 */
	public static final class Builder implements LogBuilder<Builder, LogAppender> {

		private @Nullable LogProvider<? extends LogOutput> output = null;

		/*
		 * A weaker fallback than an explicit output(...) call: consulted only if neither
		 * that nor APPENDER_OUTPUT_PROPERTY resolves anything. Package-private, not part
		 * of the public Builder API: DefaultAppenderRegistry is the only caller, for
		 * "console"'s stdout default, where the precedence is deliberately the opposite
		 * of an ordinary explicit value (the property is meant to override this default,
		 * not the other way around).
		 */
		private @Nullable LogProvider<? extends LogOutput> outputDefault = null;

		private @Nullable LogProvider<? extends LogEncoder> encoder = null;

		private @Nullable EnumSet<AppenderFlag> flags = null;

		private @Nullable AppenderType appenderType = null;

		private final String name;

		private Builder(String name) {
			this.name = name;
		}

		Builder outputDefault(LogProvider<? extends LogOutput> outputDefault) {
			this.outputDefault = outputDefault;
			return this;
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
		 * Sets the appender type (buffer/locking strategy). If not set it is resolved
		 * from {@link LogAppender#APPENDER_TYPE_PROPERTY} or otherwise defaults to
		 * {@link AppenderType#LOCK_THREAD_LOCAL_BUFFER}.
		 * @param appenderType appender type.
		 * @return this.
		 */
		public Builder appenderType(AppenderType appenderType) {
			this.appenderType = appenderType;
			return this;
		}

		@Override
		public String propertyPrefix() {
			return LogProperties.APPENDER_PREFIX;
		}

		/**
		 * Fills in whatever of flags/appender type is not already explicitly set on this
		 * builder from properties keyed under {@link #propertyPrefix()} for this
		 * builder's name; an already-set field always wins over the property, matching
		 * every other generated builder's {@code fromProperties} in this project. Both
		 * are resolved and validated together against one {@link LogProperty.Validator},
		 * so a builder with both malformed reports both in a single exception rather than
		 * just the first one reached.
		 * <p>
		 * Output/encoder are deliberately not handled here even though they too are keyed
		 * under {@link #propertyPrefix()}: turning either into a concrete, registered
		 * {@link LogOutput}/{@link LogEncoder} needs a {@link LogConfig} (to actually
		 * look up the URI scheme), which this method does not have. {@link #build()}
		 * resolves those two once a real {@link LogConfig} is available, using the same
		 * property keys.
		 * @param properties properties to resolve unset fields from.
		 * @return this.
		 */
		@Override
		public Builder fromProperties(LogProperties properties) {
			/*
			 * Register every result with the validator first, and only call validate(),
			 * which throws one aggregate exception up front if anything registered is
			 * Missing (when added via add(...)) or an Error (either method), before
			 * extracting any individual value below. Extracting a value via
			 * value()/valueOrNull() throws immediately for a still-unhandled Error (see
			 * LogProperty.Result.Error's own valueOrNull()), which would otherwise let
			 * the first bad property escape unwrapped before the Validator ever got a
			 * chance to collect the rest: the same ordering the annotation processor
			 * generates for every other builder in this project.
			 */
			var validator = LogProperty.Validator.of(LogAppender.class);
			var flagsResult = flags == null ? properties.forKey(APPENDER_FLAGS_PROPERTY, name)
				.ofList()
				.map(AppenderFlag::parse)
				.validateIfError(validator) : null;
			var appenderTypeResult = appenderType == null ? properties.forKey(APPENDER_TYPE_PROPERTY, name)
				.ofString()
				.map(AppenderType::parse)
				.validateIfError(validator) : null;
			validator.validate();
			if (flagsResult != null) {
				var resolved = flagsResult.valueOrNull();
				// AppenderFlag.parse always returns an EnumSet (empty or not), so
				// EnumSet.copyOf is always safe here, never the "non-EnumSet and empty"
				// case it rejects.
				if (resolved != null) {
					flags = EnumSet.copyOf(resolved);
				}
			}
			if (appenderTypeResult != null) {
				appenderType = appenderTypeResult.valueOrNull();
			}
			return this;
		}

		/**
		 * Builds.
		 * @return an appender factory.
		 */
		public LogProvider<LogAppender> build() {
			/*
			 * We need to capture parameters since appender creation needs to be lazy, and
			 * a copy is made below (rather than mutating this builder's own fields) so a
			 * shared/reused Builder instance is never mutated by a later
			 * fromProperties(...) call the lazy lambda makes once a real LogConfig is
			 * available.
			 */
			var _name = name;
			var _output = output;
			var _outputDefault = outputDefault;
			var _encoder = encoder;
			var _flags = flags;
			var _appenderType = appenderType;
			/*
			 * TODO should we use the parent name for resolution?
			 */
			return (n, config) -> {
				var b = new Builder(_name);
				b.flags = _flags;
				b.appenderType = _appenderType;
				b.fromProperties(config.properties());

				LogOutput output = LogProvider.provideOrNull(_output, _name, config);
				if (output == null) {
					/*
					 * A malformed (not just missing) property must throw here,
					 * immediately, regardless of whether a default exists below: e.g.
					 * "console" with a genuinely bad logging.appender.console.output must
					 * fail loudly, not silently fall back to stdout. Only the Missing
					 * case falls through to outputDefault.
					 */
					output = switch (outputProperty(_name, config)) {
						case LogProperty.Result.Success<LogOutput> s -> s.value();
						case LogProperty.Result.Missing<LogOutput> m -> null;
						case LogProperty.Result.Error<LogOutput> e -> e.value();
					};
				}
				if (output == null) {
					output = LogProvider.provideOrNull(_outputDefault, _name, config);
				}
				if (output == null) {
					/*
					 * No explicit value, no property, no default: the same missing
					 * -property failure a required property with no fallback produces
					 * anywhere else. Re-deriving the same (definitely still Missing)
					 * result and calling value() throws it unwrapped, no Validator
					 * involved, same as before this refactor.
					 */
					output = DefaultAppenderRegistry.rawValue(outputProperty(_name, config)).value();
				}

				final LogOutput finalOutput = output;
				LogEncoder encoder = _encoder != null ? LogProvider.provideOrNull(_encoder, _name, config) : null;
				if (output instanceof LogEncoder e) {
					encoder = e;
				}
				if (encoder == null) {
					encoder = DefaultAppenderRegistry
						.rawValue(encoderProperty(_name, config).or(() -> config.encoderRegistry()
							.encoderForOutputType(finalOutput.type())
							.provide(_name, config)))
						.value();
				}

				Set<AppenderFlag> flags = b.flags != null ? b.flags : EnumSet.noneOf(AppenderFlag.class);
				AppenderType appenderType = b.appenderType != null ? b.appenderType
						: AppenderType.LOCK_THREAD_LOCAL_BUFFER;

				return DirectLogAppender.of(_name, output, encoder, appenderType, flags, config.alerts(),
						config.metrics());
			};
		}

		/*
		 * Resolves APPENDER_OUTPUT_PROPERTY all the way to a concrete LogOutput (not just
		 * a LogProvider), catching any exception provide() throws and re-wrapping it with
		 * "Error for property. key: ..." context via Result.map(), the same mechanism
		 * DefaultAppenderRegistry's now-removed outputProperty(...) helper used, moved
		 * here since output resolution is now entirely this Builder's concern.
		 */
		private static LogProperty.Result<LogOutput> outputProperty(String name, LogConfig config) {
			return config.properties()
				.forKey(APPENDER_OUTPUT_PROPERTY, name)
				.ofProvider(LogOutput::of)
				.map(p -> p.provide(name, config));
		}

		private static LogProperty.Result<LogEncoder> encoderProperty(String name, LogConfig config) {
			return config.properties()
				.forKey(APPENDER_ENCODER_PROPERTY, name)
				.ofProvider(LogEncoder::of)
				.map(p -> p.provide(name, config));
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

		Appenders(String name, LogConfig config, List<LogProvider<LogAppender>> appenders) {
			super();
			this.name = name;
			this.config = config;
			this.appenders = appenders;
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
					config.serviceRegistry().put(LogAppender.class, name + "." + ia.name(), ia);
					yield ia;
				}
				case CompositeLogAppender ca -> {
					config.serviceRegistry().put(LogAppender.class, name, ca);
					yield ca;
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
			return CompositeLogAppender.of(appenders);
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

	static DirectLogAppender of(String name, LogOutput output, LogEncoder encoder, AppenderType type,
			Set<LogAppender.AppenderFlag> flags, LogAlerts alerts, LogMetrics metrics) {
		type = AbstractLogAppender.resolveAutoDetectAppenderType(type);
		type = AbstractLogAppender.guardSynchronizedAppenderType(type);
		type = AbstractLogAppender.guardThreadLocalAppenderType(type);
		return switch (type) {
			case REUSE_BUFFER ->
				new ReuseBufferLogAppender(name, output, encoder, flags, new ReentrantLock(), alerts, metrics);
			case SYNCHRONIZED_THREAD_LOCAL_BUFFER ->
				new SynchronizedThreadLocalBufferLogAppender(name, output, encoder, flags, alerts, metrics);
			case LOCK_THREAD_LOCAL_BUFFER -> new LockThreadLocalBufferLogAppender(name, output, encoder, flags,
					new ReentrantLock(), alerts, metrics);
			case LOCK_NEW_BUFFER ->
				new LockNewBufferLogAppender(name, output, encoder, flags, new ReentrantLock(), alerts, metrics);
			case AUTO_DETECT ->
				throw new IllegalStateException("AUTO_DETECT should have already been resolved to a concrete type");
		};
	}

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
	 * {@link LogAppender.AppenderType#SYNCHRONIZED_THREAD_LOCAL_BUFFER} to
	 * {@link LogAppender.AppenderType#LOCK_THREAD_LOCAL_BUFFER} if
	 * {@link #forceReentrantLockAppenders} is active - the enforcement point that makes
	 * the global no-synchronized guarantee a real guarantee rather than just a changed
	 * default.
	 * @param type type as given to an appender factory method.
	 * @return {@code type} unchanged, unless the guarantee is active and
	 * {@code SYNCHRONIZED_THREAD_LOCAL_BUFFER} was requested, in which case
	 * {@code LOCK_THREAD_LOCAL_BUFFER} instead.
	 */
	static LogAppender.AppenderType guardSynchronizedAppenderType(LogAppender.AppenderType type) {
		if (!forceReentrantLockAppenders || type != LogAppender.AppenderType.SYNCHRONIZED_THREAD_LOCAL_BUFFER) {
			return type;
		}
		return LogAppender.AppenderType.LOCK_THREAD_LOCAL_BUFFER;
	}

	/*
	 * Set once from LogProperties#GLOBAL_THREADLOCAL_DISABLED_PROPERTY during LogConfig
	 * construction (see DefaultLogConfig) - a global, process-wide guarantee that no
	 * appender will ever use ThreadLocal, for deployments that want that guaranteed even
	 * when something explicitly requests LOCK_THREAD_LOCAL_BUFFER (the default) or
	 * SYNCHRONIZED_THREAD_LOCAL_BUFFER. Global (not per-route/per-appender) by design,
	 * matching the property's own scope - rainbowgum-slf4j's MDC support independently
	 * reads the same property key to decide whether to disable itself too, see
	 * LogProperties#GLOBAL_THREADLOCAL_DISABLED_PROPERTY's javadoc.
	 */
	static volatile boolean forceNoThreadLocalAppenders = false;

	/**
	 * Downgrades either {@link ThreadLocal}-backed type (
	 * {@link LogAppender.AppenderType#LOCK_THREAD_LOCAL_BUFFER} or
	 * {@link LogAppender.AppenderType#SYNCHRONIZED_THREAD_LOCAL_BUFFER}) to
	 * {@link LogAppender.AppenderType#LOCK_NEW_BUFFER} if
	 * {@link #forceNoThreadLocalAppenders} is active - the enforcement point that makes
	 * the global no-{@link ThreadLocal} guarantee a real guarantee rather than just a
	 * changed default. An explicit {@link LogAppender.AppenderType#REUSE_BUFFER} request
	 * is left as-is - it is already {@link ThreadLocal}-free, so there is nothing to
	 * downgrade.
	 * @param type type as given to an appender factory method.
	 * @return {@code type} unchanged, unless the guarantee is active and a
	 * {@link ThreadLocal}-backed type was requested, in which case
	 * {@code LOCK_NEW_BUFFER} instead.
	 */
	static LogAppender.AppenderType guardThreadLocalAppenderType(LogAppender.AppenderType type) {
		if (!forceNoThreadLocalAppenders) {
			return type;
		}
		return switch (type) {
			case LOCK_THREAD_LOCAL_BUFFER, SYNCHRONIZED_THREAD_LOCAL_BUFFER -> LogAppender.AppenderType.LOCK_NEW_BUFFER;
			case REUSE_BUFFER, LOCK_NEW_BUFFER -> type;
			case AUTO_DETECT ->
				throw new IllegalStateException("AUTO_DETECT should have already been resolved to a concrete type");
		};
	}

	/**
	 * Resolves {@link LogAppender.AppenderType#AUTO_DETECT} to
	 * {@link LogAppender.AppenderType#SYNCHRONIZED_THREAD_LOCAL_BUFFER} or
	 * {@link LogAppender.AppenderType#LOCK_THREAD_LOCAL_BUFFER} depending on
	 * {@link #isNativeImageRuntime()}; any other type is returned unchanged. Called
	 * before {@link #guardSynchronizedAppenderType(LogAppender.AppenderType)} and
	 * {@link #guardThreadLocalAppenderType(LogAppender.AppenderType)} so a resolved
	 * {@code SYNCHRONIZED_THREAD_LOCAL_BUFFER} is still subject to both of those global
	 * guarantees the same as if it had been requested directly.
	 * @param type type as given to an appender factory method.
	 * @return {@code type} unchanged unless it was {@code AUTO_DETECT}.
	 */
	static LogAppender.AppenderType resolveAutoDetectAppenderType(LogAppender.AppenderType type) {
		if (type != LogAppender.AppenderType.AUTO_DETECT) {
			return type;
		}
		return isNativeImageRuntime() ? LogAppender.AppenderType.SYNCHRONIZED_THREAD_LOCAL_BUFFER
				: LogAppender.AppenderType.LOCK_THREAD_LOCAL_BUFFER;
	}

	/**
	 * The system property GraalVM native-image itself sets to {@code buildtime} during
	 * the image build and {@code runtime} when the built image actually executes - absent
	 * entirely on a plain JVM. The standard, dependency-free way every native-image-aware
	 * framework checks "am I native" without a hard compile-time dependency on GraalVM's
	 * own SDK just to ask this one question.
	 */
	static final String NATIVE_IMAGE_CODE_PROPERTY = "org.graalvm.nativeimage.imagecode";

	/**
	 * Whether this process is currently running as an executing (not building) GraalVM
	 * native image - see {@link #NATIVE_IMAGE_CODE_PROPERTY}.
	 * @return true if running as a native image at runtime.
	 */
	static boolean isNativeImageRuntime() {
		return "runtime".equals(System.getProperty(NATIVE_IMAGE_CODE_PROPERTY));
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

	public static CompositeLogAppender of(List<? extends LogAppender> appenders) {
		@SuppressWarnings("null") // TODO Eclipse issue here
		DirectLogAppender @NonNull [] array = appenders.stream()
			.map(CompositeLogAppender::cast)
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
			metrics.errorCounter(LogMetrics.EVENTS_FAILED_METRIC, 1);
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
			metrics.errorCounter(LogMetrics.EVENTS_FAILED_METRIC, count);
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
			metrics.errorCounter(LogMetrics.EVENTS_FAILED_METRIC, 1);
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
			metrics.errorCounter(LogMetrics.EVENTS_FAILED_METRIC, count);
		}
	}

}

/*
 * Like LockThreadLocalBufferLogAppender (encode outside the lock, lock only the final
 * write) except the buffer is a fresh allocation per event instead of a ThreadLocal - for
 * deployments that want a hard guarantee of no ThreadLocal anywhere in the logging path
 * without paying ReuseBufferLogAppender's lock-across-the-whole-encode cost.
 */
final class LockNewBufferLogAppender extends LockLogAppender implements InternalLogAppender {

	LockNewBufferLogAppender(String name, LogOutput output, LogEncoder encoder, Set<LogAppender.AppenderFlag> flags,
			ReentrantLock lock, LogAlerts alerts, LogMetrics metrics) {
		super(name, output, encoder, flags, lock, alerts, metrics);
	}

	@Override
	public final void append(LogEvent event) {
		try {
			var buffer = encoder.buffer(output.bufferHints());
			encoder.encode(event, buffer);
			writeLocked(event, buffer);
		}
		catch (Exception e) {
			alerts.error(getClass(), "appender '" + name + "' failed to append event", e);
			metrics.errorCounter(LogMetrics.EVENTS_FAILED_METRIC, 1);
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
			var buffer = encoder.buffer(output.bufferHints());
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
			metrics.errorCounter(LogMetrics.EVENTS_FAILED_METRIC, count);
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
			metrics.errorCounter(LogMetrics.EVENTS_FAILED_METRIC, 1);
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
			metrics.errorCounter(LogMetrics.EVENTS_FAILED_METRIC, count);
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

}
