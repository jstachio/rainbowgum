package io.jstach.rainbowgum;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogResponse.Status;

/**
 * Appenders are guaranteed to be written synchronously much like an actor in actor
 * concurrency.
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
			/*
			 * TODO should we use the parent name for resolution?
			 */
			return (n, config) -> {
				AppenderConfig a = new AppenderConfig(_name, LogProvider.provideOrNull(_output, _name, config),
						LogProvider.provideOrNull(_encoder, _name, config));
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
				for (var a : apps) {
					register(a);
				}
				return apps;
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
				register(appender);
				return appender;
			}
			else {
				throw new IllegalStateException("Appenders already provided.");
			}
		}

		private void register(LogAppender appender) {
			switch (appender) {
				case DirectLogAppender ia -> {
					config.serviceRegistry().put(LogAppender.class, name + "." + ia.name(), ia);
				}
				case CompositeLogAppender ca -> {
					config.serviceRegistry().put(LogAppender.class, name, ca);
				}
				default -> {
					throw new IllegalStateException();
				}
			}
			;
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
			return CompositeLogAppender.of(appenders, lock);

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

	static List<DirectLogAppender> findAppenders(ServiceRegistry registry) {
		List<DirectLogAppender> appenders = new ArrayList<>();
		for (var a : registry.find(LogAppender.class)) {
			if (a instanceof InternalLogAppender internal) {
				internal.visit(appenders::add);
			}
		}
		return appenders;
	}

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

	/**
	 * Creates an appender from an output and encoder.
	 * @param output set the output field and will be started and closed with the
	 * appender.
	 * @param encoder set the encoder field.
	 */
	protected AbstractLogAppender(String name, LogOutput output, LogEncoder encoder) {
		super();
		this.name = name;
		this.output = output;
		this.encoder = encoder;
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
		return getClass().getName() + "[name=" + name + " encoder=" + encoder + ", " + "output=" + output + "]";
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

	public static CompositeLogAppender of(List<? extends LogAppender> appenders, Lock lock) {
		@SuppressWarnings("null") // TODO Eclipse issue here
		InternalLogAppender @NonNull [] array = appenders.stream()
			.map(InternalLogAppender::of)
			.map(a -> a.changeLock(lock))
			.toArray(i -> new InternalLogAppender[i]);
		return new CompositeLogAppender(array, lock);
	}

	@Override
	public InternalLogAppender[] components() {
		return this.appenders;
	}

	@Override
	public InternalLogAppender changeLock(Lock lock) {
		return of(List.of(appenders), lock);
	}

}

/*
 * The idea here is to have the virtual thread do the formatting outside of the lock
 */
final class DefaultLogAppender extends AbstractLogAppender implements InternalLogAppender {

	private final Lock lock;

	public DefaultLogAppender(String name, LogOutput output, LogEncoder encoder, Lock lock) {
		super(name, output, encoder);
		this.lock = lock;
	}

	public DefaultLogAppender(String name, LogOutput output, LogEncoder encoder) {
		this(name, output, encoder, new ReentrantLock());
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
	public final void append(LogEvent event) {
		try (var buffer = encoder.buffer(output.bufferHints())) {
			encoder.encode(event, buffer);
			lock.lock();
			try {
				output.write(event, buffer);
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
			output.flush();
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
	public InternalLogAppender changeLock(Lock lock) {
		return new DefaultLogAppender(name, output, encoder, lock);
	}

}
