package io.jstach.rainbowgum;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
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
		 * The appender will call flush on each item appended or if in async batch mode
		 * for each batch.
		 */
		IMMEDIATE_FLUSH;

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
			this.encoder = LogProvider.of(LogEncoder.of(formatter));
			return this;
		}

		/**
		 * Sets formatter as encoder.
		 * @param formatter formatter to be converted to encoder.
		 * @return builder.
		 * @see LogEncoder#of(LogFormatter)
		 */
		public Builder formatter(LogFormatter.EventFormatter formatter) {
			this.encoder = LogProvider.of(LogEncoder.of(formatter));
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
		 * Consolidate the appenders as a single appender. The appenders will be appended
		 * synchronously and will share the same lock.
		 * @return single appender.
		 * @throws IllegalStateException if appenders are already registered.
		 */
		public LogAppender asSingle() throws IllegalStateException {
			if (created.compareAndSet(false, true)) {
				var apps = appenders();
				var appender = single(apps);
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
		 * Creates a composite log appender from many. The appenders will be appended
		 * synchronously and will share the same lock.
		 * @param appenders appenders.
		 * @return appender.
		 */
		private static LogAppender single(List<? extends LogAppender> appenders) {
			if (appenders.isEmpty()) {
				throw new IllegalArgumentException("A single appender is required");
			}
			if (appenders.size() == 1) {
				return Objects.requireNonNull(appenders.get(0));
			}
			Lock lock = new ReentrantLock();
			return CompositeLogAppender.of(appenders, lock, Set.of());

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
		// InternalLogAppender a = switch (appender) {
		// case InternalLogAppender ia -> ia;
		// };
		return Objects.requireNonNull((InternalLogAppender) appender); // TODO eclipse
																		// bug.
	}

	/**
	 * THIS IS A JAVADOC BUG.
	 * @param visitor ignore
	 * @return true if stop.
	 */
	boolean visit(AppenderVisitor visitor);

	InternalLogAppender changeLock(Lock lock);

	InternalLogAppender withFlags(Set<LogAppender.AppenderFlag> flags);

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
				case LogAction.StandardAction.STATUS -> List.of(status());
			};
			default -> throw new IllegalArgumentException(); // TODO fucking eclipse
		};
		return r;
	}

	default LogResponse reopen() {
		var status = output().reopen();
		return new Response(name(), status);
	}

	default LogResponse flush() {
		output().flush();
		return new Response(name(), LogResponse.Status.StandardStatus.OK);
	}

	default LogResponse status() {
		Status status;
		try {
			status = output().status();
		}
		catch (Exception e) {
			status = LogResponse.Status.ofError(e);
		}
		return new Response(name(), status);
	}

	static List<DirectLogAppender> findAppenders(ServiceRegistry registry) {
		List<DirectLogAppender> appenders = new ArrayList<>();
		for (var a : registry.find(LogAppender.class)) {
			if (a instanceof InternalLogAppender internal) {
				internal.visit(appenders::add);
			}
		}
		return appenders;
	}

	static DirectLogAppender of(String name, LogOutput output, LogEncoder encoder,
			Set<LogAppender.AppenderFlag> flags) {
		if (flags.contains(AppenderFlag.REUSE_BUFFER)) {
			return new ReuseBufferLogAppender(name, output, encoder, flags, new ReentrantLock());
		}
		return new DefaultLogAppender(name, output, encoder, flags, new ReentrantLock());
	}

	@Override
	DirectLogAppender withFlags(Set<LogAppender.AppenderFlag> flags);

	@Override
	DirectLogAppender changeLock(Lock lock);

}

/**
 * An abstract appender to help create custom appenders.
 */
sealed abstract class AbstractLogAppender implements DirectLogAppender {

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
	 * Creates an appender from an output and encoder.
	 * @param output set the output field and will be started and closed with the
	 * appender.
	 * @param encoder set the encoder field.
	 */
	protected AbstractLogAppender(String name, LogOutput output, LogEncoder encoder,
			Set<LogAppender.AppenderFlag> flags) {
		super();
		this.name = name;
		this.output = output;
		this.encoder = encoder;
		this.flags = flags;
		this.immediateFlush = flags.contains(LogAppender.AppenderFlag.IMMEDIATE_FLUSH);
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
	public boolean visit(AppenderVisitor visitor) {
		return visitor.consume(this);
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

sealed interface BaseComposite<T extends InternalLogAppender> extends InternalLogAppender {

	T[] components();

	Lock lock();

	@Override
	default void append(LogEvent[] event, int count) {
		lock().lock();
		try {
			for (var appender : components()) {
				appender.append(event, count);
			}
		}
		finally {
			lock().unlock();
		}
	}

	@Override
	default void append(LogEvent event) {
		lock().lock();
		try {
			for (var appender : components()) {
				appender.append(event);
			}
		}
		finally {
			lock().unlock();
		}
	}

	@Override
	default void close() {
		lock().lock();
		try {
			for (var appender : components()) {
				appender.close();
			}
		}
		finally {
			lock().unlock();
		}
	}

	@Override
	default void start(LogConfig config) {
		lock().lock();
		try {
			for (var appender : components()) {
				appender.start(config);
			}
		}
		finally {
			lock().unlock();
		}

	}

	@Override
	default boolean visit(AppenderVisitor visitor) {
		for (var appender : components()) {
			if (appender.visit(visitor)) {
				return true;
			}
		}
		return false;
	}

	@Override
	default List<LogResponse> act(LogAction action) {
		lock().lock();
		try {
			return Actor.act(components(), action);
		}
		finally {
			lock().unlock();
		}
	}

}

record CompositeLogAppender(InternalLogAppender[] appenders,
		Lock lock) implements BaseComposite<InternalLogAppender>, InternalLogAppender {

	public static CompositeLogAppender of(List<? extends LogAppender> appenders, Lock lock,
			Set<LogAppender.AppenderFlag> flags) {
		@SuppressWarnings("null") // TODO Eclipse issue here
		InternalLogAppender @NonNull [] array = appenders.stream()
			.map(InternalLogAppender::of)
			.map(a -> a.changeLock(lock).withFlags(flags))
			.toArray(i -> new InternalLogAppender[i]);
		return new CompositeLogAppender(array, lock);
	}

	@Override
	public InternalLogAppender[] components() {
		return this.appenders;
	}

	@Override
	public CompositeLogAppender changeLock(Lock lock) {
		return of(List.of(appenders), lock, EnumSet.noneOf(LogAppender.AppenderFlag.class));
	}

	@Override
	public CompositeLogAppender withFlags(Set<LogAppender.AppenderFlag> flags) {
		if (flags.isEmpty()) {
			return this;
		}
		return of(List.of(appenders), lock, flags);
	}

}

sealed abstract class LockLogAppender extends AbstractLogAppender implements InternalLogAppender {

	protected final Lock lock;

	public LockLogAppender(String name, LogOutput output, LogEncoder encoder, Set<LogAppender.AppenderFlag> flags,
			Lock lock) {
		super(name, output, encoder, flags);
		this.lock = lock;
	}

	@Override
	public List<LogResponse> act(LogAction action) {
		lock.lock();
		try {
			return _request(action);
		}
		catch (UncheckedIOException ioe) {
			return List.of(new Response(name, Status.ErrorStatus.of(ioe)));
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
		if (flags.contains(LogAppender.AppenderFlag.REUSE_BUFFER)) {
			return new ReuseBufferLogAppender(name, output, encoder, flags, lock);
		}
		return new DefaultLogAppender(name, output, encoder, flags, lock);
	}

}

/*
 * The idea here is to have the virtual thread do the formatting outside of the lock
 */
final class DefaultLogAppender extends LockLogAppender implements InternalLogAppender {

	DefaultLogAppender(String name, LogOutput output, LogEncoder encoder, Set<LogAppender.AppenderFlag> flags,
			Lock lock) {
		super(name, output, encoder, flags, lock);
	}

	@Override
	public final void append(LogEvent event) {
		try (var buffer = encoder.buffer(output.bufferHints())) {
			encoder.encode(event, buffer);
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
	}

	@Override
	public void append(LogEvent[] events, int count) {
		lock.lock();
		try {
			output.write(events, count, encoder);
			if (immediateFlush) {
				output.flush();
			}
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public DirectLogAppender changeLock(Lock lock) {
		return new DefaultLogAppender(name, output, encoder, flags, lock);
	}

}

/*
 * The idea here is to reuse the buffer trading lock contention for less GC.
 */
final class ReuseBufferLogAppender extends LockLogAppender implements InternalLogAppender {

	private final LogEncoder.Buffer buffer;

	ReuseBufferLogAppender(String name, LogOutput output, LogEncoder encoder, Set<LogAppender.AppenderFlag> flags,
			Lock lock) {
		super(name, output, encoder, flags, lock);
		this.buffer = encoder.buffer(output.bufferHints());
	}

	// ReuseBufferLogAppender(String name, LogOutput output, LogEncoder encoder) {
	// this(name, output, encoder, EnumSet.noneOf(LogAppender.AppenderFlag.class), new
	// ReentrantLock());
	// }

	@Override
	public final void append(LogEvent event) {
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

	@Override
	public void append(LogEvent[] events, int count) {
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

	@Override
	public DirectLogAppender changeLock(Lock lock) {
		return new ReuseBufferLogAppender(name, output, encoder, flags, lock);
	}

}
