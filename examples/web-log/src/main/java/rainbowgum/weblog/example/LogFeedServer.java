package rainbowgum.weblog.example;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Serves a page that follows a {@link LogFeed}, using the JDK's built-in HTTP server and
 * server-sent events. It only reads the feed, never an output.
 */
final class LogFeedServer {

	private static final Duration KEEP_ALIVE = Duration.ofSeconds(15);

	private final LogFeed feed;

	private final HttpServer server;

	private final ExecutorService executor;

	/*
	 * Lets a page that reconnects tell a restarted application from a dropped connection.
	 */
	private final long instance = System.currentTimeMillis();

	private LogFeedServer(LogFeed feed, HttpServer server, ExecutorService executor) {
		this.feed = feed;
		this.server = server;
		this.executor = executor;
	}

	static LogFeedServer start(LogFeed feed, InetSocketAddress address) throws IOException {
		var server = HttpServer.create(address, 0);
		// A page holds its connection open, so give each one a cheap virtual thread.
		var executor = Executors.newVirtualThreadPerTaskExecutor();
		var s = new LogFeedServer(feed, server, executor);
		server.setExecutor(executor);
		server.createContext("/", s::page);
		server.createContext("/events", s::events);
		server.start();
		return s;
	}

	void stop() {
		server.stop(0);
		// Wakes the threads still following the feed.
		executor.shutdownNow();
	}

	private void page(HttpExchange exchange) throws IOException {
		try (exchange) {
			if (!exchange.getRequestURI().getPath().equals("/")) {
				exchange.sendResponseHeaders(404, -1);
				return;
			}
			byte[] page;
			try (InputStream in = Objects.requireNonNull(LogFeedServer.class.getResourceAsStream("weblog.html"))) {
				page = in.readAllBytes();
			}
			exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
			exchange.sendResponseHeaders(200, page.length);
			exchange.getResponseBody().write(page);
		}
	}

	/*
	 * Sends the entries the feed holds, then each new one, until the page goes away.
	 */
	private void events(HttpExchange exchange) throws IOException {
		var headers = exchange.getResponseHeaders();
		headers.set("Content-Type", "text/event-stream; charset=utf-8");
		headers.set("Cache-Control", "no-store");
		exchange.sendResponseHeaders(200, 0);
		try (exchange; var subscription = feed.subscribe(); OutputStream body = exchange.getResponseBody()) {
			send(body, "event: start\ndata: " + instance + "\n\n");
			for (var entry : subscription.backlog()) {
				send(body, data(entry));
			}
			body.flush();
			while (!subscription.isClosed()) {
				var entry = subscription.poll(KEEP_ALIVE);
				// A comment every so often notices a closed page even when nothing is logged.
				send(body, entry == null ? ": keep-alive\n\n" : data(entry));
				body.flush();
			}
		}
		catch (IOException e) {
			// The page was closed or reloaded.
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static void send(OutputStream body, String message) throws IOException {
		body.write(message.getBytes(StandardCharsets.UTF_8));
	}

	private static String data(LogFeed.Entry entry) {
		String text = entry.text();
		var json = new StringBuilder(text.length() + 56);
		json.append("data: {\"seq\":")
			.append(entry.sequence())
			.append(",\"level\":\"")
			.append(entry.level().getName())
			.append("\",\"text\":\"");
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
		return json.append("\"}\n\n").toString();
	}

}
