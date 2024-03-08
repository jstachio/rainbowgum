package io.jstach.rainbowgum;

import java.util.ArrayDeque;
import java.util.Deque;

import io.jstach.rainbowgum.LogPublisher.SyncLogPublisher;

class TestSyncPublisher implements SyncLogPublisher {

	public Deque<LogEvent> events = new ArrayDeque<>();

	@Override
	public void start(LogConfig config) {
	}

	@Override
	public void close() {
	}

	@Override
	public void log(LogEvent event) {
		events.add(event);
	}

}
