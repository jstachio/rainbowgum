package io.jstach.rainbowgum.router;

import java.lang.System.Logger.Level;
import java.util.concurrent.locks.ReentrantLock;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogRouter;

public class LockingQueueRouter implements LogRouter.SyncLogRouter {

	private final LogAppender appender;

	private final ReentrantLock lock = new ReentrantLock(false);

	public LockingQueueRouter(LogAppender appender) {
		super();
		this.appender = appender;
	}

	@Override
	public boolean isEnabled(String loggerName, Level level) {
		return true;
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
