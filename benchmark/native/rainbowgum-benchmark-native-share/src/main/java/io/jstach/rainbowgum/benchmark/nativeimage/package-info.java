/**
 * Shared log workload and JDK {@code com.sun.net.httpserver.HttpServer} bootstrap for the
 * GraalVM native image benchmark apps - the same request-handling code path regardless of
 * which logging backend a given app module wires up, so only the logging backend differs
 * between them.
 */
@org.jspecify.annotations.NullMarked
package io.jstach.rainbowgum.benchmark.nativeimage;
