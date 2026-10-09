package io.jstach.rainbowgum.benchmark.nativeimage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * A deliberately simple request handler meant to stand in for a real one: a fixed,
 * non-branching sequence of log statements (the same work every request, across every
 * logging backend, for a clean comparison), matching {@code BenchController} from the
 * Spring Boot webapp benchmark ({@code feature/webapp-benchmark}) - same log calls, same
 * MDC request id, just dispatched by {@link com.sun.net.httpserver.HttpServer} instead of
 * Spring MVC, and a plain text response instead of JSON (nothing here needs a
 * serialization library).
 * <p>
 * Handles {@code GET /greet/<name>}; the path segment after the last {@code /} is the
 * name.
 */
public final class BenchHandler implements HttpHandler {

	private static final Logger log = LoggerFactory.getLogger(BenchHandler.class);

	/**
	 * MDC key used for the request id, matching a real application's request-scoped
	 * diagnostic context (correlation ids, tenant ids, etc).
	 */
	public static final String REQUEST_ID_KEY = "requestId";

	/*
	 * A counter is used instead of a UUID so id generation does not add meaningful
	 * allocation/CPU overhead of its own - the cost being measured should be attributable
	 * to logging, not to id generation.
	 */
	private final AtomicLong counter = new AtomicLong();

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		String name = path.substring(path.lastIndexOf('/') + 1);
		MDC.put(REQUEST_ID_KEY, Long.toString(counter.incrementAndGet()));
		try {
			String body = greet(name);
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
		}
		finally {
			MDC.remove(REQUEST_ID_KEY);
			exchange.close();
		}
	}

	private String greet(String name) {
		log.info("received request name={}", name);
		log.info("validating input length={}", name.length());

		long step1 = name.hashCode() * 31L;
		log.info("processing business logic step=1 value={}", step1);

		long step2 = step1 ^ name.length();
		log.info("processing business logic step=2 value={}", step2);

		/*
		 * Exercises the "is this level enabled" fast path: real applications have far
		 * more debug statements than ones that actually fire, and frameworks differ in
		 * how cheap a disabled check is.
		 */
		log.debug("debug details name={} length={} step1={} step2={}", name, name.length(), step1, step2);

		log.info("returning response status=200");

		return "hello, " + name;
	}

}
