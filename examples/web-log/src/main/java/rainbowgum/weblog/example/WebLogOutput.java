package rainbowgum.weblog.example;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;

/**
 * Pushes each encoded event to the {@link LogFeed} and serves a page that follows the
 * feed. Configured by the appender's output URI, for example
 * <code>web://127.0.0.1:8080/</code>, the host and port to listen on.
 * <p>
 * The output holds nothing worth reaching for: the feed is the part that is safe to
 * share, so look that up instead.
 * <p>
 * There is no authentication. Logs can hold anything an application logs, so listen on
 * localhost or a firewalled port.
 */
final class WebLogOutput implements LogOutput {

	/**
	 * The output URI scheme.
	 */
	static final String SCHEME = "web";

	private static final int DEFAULT_PORT = 8080;

	private final URI uri;

	private final InetSocketAddress address;

	private final LogFeed feed;

	private LogFeedServer server;

	private WebLogOutput(URI uri, InetSocketAddress address, LogFeed feed) {
		this.uri = uri;
		this.address = address;
		this.feed = feed;
	}

	/*
	 * Wires the feed now, so a missing one fails when the appender is built rather than
	 * on the first event.
	 */
	static WebLogOutput of(URI uri, LogConfig config) {
		if (uri.getRawQuery() != null) {
			throw new IllegalArgumentException(
					"The web output takes only a host and port. Set " + LogFeed.SIZE_PROPERTY + " for the feed size.");
		}
		String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
		int port = uri.getPort() < 0 ? DEFAULT_PORT : uri.getPort();
		return new WebLogOutput(uri, new InetSocketAddress(host, port), LogFeed.find(config));
	}

	@Override
	public void start(LogConfig config) {
		try {
			server = LogFeedServer.start(feed, address);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not listen on " + address, e);
		}
	}

	@Override
	public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
		feed.publish(event.level(), new String(bytes, off, len, StandardCharsets.UTF_8).stripTrailing());
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
		if (server != null) {
			server.stop();
		}
	}

	@Override
	public URI uri() {
		return uri;
	}

	@Override
	public OutputType type() {
		return OutputType.NETWORK;
	}

}
