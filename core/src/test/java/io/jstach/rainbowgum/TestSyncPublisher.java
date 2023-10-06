package io.jstach.rainbowgum;

import java.util.Deque;
import java.util.LinkedList;

import io.jstach.rainbowgum.LogPublisher.SyncLogPublisher;

public class TestSyncPublisher implements SyncLogPublisher {

	public Deque<LogEvent> events = new LinkedList<>();

	@Override
	public void close() {
	}

	@Override
	public void log(LogEvent event) {
		events.add(event);
	}

}
