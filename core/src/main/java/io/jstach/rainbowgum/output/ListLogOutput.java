package io.jstach.rainbowgum.output;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;

public class ListLogOutput implements LogOutput {

	private List<Entry<LogEvent, String>> events;

	public ListLogOutput(List<Entry<LogEvent, String>> events) {
		super();
		this.events = events;
	}

	public ListLogOutput() {
		this(new ArrayList<>());
	}

	@Override
	public void write(LogEvent event, String s) {
		events.add(Map.entry(event, s));
	}

	@Override
	public void write(LogEvent event, byte[] bytes, int off, int len) {
		write(event, new String(bytes, off, len, StandardCharsets.UTF_8));
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
	}

	public List<Entry<LogEvent, String>> events() {
		return events;
	}

	@Override
	public String toString() {
		return events.stream().map(e -> e.getValue()).collect(Collectors.joining());
	}

}