package io.jstach.rainbowgum;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A component that has a start and stop.
 */
public interface LogLifecycle extends AutoCloseable {

	/**
	 * Starts a component.
	 * @param config log config.
	 */
	public void start(LogConfig config);

	public void close();

}

final class ShutdownManager {

	private static final ReentrantReadWriteLock staticLock = new ReentrantReadWriteLock();

	private static CopyOnWriteArrayList<AutoCloseable> shutdownHooks = new CopyOnWriteArrayList<>();

	// we do not need the newer VarHandle because there is only one of these guys
	private static AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);

	static void addShutdownHook(AutoCloseable hook) {
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
				MetaLog.error(Defaults.class, e);
			}
		}
		// Help the GC or whatever final cleanup is going on
		shutdownHooks.clear();
	}

}
