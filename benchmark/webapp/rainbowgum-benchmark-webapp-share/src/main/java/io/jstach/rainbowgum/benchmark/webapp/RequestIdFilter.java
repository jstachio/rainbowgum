package io.jstach.rainbowgum.benchmark.webapp;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.MDC;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * Puts a request id in MDC for the lifetime of each request, matching the sort of
 * request-scoped diagnostic context a real application would set (correlation ids, tenant
 * ids, etc).
 */
public class RequestIdFilter implements Filter {

	/**
	 * MDC key used for the request id.
	 */
	public static final String REQUEST_ID_KEY = "requestId";

	/*
	 * A counter is used instead of a UUID so the filter itself does not add meaningful
	 * allocation/CPU overhead to the benchmark - we want the cost attributed to logging,
	 * not to id generation.
	 */
	private final AtomicLong counter = new AtomicLong();

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		MDC.put(REQUEST_ID_KEY, Long.toString(counter.incrementAndGet()));
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(REQUEST_ID_KEY);
		}
	}

}
