package io.jstach.rainbowgum.slf4j;

/**
 * Implemented by loggers that can report the {@link LogEventHandler} they would currently
 * dispatch to. It is read-only: it exists so {@link LocationAwareForwardingLogger} can
 * look up the current handler at each {@code log(...)} call instead of caching one at
 * construction time, which would go stale after a router swap through
 * {@code ReplaceableLogger.set(level, handler)}.
 */
interface HandlerSource {

	LogEventHandler currentHandler();

}
