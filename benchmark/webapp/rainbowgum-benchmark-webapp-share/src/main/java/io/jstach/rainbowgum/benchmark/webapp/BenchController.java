package io.jstach.rainbowgum.benchmark.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * A deliberately simple REST endpoint meant to stand in for a real request handler: a
 * fixed, non-branching sequence of log statements (the same work every request, across
 * every logging backend, for a clean comparison) plus a small amount of non-logging work
 * (JSON serialization of the response) so the endpoint resembles a real one rather than a
 * pure logging micro-benchmark.
 */
@RestController
public class BenchController {

	private static final Logger log = LoggerFactory.getLogger(BenchController.class);

	@GetMapping("/api/greet/{name}")
	public Greeting greet(@PathVariable("name") String name) {
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

		return new Greeting("hello, " + name, MDC.get(RequestIdFilter.REQUEST_ID_KEY));
	}

	/**
	 * Response body.
	 *
	 * @param message greeting message.
	 * @param requestId the request id that was in MDC for this request.
	 */
	public record Greeting(String message, String requestId) {
	}

}
