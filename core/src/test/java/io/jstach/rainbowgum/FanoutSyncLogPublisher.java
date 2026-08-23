package io.jstach.rainbowgum;

import java.util.List;

/**
 * A synchronous publisher built to exercise {@link LogAppender.Appenders#asList()}, which
 * - unlike {@link LogAppender.Appenders#asSingle()}/
 * {@link LogAppender.Appenders#asSingleSharedLock()} - has no real production caller yet
 * (see {@code LogPublisherRegistry.DefaultPublisherProviders}, which only ever calls
 * {@code asSingle()}). {@code asList()} exists for a future <em>fanout</em>-style
 * publisher: one that pushes each event to every appender independently rather than
 * combining them into one composite appender first.
 * <p>
 * This one is deliberately the simplest possible fanout: no composite wrapping, no shared
 * lock - each appender in the list keeps the independent lock it was built with, and is
 * appended to directly, in list order. Unlike {@link IndependentLockCompositeLogAppender}
 * (what {@code asSingle()} without {@code SHARED_APPENDER_LOCK} produces), there is no
 * single {@link LogAppender} object wrapping the list - the fan-out happens here, at the
 * publisher level, which is exactly what a real fanout publisher (e.g. one that pushes to
 * per-appender queues/threads instead of a synchronous loop) would also do.
 */
final class FanoutSyncLogPublisher implements LogPublisher.SyncLogPublisher {

	private final List<? extends LogAppender> appenders;

	FanoutSyncLogPublisher(List<? extends LogAppender> appenders) {
		this.appenders = appenders;
	}

	@Override
	public void log(LogEvent event) {
		for (var appender : appenders) {
			appender.append(event);
		}
	}

	@Override
	public void start(LogConfig config) {
		for (var appender : appenders) {
			appender.start(config);
		}
	}

	@Override
	public void close() {
		for (var appender : appenders) {
			appender.close();
		}
	}

	/*
	 * Exposed for unit test, matching DefaultSyncLogPublisher's own precedent.
	 */
	List<? extends LogAppender> appenders() {
		return this.appenders;
	}

	@Override
	public String toString() {
		return "FanoutSyncLogPublisher[appenders=" + appenders + "]";
	}

}
