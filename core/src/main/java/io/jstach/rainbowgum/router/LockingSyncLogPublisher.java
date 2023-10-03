package io.jstach.rainbowgum.router;

import java.util.concurrent.locks.ReentrantLock;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogPublisher;

public final class LockingSyncLogPublisher implements LogPublisher.SyncLogPublisher {

	private final LogAppender appender;

	private final ReentrantLock lock = new ReentrantLock(false);

	public LockingSyncLogPublisher(LogAppender appender) {
		super();
		this.appender = appender;
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
