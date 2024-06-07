package io.jstach.rainbowgum;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogAppender.ThreadSafeLogAppender;
import io.jstach.rainbowgum.LogEncoder.Buffer;
import io.jstach.rainbowgum.LogResponse.Status;

/**
 * Appenders are guaranteed to be written synchronously much like an actor in actor
 * concurrency. To ensure this invariant call
 * {@link ThreadSafeLogAppender#of(LogAppender)}.
 *
 * The only exception is if an Appender implements {@link ThreadSafeLogAppender}.
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
	 * Creates a composite log appender from many. The appenders will be appended
	 * synchronously and are not thread safe.
	 * @param appenders appenders.
	 * @return appender.
	 * @see ThreadSafeLogAppender#of(LogAppender)
	 */
	public static LogAppender of(List<? extends LogAppender> appenders) {
		if (appenders.isEmpty()) {
			throw new IllegalArgumentException("A single appender is required");
		}
		if (appenders.size() == 1) {
			return Objects.requireNonNull(appenders.get(0));
		}
		@SuppressWarnings("null") // TODO Eclipse issue here
		InternalLogAppender @NonNull [] array = appenders.stream()
			.map(ThreadSafeLogAppender::unwrapIfThreadSafe)
			.toArray(i -> new InternalLogAppender[i]);
		return new CompositeLogAppender(array);
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

	@Override
	public void close();

	/**
	 * An appender that can be used by a {@link LogPublisher.SyncLogPublisher}.
	 */
	public sealed interface ThreadSafeLogAppender extends InternalLogAppender {

		/*
		 * TODO maybe we make all appenders thread safe.
		 */

		/**
		 * Make an appender thread safe if is not already thread safe.
		 * @param appender appender.
		 * @return new thread safe appender if is not one or the passed in appender if it
		 * thread safe.
		 */
		@SuppressWarnings({ "null", "resource" }) // TODO eclipse bugs.
		public static ThreadSafeLogAppender of(LogAppender appender) {
			// TODO eclipse needs a local variable for some reason
			ThreadSafeLogAppender rv = switch (appender) {
				case InternalLogAppender internal -> internal.wrap();
				default -> throw new IllegalStateException(); // TODO eclipse bug
			};
			return rv;

		}

		/**
		 * If thread safety is not needed because it is taken care of through a publisher
		 * this call will attempt to return the orginal non thread safe appender.
		 * @return wrapped un-threadsafe appender.
		 */
		public LogAppender unwrap();

		@Override
		default ThreadSafeLogAppender wrap() {
			return this;
		}

		private static InternalLogAppender unwrapIfThreadSafe(LogAppender appender) {
			if (appender instanceof ThreadSafeLogAppender ta) {
				return InternalLogAppender.of(ta.unwrap());
			}
			return InternalLogAppender.of(appender);
		}

	}

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

	ThreadSafeLogAppender wrap();

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

	@Override
	default void append(LogEvent[] event, int count) {
		for (var appender : components()) {
			appender.append(event, count);
		}
	}

	@Override
	default void append(LogEvent event) {
		for (var appender : components()) {
			appender.append(event);
		}
	}

	@Override
	default void close() {
		for (var appender : components()) {
			appender.close();
		}
	}

	@Override
	default void start(LogConfig config) {
		for (var appender : components()) {
			appender.start(config);
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
		return Actor.act(components(), action);
	}

}

record CompositeLogAppender(
		InternalLogAppender[] appenders) implements BaseComposite<InternalLogAppender>, InternalLogAppender {

	@Override
	public InternalLogAppender[] components() {
		return this.appenders;
	}

	@Override
	public ThreadSafeLogAppender wrap() {
		ThreadSafeLogAppender[] array = Stream.of(appenders)
			.map(a -> ThreadSafeLogAppender.of(a))
			.toArray(i -> new @NonNull ThreadSafeLogAppender[i]);
		return new CompositeThreadSafeLogAppender(array);
	}

}

record CompositeThreadSafeLogAppender(
		ThreadSafeLogAppender[] appenders) implements BaseComposite<ThreadSafeLogAppender>, ThreadSafeLogAppender {

	@Override
	public ThreadSafeLogAppender[] components() {
		return this.appenders;
	}

	@Override
	public LogAppender unwrap() {
		@SuppressWarnings("null")
		DirectLogAppender[] array = Stream.of(appenders)
			.map(ThreadSafeLogAppender::unwrap)
			.toArray(i -> new DirectLogAppender[i]);
		return new CompositeLogAppender(array);
	}

}

/*
 * The idea here is to have the virtual thread do the formatting outside of the lock
 */
final class DefaultLogAppender extends AbstractLogAppender implements ThreadSafeLogAppender {

	private final ReentrantLock lock = new ReentrantLock();

	public DefaultLogAppender(String name, LogOutput output, LogEncoder encoder) {
		super(name, output, encoder);
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
	public LogAppender unwrap() {
		return new BufferLogAppender(name, output, encoder);
	}

}

final class BufferLogAppender extends AbstractLogAppender implements InternalLogAppender {

	private final Buffer buffer;

	public BufferLogAppender(String name, LogOutput output, LogEncoder encoder) {
		super(name, output, encoder);
		this.buffer = encoder.buffer(output.bufferHints());
	}

	@Override
	public List<LogResponse> act(LogAction action) {
		return _request(action);
	}

	@Override
	public void append(LogEvent[] events, int count) {
		buffer.clear();
		output.write(events, count, encoder, buffer);
		output.flush();
	}

	@Override
	public void append(LogEvent event) {
		buffer.clear();
		output.write(event, buffer);
		output.flush();
	}

	@Override
	public ThreadSafeLogAppender wrap() {
		return new DefaultLogAppender(name, output, encoder);
	}

}
