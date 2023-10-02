package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogRouter.ForwardingLogRouter;
import io.jstach.rainbowgum.LogRouter.RootRouter;
import io.jstach.rainbowgum.router.BlockingQueueRouter;
import io.jstach.rainbowgum.router.LockingSyncLogRouter;

public sealed interface LogRouter extends LogEventLogger, AutoCloseable {

	public boolean isEnabled(String loggerName, java.lang.System.Logger.Level level);

	void log(LogEvent event);

	default void start(LogConfig config) {
	}

	@Override
	public void close();

	public static RootRouter global() {
		return GlobalLogRouter.INSTANCE;
	}

	static void setRouter(RootRouter router) {
		if (GlobalLogRouter.INSTANCE == router) {
			throw new IllegalArgumentException();
		}
		GlobalLogRouter.INSTANCE.drain(router);
	}

	abstract class AbstractBuilder<T> extends LevelResolver.AbstractBuilder<T> {

		protected List<LogAppender> appenders = new ArrayList<>();

		protected AbstractBuilder() {
			super();
		}

		public T appenders(List<LogAppender> appenders) {
			this.appenders = appenders;
			return self();
		}

		public T appender(LogAppender appender) {
			this.appenders.add(appender);
			return self();
		}

		protected List<LogAppender> appenders() {
			return this.appenders;
		}

		protected abstract T self();

	}

	public sealed interface RootRouter extends LogRouter {

		void start(LogConfig config);

		public LevelResolver levelResolver();

		default void log(String loggerName, java.lang.System.Logger.Level level, String formattedMessage,
				@Nullable Throwable cause) {
			if (isEnabled(loggerName, level)) {
				LogEvent event = LogEvent.of(level, loggerName, formattedMessage, cause);
				log(event);
			}
		}

		default void log(String loggerName, java.lang.System.Logger.Level level, String message) {
			log(loggerName, level, message, null);
		}

		public static RootRouter of(List<? extends ChildLogRouter> routers, LevelResolver levelResolver) {

			if (routers.isEmpty()) {
				throw new IllegalArgumentException("atleast one router is required");
			}
			List<LogRouter> sorted = new ArrayList<>();
			/*
			 * We add the async routers first
			 */
			Set<ChildLogRouter> matched = Collections.newSetFromMap(new IdentityHashMap<ChildLogRouter, Boolean>());
			for (var r : routers) {
				if (r instanceof AsyncLogRouter a) {
					matched.add(a);
					sorted.add(a);
				}
			}
			for (var r : routers) {
				if (r instanceof SyncLogRouter a && !matched.contains(a)) {
					sorted.add(a);
				}
			}

			List<LevelResolver> resolvers = new ArrayList<>();
			routers.stream().map(ChildLogRouter::levelResolver).forEach(resolvers::add);
			resolvers.add(levelResolver);
			var globalLevelResolver = LevelResolver.of(resolvers);

			List<ChildLogRouter> wrappedRouters = new ArrayList<>();
			for (var router : routers) {
				wrappedRouters.add(ReLevelLogRouter.of(router, levelResolver));
			}

			ChildLogRouter[] array = wrappedRouters.toArray(new ChildLogRouter[] {});

			if (array.length == 1 && array[0].synchronous()) {
				return new SingleSyncRootRouter(array[0], globalLevelResolver);
			}
			return new CompositeLogRouter(array, globalLevelResolver);
		}

	}

	public sealed interface ChildLogRouter extends LogRouter {

		LevelResolver levelResolver();

		public boolean synchronous();

	}

	public non-sealed interface SyncLogRouter extends ChildLogRouter {

		public static Builder builder() {
			return new Builder();
		}

		default boolean synchronous() {
			return true;
		}

		public static class Builder extends AbstractBuilder<Builder> {

			private Builder() {
				super();
			}

			@Override
			protected Builder self() {
				return this;
			}

			public SyncLogRouter build() {

				return new LockingSyncLogRouter(LogAppender.of(appenders), buildLevelResolver());
			}

		}

	}

	public non-sealed interface AsyncLogRouter extends ChildLogRouter {

		@Override
		public void start(LogConfig config);

		@Override
		default boolean synchronous() {
			return false;
		}

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder extends AbstractBuilder<Builder> {

			private int bufferSize = 1024;

			private Builder() {
				super();
			}

			public Builder bufferSize(int bufferSize) {
				this.bufferSize = bufferSize;
				return this;
			}

			public AsyncLogRouter build() {
				return BlockingQueueRouter.of(LogAppender.of(appenders), buildLevelResolver(), bufferSize);
			}

			@Override
			protected Builder self() {
				return this;
			}

		}

	}

	sealed interface ForwardingLogRouter extends ChildLogRouter permits ReLevelLogRouter {

	}

}

/*
 * TODO revisit this. We use this shitty wrapper because the router does not have the
 * config yet to do proper level resolution.
 */
final class ReLevelLogRouter implements ForwardingLogRouter {

	private final ChildLogRouter router;

	private final LevelResolver levelResolver;

	private ReLevelLogRouter(ChildLogRouter router, LevelResolver levelResolver) {
		super();
		this.router = router;
		this.levelResolver = levelResolver;
	}

	static ReLevelLogRouter of(ChildLogRouter router, LevelResolver levelResolver) {
		var resolver = LevelResolver.builder()
			.levelResolver(router.levelResolver())
			.levelResolver(levelResolver)
			.build();
		return new ReLevelLogRouter(router, resolver);
	}

	public boolean synchronous() {
		return router.synchronous();
	}

	@Override
	public LevelResolver levelResolver() {
		return levelResolver;
	}

	@Override
	public boolean isEnabled(String loggerName, Level level) {
		return levelResolver.isEnabled(loggerName, level);
	}

	@Override
	public void log(LogEvent event) {
		router.log(event);
	}

	@Override
	public void close() {
		router.close();
	}

}

record SingleSyncRootRouter(ChildLogRouter router, LevelResolver levelResolver) implements RootRouter {

	@Override
	public boolean isEnabled(String loggerName, Level level) {
		return levelResolver.isEnabled(loggerName, level);
	}

	@Override
	public void log(LogEvent event) {
		if (router.isEnabled(event.loggerName(), event.level())) {
			router.log(event);
		}
	}

	@Override
	public void start(LogConfig config) {
		router.start(config);
	}

	@Override
	public void close() {
		router.close();
	}

}

record CompositeLogRouter(ChildLogRouter[] routers, LevelResolver levelResolver) implements RootRouter {

	@Override
	public boolean isEnabled(String loggerName, Level level) {
		return levelResolver.isEnabled(loggerName, level);
	}

	@Override
	public void log(LogEvent event) {
		LogEvent frozen = null;
		for (var r : routers) {
			if (r.isEnabled(event.loggerName(), event.level())) {
				if (frozen != null) {
					r.log(frozen);

				}
				else if (!r.synchronous()) {
					if (frozen == null) {
						frozen = event.freeze();
					}
					r.log(frozen);
				}
				else {
					r.log(event);
				}
			}
		}

	}

	@Override
	public void start(LogConfig config) {
		for (var r : routers) {
			r.start(config);
		}
	}

	@Override
	public void close() {
		for (var r : routers) {
			r.close();
		}
	}
}

enum FailsafeAppender implements LogAppender {

	INSTANCE;

	@Override
	public void append(LogEvent event) {
		if (event.level().compareTo(Level.ERROR) >= 0) {
			event.formattedMessage(System.err);
			var throwable = event.throwable();
			if (throwable != null) {
				throwable.printStackTrace(System.err);
			}
		}
	}

}

enum GlobalLogRouter implements RootRouter {

	INSTANCE;

	private final ConcurrentLinkedQueue<LogEvent> events = new ConcurrentLinkedQueue<>();

	private static final LevelResolver INFO_RESOLVER = LevelResolver.of(Level.INFO);

	private volatile @Nullable RootRouter delegate = null;

	private final ReentrantLock drainLock = new ReentrantLock();

	static boolean isShutdownEvent(String loggerName, java.lang.System.Logger.Level level) {
		return Boolean.parseBoolean(System.getProperty(Defaults.SHUTDOWN)) && matchesShutdown(loggerName, level);
	}

	private static boolean matchesShutdown(String loggerName, java.lang.System.Logger.Level level) {
		return loggerName.equals(Defaults.SHUTDOWN);
	}

	@Override
	public LevelResolver levelResolver() {
		RootRouter d = delegate;
		if (d != null) {
			return d.levelResolver();
		}
		return INFO_RESOLVER;
	}

	@Override
	public boolean isEnabled(String loggerName, Level level) {
		LogRouter d = delegate;
		if (d != null) {
			return d.isEnabled(loggerName, level);
		}
		return INFO_RESOLVER.isEnabled(loggerName, level);
	}

	public void log(String loggerName, java.lang.System.Logger.Level level, String message, @Nullable Throwable cause) {
		RootRouter d = delegate;
		if (d != null) {
			d.log(loggerName, level, message, cause);
			if (isShutdownEvent(loggerName, level)) {
				d.close();
			}
		}
		else {
			var event = LogEvent.of(level, loggerName, message, cause);
			events.add(event);
			if (event.level().getSeverity() >= Level.ERROR.getSeverity()) {
				Errors.error(event);
			}
		}
	}

	public void drain(RootRouter delegate) {
		drainLock.lock();
		try {
			this.delegate = Objects.requireNonNull(delegate);
			LogEvent e;
			while ((e = this.events.poll()) != null) {
				if (delegate.isEnabled(e.loggerName(), e.level())) {
					delegate.log(e);
				}
			}
		}
		finally {
			drainLock.unlock();
		}
	}

	@Override
	public void log(LogEvent event) {
		LogRouter d = delegate;
		if (d != null) {
			if (d.isEnabled(event.loggerName(), event.level())) {
				d.log(event);
			}
		}
		else {
			events.add(event);
		}
	}

	@Override
	public void start(LogConfig config) {
	}

	@Override
	public void close() {
	}

}
