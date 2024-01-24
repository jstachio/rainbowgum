package io.jstach.rainbowgum;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
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

interface Shutdownable {

	public void shutdown();

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

	static void removeShutdownHook(AutoCloseable hook) {
		staticLock.writeLock().lock();
		try {
			shutdownHooks.removeIf(h -> h == hook);
		}
		finally {
			staticLock.writeLock().unlock();
		}

	}

	/*
	 * This is for unit testing.
	 */
	static List<AutoCloseable> shutdownHooks() {
		return List.copyOf(shutdownHooks);
	}

	private static void runShutdownHooks() {
		/*
		 * We do not lock here since we are in the shutdown thread luckily shutdownHooks
		 * is thread safe
		 */
		var found = Collections.newSetFromMap(new IdentityHashMap<>());
		for (var hook : shutdownHooks) {
			try {
				if (found.add(hook)) {
					if (hook instanceof Shutdownable shut) {
						shut.shutdown();
					}
					else {
						hook.close();
					}
				}
			}
			catch (Exception e) {
				MetaLog.error(Defaults.class, e);
			}
		}
		// Help the GC or whatever final cleanup is going on
		shutdownHooks.clear();
	}

}
