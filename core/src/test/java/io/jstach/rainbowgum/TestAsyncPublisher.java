package io.jstach.rainbowgum;

import java.util.ArrayDeque;
import java.util.Deque;

import io.jstach.rainbowgum.LogPublisher.AsyncLogPublisher;

class TestAsyncPublisher implements AsyncLogPublisher {

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
