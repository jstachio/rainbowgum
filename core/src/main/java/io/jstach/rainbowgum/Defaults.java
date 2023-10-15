package io.jstach.rainbowgum;

import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import io.jstach.rainbowgum.LogAppender.ThreadSafeLogAppender;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.format.StandardEventFormatter;
import io.jstach.rainbowgum.publisher.BlockingQueueAsyncLogPublisher;

/**
 * Static defaults that should probably be in the config class.
 */
public class Defaults {

	public static final String SHUTDOWN = "#SHUTDOWN#";

	public static final String ROOT_PROPERTY_PREFIX = "rainbowgum";

	public static final String LOGGER_PROPERTY_PREFIX = "log";

	private static final ReentrantReadWriteLock staticLock = new ReentrantReadWriteLock();

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	private static CopyOnWriteArrayList<AutoCloseable> shutdownHooks = new CopyOnWriteArrayList<>();

	// we do not need the newer VarHandle because there is only one of these guys
	private static AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);

	private final EnumMap<OutputType, Supplier<? extends LogFormatter>> formatters = new EnumMap<>(OutputType.class);

	private final Properties properties;

	public Defaults(LogProperties logProperties) {
		this.properties = Properties.of(logProperties);
	}

	static Function<LogAppender, ThreadSafeLogAppender> threadSafeAppender = (appender) -> {
		return new LockingLogAppender(appender);
	};

	public LogPublisher.AsyncLogPublisher asyncPublisher(List<? extends LogAppender> appenders, int bufferSize) {
		return BlockingQueueAsyncLogPublisher.of(appenders, bufferSize);
	}

	public LogAppender logAppender(LogOutput output, LogEncoder encoder) {
		if (encoder == null) {
			encoder = LogEncoder.of(formatterForOutputType(output.type()));
		}
		return properties.property("defaults.appender.buffer").parseBoolean(false)
				? new BufferLogAppender(output, encoder) : new DefaultLogAppender(output, encoder);
	}

	public LogFormatter formatterForOutputType(OutputType outputType) {
		lock.readLock().lock();
		try {
			var formatter = formatters.get(outputType);
			if (formatter == null) {
				return StandardEventFormatter.builder().build();
			}
			return formatter.get();
		}
		finally {
			lock.readLock().unlock();
		}
	}

	public void setFormatterForOutputType(OutputType outputType, Supplier<? extends LogFormatter> formatter) {
		lock.writeLock().lock();
		try {
			formatters.put(outputType, formatter);
		}
		finally {
			lock.writeLock().unlock();
		}
	}

	public static IntSupplier maxStringBuilderSize = () -> {
		return 1024 * 1024;
	};

	public static void addShutdownHook(AutoCloseable hook) {
		staticLock.writeLock().lock();
		try {
			if (shutdownHookRegistered.compareAndSet(false, true)) {
				var thread = new Thread(() -> {
					runShutdownHooks();
				});
				thread.setName("rainbowgum-shutdown");
				Runtime.getRuntime().addShutdownHook(thread);
			}
			shutdownHooks.add(hook);
		}
		finally {
			staticLock.writeLock().unlock();
		}
	}

	private static void runShutdownHooks() {
		/*
		 * We do not lock here since we are in the shutdown thread luckily shutdownHooks
		 * is thread safe
		 */
		for (var hook : shutdownHooks) {
			try {
				hook.close();
			}
			catch (Exception e) {
				Errors.error(Defaults.class, e);
			}
		}
		// Help the GC or whatever final cleanup is going on
		shutdownHooks.clear();
	}

}
