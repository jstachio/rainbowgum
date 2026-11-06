package rainbowgum.weblog.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;

/**
 * Keeps the most recent log events in memory and serves them on a page that follows the
 * log as it is written, using the JDK's built-in HTTP server and server-sent events.
 * <p>
 * Configured by the appender's output URI, for example
 * <code>web://127.0.0.1:8080/?size=500</code>: the host and port to listen on and how
 * many events to keep. Each event is the text the appender's encoder produced, so the
 * page shows the same format as the console.
 * <p>
 * There is no authentication. Logs can hold anything an application logs, so listen on
 * localhost or a firewalled port.
 */
public final class WebLogOutput implements LogOutput {

	/**
	 * The output URI scheme.
	 */
	public static final String SCHEME = "web";

	private static final int DEFAULT_PORT = 8080;

	private static final int DEFAULT_SIZE = 500;

	private final URI uri;

	private final InetSocketAddress address;

	private final int size;

	/*
	 * Events are kept already encoded as the JSON the page receives, so a page that
	 * connects only copies strings.
	 */
	private final ArrayDeque<String> recent;

	private final List<BlockingQueue<String>> subscribers = new ArrayList<>();

	private final ReentrantLock lock = new ReentrantLock();

	/*
	 * Lets a page that reconnects tell a restarted application from a dropped connection.
	 */
	private final long instance = System.currentTimeMillis();

	private long sequence;

	private HttpServer server;

	private ExecutorService executor;

	private WebLogOutput(URI uri, InetSocketAddress address, int size) {
		this.uri = uri;
		this.address = address;
		this.size = size;
		this.recent = new ArrayDeque<>(size);
	}

	/**
	 * Creates the output from its URI. The server starts with the appender.
	 * @param uri for example <code>web://127.0.0.1:8080/?size=500</code>.
	 * @return output.
	 */
	public static WebLogOutput of(URI uri) {
		String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
		int port = uri.getPort() < 0 ? DEFAULT_PORT : uri.getPort();
		int size = DEFAULT_SIZE;
		String query = uri.getQuery();
		if (query != null) {
			for (String pair : query.split("&")) {
				if (pair.startsWith("size=")) {
					size = Integer.parseInt(pair.substring("size=".length()));
				}
				else if (!pair.isEmpty()) {
					throw new IllegalArgumentException("Unknown web output parameter. Expected size=N: " + pair);
				}
			}
		}
		if (size <= 0) {
			throw new IllegalArgumentException("size must be positive: " + size);
		}
		return new WebLogOutput(uri, new InetSocketAddress(host, port), size);
	}

	@Override
	public void start(LogConfig config) {
		try {
			server = HttpServer.create(address, 0);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not listen on " + address, e);
		}
		// A page holds its connection open, so give each one a cheap virtual thread.
		executor = Executors.newVirtualThreadPerTaskExecutor();
		server.setExecutor(executor);
		server.createContext("/", this::page);
		server.createContext("/events", this::events);
		server.start();
	}

	/**
	 * The address the page is served on.
	 * @return for example <code>http://127.0.0.1:8080/</code>.
	 */
	public URI pageUri() {
		var bound = server == null ? address : server.getAddress();
		return URI.create("http://" + bound.getHostString() + ":" + bound.getPort() + "/");
	}

	@Override
	public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
		String text = new String(bytes, off, len, StandardCharsets.UTF_8).stripTrailing();
		lock.lock();
		try {
			String json = json(++sequence, event.level(), text);
			if (recent.size() == size) {
				recent.removeFirst();
			}
			recent.addLast(json);
			for (var subscriber : subscribers) {
				// Never block logging on a slow page; it misses the event instead.
				subscriber.offer(json);
			}
		}
		finally {
			lock.unlock();
		}
	}

	private void page(HttpExchange exchange) throws IOException {
		try (exchange) {
			if (!exchange.getRequestURI().getPath().equals("/")) {
				exchange.sendResponseHeaders(404, -1);
				return;
			}
			byte[] page;
			try (InputStream in = Objects.requireNonNull(WebLogOutput.class.getResourceAsStream("weblog.html"))) {
				page = in.readAllBytes();
			}
			exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
			exchange.sendResponseHeaders(200, page.length);
			exchange.getResponseBody().write(page);
		}
	}

	/*
	 * Sends the events kept so far, then each new one as it is written, until the page
	 * goes away.
	 */
	private void events(HttpExchange exchange) throws IOException {
		var headers = exchange.getResponseHeaders();
		headers.set("Content-Type", "text/event-stream; charset=utf-8");
		headers.set("Cache-Control", "no-store");
		exchange.sendResponseHeaders(200, 0);
		var subscriber = new ArrayBlockingQueue<String>(size);
		List<String> backlog;
		lock.lock();
		try {
			backlog = List.copyOf(recent);
			subscribers.add(subscriber);
		}
		finally {
			lock.unlock();
		}
		try (exchange; OutputStream body = exchange.getResponseBody()) {
			send(body, "event: start\ndata: " + instance + "\n\n");
			for (String json : backlog) {
				send(body, "data: " + json + "\n\n");
			}
			body.flush();
			while (true) {
				String json = subscriber.poll(15, TimeUnit.SECONDS);
				// A comment every so often notices a closed page even when nothing is logged.
				send(body, json == null ? ": keep-alive\n\n" : "data: " + json + "\n\n");
				body.flush();
			}
		}
		catch (IOException e) {
			// The page was closed or reloaded.
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		finally {
			lock.lock();
			try {
				subscribers.remove(subscriber);
			}
			finally {
				lock.unlock();
			}
		}
	}

	private static void send(OutputStream body, String message) throws IOException {
		body.write(message.getBytes(StandardCharsets.UTF_8));
	}

	private static String json(long sequence, Level level, String text) {
		var json = new StringBuilder(text.length() + 48);
		json.append("{\"seq\":").append(sequence).append(",\"level\":\"").append(level.getName()).append("\",\"text\":\"");
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '"' -> json.append("\\\"");
				case '\\' -> json.append("\\\\");
				case '\n' -> json.append("\\n");
				case '\r' -> json.append("\\r");
				case '\t' -> json.append("\\t");
				default -> {
					if (c < 0x20) {
						json.append(String.format("\\u%04x", (int) c));
					}
					else {
						json.append(c);
					}
				}
			}
		}
		return json.append("\"}").toString();
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
		if (server != null) {
			server.stop(0);
		}
		if (executor != null) {
			// Wakes the threads still following the log.
			executor.shutdownNow();
		}
	}

	@Override
	public URI uri() {
		return uri;
	}

	@Override
	public OutputType type() {
		return OutputType.MEMORY;
	}

}
