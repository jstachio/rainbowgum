package io.jstach.rainbowgum.output;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;

/**
 * An output for debugging.
 */
public final class ListLogOutput implements LogOutput {

	private List<Entry<LogEvent, String>> events;

	private volatile BiConsumer<LogEvent, String> consumer;

	private ListLogOutput(List<Entry<LogEvent, String>> events, BiConsumer<LogEvent, String> consumer) {
		super();
		this.events = events;
		this.consumer = consumer;
	}

	@Override
	public URI uri() throws UnsupportedOperationException {
		throw new UnsupportedOperationException("uri");
	}

	/**
	 * Creates a list output.
	 */
	public ListLogOutput() {
		this(new ArrayList<>(), (e, s) -> {
		});
	}

	/**
	 * Sets a consumer for testing purposes. Default is a noop.
	 * @param consumer callback to consume events.
	 */
	public void setConsumer(BiConsumer<LogEvent, String> consumer) {
		this.consumer = Objects.requireNonNull(consumer);
	}

	@Override
	public void write(LogEvent event, String s) {
		events.add(Map.entry(event, s));
		this.consumer.accept(event, s);
	}

	@Override
	public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
		write(event, new String(bytes, off, len, StandardCharsets.UTF_8));
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
	}

	/**
	 * The arraylist that contains the logged events so far.
	 * @return list.
	 */
	public List<Entry<LogEvent, String>> events() {
		return events;
	}

	@Override
	public String toString() {
		return events.stream().map(e -> e.getValue()).collect(Collectors.joining());
	}

	@Override
	public OutputType type() {
		return OutputType.MEMORY;
	}

	/**
	 * Clears the currently stored events.
	 */
	public void clear() {
		events.clear();
	}

}