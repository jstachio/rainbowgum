package io.jstach.rainbowgum.router;

import java.util.concurrent.locks.ReentrantLock;

import io.jstach.rainbowgum.LevelResolver;
import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogRouter;

public class LockingQueueRouter implements LogRouter.SyncLogRouter {

	private final LevelResolver levelResolver;

	private final LogAppender appender;

	private final ReentrantLock lock = new ReentrantLock(false);

	public LockingQueueRouter(LogAppender appender, LevelResolver levelResolver) {
		super();
		this.appender = appender;
		this.levelResolver = levelResolver;
	}

	@Override
	public LevelResolver levelResolver() {
		return this.levelResolver;
	}

	@Override
	public void log(LogEvent event) {
		lock.lock();
		try {
			appender.append(event);
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public void close() {
		lock.lock();
		try {
			appender.close();
		}
		finally {
			lock.unlock();
		}

	}

}