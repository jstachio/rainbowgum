package io.jstach.rainbowgum.benchmark.micronaut;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.MDC;

import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;

/**
 * Puts a request id in MDC for the lifetime of each request, matching the sort of
 * request-scoped diagnostic context a real application would set (correlation ids, tenant
 * ids, etc). Uses Micronaut's simplified filter method style (introduced in Micronaut 4)
 * rather than implementing {@code HttpServerFilter} directly with reactive types.
 */
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
public class RequestIdFilter {

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

	/**
	 * For Micronaut.
	 */
	public RequestIdFilter() {
	}

	@RequestFilter
	void onRequest() {
		MDC.put(REQUEST_ID_KEY, Long.toString(counter.incrementAndGet()));
	}

	@ResponseFilter
	void onResponse() {
		MDC.remove(REQUEST_ID_KEY);
	}

}
