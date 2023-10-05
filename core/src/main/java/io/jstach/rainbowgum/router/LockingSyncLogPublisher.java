package io.jstach.rainbowgum.router;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogAppender.LockingLogAppender;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogPublisher;

public final class LockingSyncLogPublisher implements LogPublisher.SyncLogPublisher {

	private final LockingLogAppender appender;

	public LockingSyncLogPublisher(LogAppender appender) {
		super();
		this.appender = LockingLogAppender.of(appender);
	}

	@Override
	public void log(LogEvent event) {
		appender.append(event);
	}

	@Override
	public void close() {
		appender.close();
	}

}
