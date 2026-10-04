package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;

/**
 * A dynamic version of {@link LogEvent}'s static "<code>of</code>" factory methods - same
 * method shapes (level, message, ...), but {@link #timestamp()}, {@link #threadName()},
 * {@link #threadId()} and {@link #loggerName()} are resolved through overridable methods
 * instead of being passed in on every call/pulled from
 * {@link Instant#now()}/{@link Thread#currentThread()} inline the way those static
 * methods do. Those static methods are unaffected for now; this is a first step toward
 * eventually replacing them.
 * <p>
 * Not being able to override thread name/thread id/timestamp is a real limitation for
 * anything that needs deterministic events not tied to whichever real thread happens to
 * construct them - tests especially, but possibly other reasons later. A test-specific
 * implementation overriding those methods once gets an entire family of
 * event-construction methods that are deterministic, rather than needing to thread fixed
 * values through every call site.
 * <p>
 * Each arity of message argument has its own, distinctly-named method
 * ({@link #eventNoArg(Level, String, KeyValues, Throwable) eventNoArg}/
 * {@link #eventOneArg(Level, String, KeyValues, Object) eventOneArg}/
 * {@link #eventTwoArg(Level, String, KeyValues, Object, Object) eventTwoArg}/
 * {@link #eventArgs(Level, String, KeyValues, Object[], Throwable) eventArgs}) rather
 * than being overloaded under one name and disambiguated only by parameter shape - this
 * is meant to be the one place any facade implementation (SLF4J, JCL, JUL, ...) goes to
 * build events correctly, and overload resolution across five-plus differently-shaped
 * methods sharing a name reads confusingly at call sites once there are this many of
 * them.
 * <p>
 * This holds no mutable per-instance state - implementations are expected to be immutable
 * too. Implement it (or extend {@link #of(String)}'s returned instance's behavior by
 * writing your own implementation) and override individual methods to customize how
 * events get constructed.
 *
 * @apiNote a builder that in turn builds/configures a factory was considered but is not
 * included (yet) - implementing this directly covers today's need (mainly tests and
 * facade modules) with less machinery.
 */
public interface LogEventFactory {

	/**
	 * Creates a factory bound to the given logger name, otherwise using default behavior
	 * for {@link #timestamp()}, {@link #threadName()}, {@link #threadId()} and
	 * {@link #messageFormatter()}.
	 * @param loggerName logger name every event created by the returned factory will
	 * have.
	 * @return factory.
	 */
	public static LogEventFactory of(String loggerName) {
		return new DefaultLogEventFactory(loggerName);
	}

	/**
	 * Name of the logger every event created by this factory will have.
	 * @return logger name.
	 */
	String loggerName();

	/**
	 * Timestamp to use for the next event created by this factory. Default is
	 * {@link Instant#now()}.
	 * @return timestamp.
	 */
	default Instant timestamp() {
		return Instant.now();
	}

	/**
	 * Thread name to use for the next event created by this factory. Default is
	 * {@link Thread#getName() Thread.currentThread().getName()}.
	 * @return thread name.
	 * @apiNote this maybe empty and often is if virtual threads are used.
	 */
	default String threadName() {
		return Thread.currentThread().getName();
	}

	/**
	 * Thread id to use for the next event created by this factory. Default is
	 * {@link Thread#threadId() Thread.currentThread().threadId()}.
	 * @return thread id.
	 */
	default long threadId() {
		return Thread.currentThread().threadId();
	}

	/**
	 * Key values to use for the next event created by this factory when a caller does not
	 * supply an explicit {@link KeyValues}. Default is every {@link KeyValuesContributor}
	 * registered with the currently bound {@link RainbowGum} merged together (see
	 * {@link KeyValuesContributor#global(String...)}), which is empty if none are
	 * registered or no Rainbow Gum is bound.
	 * @return key values.
	 */
	default KeyValues defaultKeyValues() {
		return KeyValuesContributors.GLOBAL.keyValues();
	}

	/**
	 * Formatter to use for rendering a message when
	 * {@link LogEvent#formattedMessage(StringBuilder)} is called on events created by
	 * this factory that take arguments. Default is
	 * {@link LogMessageFormatter.StandardMessageFormatter#SLF4J}.
	 * @return message formatter.
	 * @apiNote a method rather than a parameter on the {@code eventXxx} methods below
	 * since this rarely changes per event - override it instead if a different formatter
	 * is needed.
	 */
	default LogMessageFormatter messageFormatter() {
		return LogMessageFormatter.StandardMessageFormatter.SLF4J;
	}

	/**
	 * Creates a log event whose message is already formatted (no arguments). Corresponds
	 * to
	 * {@link LogEvent#of(Instant, String, long, Level, String, String, KeyValues, Throwable)}.
	 * @param level the logging level.
	 * @param formattedMessage the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param throwable an exception if passed maybe <code>null</code>.
	 * @return event.
	 * @apiNote the message is already assumed to be formatted as no arguments are passed.
	 */
	default LogEvent eventNoArg(Level level, @Nullable String formattedMessage, KeyValues keyValues,
			@Nullable Throwable throwable) {
		return LogEvent.of(timestamp(), threadName(), threadId(), level, loggerName(), formattedMessage, keyValues,
				throwable);
	}

	/**
	 * Like {@link #eventNoArg(Level, String, KeyValues, Throwable)} but using
	 * {@link #defaultKeyValues()}.
	 * @param level the logging level.
	 * @param formattedMessage the unformatted message.
	 * @param throwable an exception if passed maybe <code>null</code>.
	 * @return event.
	 */
	default LogEvent eventNoArg(Level level, @Nullable String formattedMessage, @Nullable Throwable throwable) {
		return eventNoArg(level, formattedMessage, defaultKeyValues(), throwable);
	}

	/**
	 * Creates a log event with a single message argument. Corresponds to
	 * {@link LogEvent#ofOneArg(Instant, String, long, Level, String, String, KeyValues, LogMessageFormatter, Object)}.
	 * @param level the logging level.
	 * @param message the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param arg1 argument that will be passed to {@link #messageFormatter()}.
	 * @return event.
	 */
	default LogEvent eventOneArg(Level level, @Nullable String message, KeyValues keyValues, @Nullable Object arg1) {
		Instant timestamp = timestamp();
		String threadName = threadName();
		long threadId = threadId();
		String loggerName = loggerName();
		var messageFormatter = messageFormatter();
		return LogEvent.ofOneArg(timestamp, threadName, threadId, level, loggerName, message, keyValues,
				messageFormatter, arg1);
	}

	/**
	 * Like {@link #eventOneArg(Level, String, KeyValues, Object)} but using
	 * {@link #defaultKeyValues()}.
	 * @param level the logging level.
	 * @param message the unformatted message.
	 * @param arg1 argument that will be passed to {@link #messageFormatter()}.
	 * @return event.
	 */
	default LogEvent eventOneArg(Level level, @Nullable String message, @Nullable Object arg1) {
		return eventOneArg(level, message, defaultKeyValues(), arg1);
	}

	/**
	 * Creates a log event with two message arguments. Corresponds to
	 * {@link LogEvent#ofTwoArgs(Instant, String, long, Level, String, String, KeyValues, LogMessageFormatter, Object, Object)}.
	 * @param level the logging level.
	 * @param message the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param arg1 argument that will be passed to {@link #messageFormatter()}.
	 * @param arg2 argument that will be passed to {@link #messageFormatter()}.
	 * @return event.
	 */
	default LogEvent eventTwoArg(Level level, @Nullable String message, KeyValues keyValues, @Nullable Object arg1,
			@Nullable Object arg2) {
		Instant timestamp = timestamp();
		String threadName = threadName();
		long threadId = threadId();
		String loggerName = loggerName();
		var messageFormatter = messageFormatter();
		return LogEvent.ofTwoArgs(timestamp, threadName, threadId, level, loggerName, message, keyValues,
				messageFormatter, arg1, arg2);
	}

	/**
	 * Like {@link #eventTwoArg(Level, String, KeyValues, Object, Object)} but using
	 * {@link #defaultKeyValues()}.
	 * @param level the logging level.
	 * @param message the unformatted message.
	 * @param arg1 argument that will be passed to {@link #messageFormatter()}.
	 * @param arg2 argument that will be passed to {@link #messageFormatter()}.
	 * @return event.
	 */
	default LogEvent eventTwoArg(Level level, @Nullable String message, @Nullable Object arg1, @Nullable Object arg2) {
		return eventTwoArg(level, message, defaultKeyValues(), arg1, arg2);
	}

	/**
	 * Creates a log event with an array of message arguments. Corresponds to
	 * {@link LogEvent#ofAll(Instant, String, long, Level, String, String, KeyValues, Throwable, LogMessageFormatter, Object[])}.
	 * @param level the logging level.
	 * @param message the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param args an array of arguments that will be passed to
	 * {@link #messageFormatter()}. The contents maybe null elements but the array itself
	 * should not be null.
	 * @param throwable an exception if passed maybe <code>null</code>.
	 * @return event.
	 */
	default LogEvent eventArgs(Level level, @Nullable String message, KeyValues keyValues,
			@SuppressWarnings("exports") @Nullable Object @Nullable [] args, @Nullable Throwable throwable) {
		return LogEvent.ofAll(timestamp(), threadName(), threadId(), level, loggerName(), message, keyValues, throwable,
				messageFormatter(), args);
	}

	/**
	 * Like {@link #eventArgs(Level, String, KeyValues, Object[], Throwable)} with no
	 * throwable.
	 * @param level the logging level.
	 * @param message the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param args an array of arguments that will be passed to
	 * {@link #messageFormatter()}.
	 * @return event.
	 */
	default LogEvent eventArgs(Level level, @Nullable String message, KeyValues keyValues,
			@SuppressWarnings("exports") @Nullable Object @Nullable [] args) {
		return eventArgs(level, message, keyValues, args, null);
	}

	/**
	 * Like {@link #eventArgs(Level, String, KeyValues, Object[], Throwable)} but using
	 * {@link #defaultKeyValues()}.
	 * @param level the logging level.
	 * @param message the unformatted message.
	 * @param args an array of arguments that will be passed to
	 * {@link #messageFormatter()}.
	 * @param throwable an exception if passed maybe <code>null</code>.
	 * @return event.
	 */
	default LogEvent eventArgs(Level level, @Nullable String message,
			@SuppressWarnings("exports") @Nullable Object @Nullable [] args, @Nullable Throwable throwable) {
		return eventArgs(level, message, defaultKeyValues(), args, throwable);
	}

	/**
	 * Like {@link #eventArgs(Level, String, KeyValues, Object[], Throwable)} but using
	 * {@link #defaultKeyValues()} and no throwable.
	 * @param level the logging level.
	 * @param message the unformatted message.
	 * @param args an array of arguments that will be passed to
	 * {@link #messageFormatter()}.
	 * @return event.
	 */
	default LogEvent eventArgs(Level level, @Nullable String message,
			@SuppressWarnings("exports") @Nullable Object @Nullable [] args) {
		return eventArgs(level, message, defaultKeyValues(), args, null);
	}

	/**
	 * A source of ambient key values (MDC like context) for events whose logging API has
	 * no context of its own, such as JUL or {@link System.Logger}. Each store of ambient
	 * key values (SLF4J's MDC, JBoss Logging's MDC, Log4j's ThreadContext, scoped key
	 * values, ...) registers one in {@link LogConfig#serviceRegistry()} under a unique
	 * name, usually from a
	 * {@link io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator}:
	 * {@snippet :
	 * config.serviceRegistry().put(KeyValuesContributor.class, "myContext", () -> currentKeyValues());
	 * }
	 * <p>
	 * When more than one contributor is registered they are merged in order of
	 * registration name, so on a key collision the contributor whose name sorts last
	 * wins. A logging facade that has its own context store registers that store as a
	 * contributor, excludes it by name when resolving the rest, and merges its own on top
	 * so its own values win.
	 *
	 * @apiNote Rainbow Gum core deliberately has no API for putting key values into a
	 * context. Contributors only expose whatever context store the application already
	 * uses.
	 */
	@FunctionalInterface
	public interface KeyValuesContributor {

		/**
		 * Key values for an event being created on the current thread. Called once per
		 * event on the logging thread so it should be cheap. It must not throw or log and
		 * should return {@link KeyValues#of()} when there is nothing to contribute. The
		 * returned key values may be live (not a copy) as long as they are not modified
		 * while the event is being created.
		 * @return key values, never {@code null}.
		 */
		KeyValues keyValues();

		/**
		 * Combines the contributors currently registered in a service registry, skipping
		 * the excluded names. The registry is read once on this call, so contributors
		 * registered afterward are not included.
		 * @param registry where contributors are registered.
		 * @param excludedNames registration names to skip, usually the caller's own.
		 * @return combined contributor, which returns {@link KeyValues#of()} if nothing
		 * is registered.
		 */
		public static KeyValuesContributor of(ServiceRegistry registry, String... excludedNames) {
			return KeyValuesContributors.of(registry, Set.of(excludedNames));
		}

		/**
		 * Combines the contributors registered with whichever {@link RainbowGum} is bound
		 * globally at the time key values are requested, skipping the excluded names.
		 * Meant for logging facades that route through {@link LogRouter#global()} and so
		 * cannot hold a reference to a particular Rainbow Gum.
		 * @param excludedNames registration names to skip, usually the caller's own.
		 * @return contributor that follows the globally bound Rainbow Gum and returns
		 * {@link KeyValues#of()} while none is bound.
		 */
		public static KeyValuesContributor global(String... excludedNames) {
			if (excludedNames.length == 0) {
				return KeyValuesContributors.GLOBAL;
			}
			return new KeyValuesContributors.Global(Set.of(excludedNames));
		}

	}

}

record DefaultLogEventFactory(String loggerName) implements LogEventFactory {
}

final class KeyValuesContributors {

	static final Global GLOBAL = new Global(Set.of());

	private KeyValuesContributors() {
	}

	static KeyValuesContributor of(ServiceRegistry registry, Set<String> excludedNames) {
		var found = new TreeMap<String, KeyValuesContributor>();
		registry.forEach(KeyValuesContributor.class, (name, c) -> {
			if (!excludedNames.contains(name)) {
				found.put(name, c);
			}
		});
		var list = List.copyOf(found.values());
		return switch (list.size()) {
			case 0 -> Empty.INSTANCE;
			case 1 -> list.get(0);
			default -> new Composite(list.toArray(new KeyValuesContributor[0]));
		};
	}

	enum Empty implements KeyValuesContributor {

		INSTANCE;

		@Override
		public KeyValues keyValues() {
			return KeyValues.of();
		}

	}

	static final class Composite implements KeyValuesContributor {

		private final KeyValuesContributor[] contributors;

		Composite(KeyValuesContributor[] contributors) {
			this.contributors = contributors;
		}

		@Override
		public KeyValues keyValues() {
			KeyValues result = KeyValues.of();
			for (var c : contributors) {
				result = KeyValues.merge(result, c.keyValues());
			}
			return result;
		}

	}

	/*
	 * Resolves the registered contributors once per bound gum instead of per event. The
	 * registry is complete by the time a gum is bound since configurators run before.
	 */
	static final class Global implements KeyValuesContributor {

		private record Resolved(RainbowGum gum, KeyValuesContributor contributor) {
		}

		private final Set<String> excludedNames;

		private volatile @Nullable Resolved resolved;

		Global(Set<String> excludedNames) {
			this.excludedNames = excludedNames;
		}

		@Override
		@SuppressWarnings("ReferenceEquality") // identity of the bound gum is the point
		public KeyValues keyValues() {
			var gum = RainbowGumHolder.peek();
			if (gum == null) {
				return KeyValues.of();
			}
			var r = resolved;
			if (r == null || r.gum() != gum) {
				r = new Resolved(gum, of(gum.config().serviceRegistry(), excludedNames));
				resolved = r;
			}
			return r.contributor().keyValues();
		}

	}

}
