package io.jstach.rainbowgum;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.jspecify.annotations.Nullable;

/**
 * Renders a diagnostic report of a {@link LogConfig} and, once one is built from it, its
 * {@link RainbowGum}. Each config has its own reporter, see {@link LogConfig#reporter()}.
 * Which sections are included ({@link Section#VERSION}, {@link Section#COMPONENTS},
 * {@link Section#METRICS}, {@link Section#ALERTS}, ...) is chosen per report with
 * {@link Options}.
 * <p>
 * Components that read a property worth seeing in every report add its key with
 * {@link #addProperty(Class, String)}, and {@link Section#COMPONENTS} lists those keys
 * with their current values and the packages that added them.
 *
 * @apiNote The rendered output's format carries <strong>no compatibility guarantee
 * whatsoever</strong>. It is meant to be read by a human, or an agent, while setting
 * things up or debugging an incident, not parsed by anything expecting a stable schema,
 * and may change between any two releases including patch releases.
 */
public sealed interface LogReporter permits DefaultLogReporter {

	/**
	 * Adds a property key whose current value is included in every report, attributed to
	 * the package of the given class. Adding the same key from the same package again has
	 * no effect; adding it from another package adds that package to the key's
	 * attribution.
	 * @param source class reading the property.
	 * @param key full property key.
	 */
	void addProperty(Class<?> source, String key);

	/**
	 * Writes the report directly to the given appendable, rather than building the whole
	 * report in memory first, so a caller can stream straight into a
	 * {@link java.io.Writer}/response even while the system is in a degraded state.
	 * Before a {@link RainbowGum} is built from the config, {@link Section#COMPONENTS}
	 * has no routers to list.
	 * @param options which sections to include and how.
	 * @param appendable destination.
	 * @throws IOException if the appendable throws.
	 */
	void report(Options options, Appendable appendable) throws IOException;

	/**
	 * Writes the report to a {@link StringBuilder}, which never actually throws
	 * {@link IOException} in practice, so callers do not need to handle one.
	 * @param options which sections to include and how.
	 * @param sb destination.
	 */
	default void report(Options options, StringBuilder sb) {
		try {
			report(options, (Appendable) sb);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Renders the report as a String.
	 * @param options which sections to include and how.
	 * @return report.
	 */
	default String report(Options options) {
		var sb = new StringBuilder();
		report(options, sb);
		return sb.toString();
	}

	/**
	 * Renders the report with the default options as a String.
	 * @return report.
	 * @see Options.Builder#build()
	 */
	default String report() {
		return report(Options.DEFAULT);
	}

	/**
	 * Implemented by a component that wants to contribute its own description to a report
	 * instead of falling back to just its class name. Entirely optional: nothing in this
	 * codebase or a custom {@link LogOutput}/{@link LogEncoder} implementation is
	 * required to implement this.
	 *
	 * @apiNote a {@link LogOutput} implementing this does <strong>not</strong> get the
	 * same "no overlapping calls" guarantee {@link LogOutput}'s other methods are
	 * promised: a report can be requested from an unrelated thread (e.g. an operator
	 * pulling diagnostics) at any time, including while the appender is concurrently mid
	 * write/flush/reopen on its own thread. {@link #report(Appendable)} must therefore be
	 * safe to call concurrently with itself and with every other {@link LogOutput}
	 * method, unlike the rest of that interface's contract.
	 */
	interface Reportable {

		/**
		 * Returns a concise description of a component. A {@link Reportable} supplies its
		 * own description; ordinary named classes use their canonical name, while
		 * anonymous, synthetic, and local classes use the supplied fallback.
		 * @param component component to describe.
		 * @param fallback description for a component without a stable class name.
		 * @return component description.
		 */
		static String toString(Object component, String fallback) {
			if (component instanceof Reportable reportable) {
				var out = new StringBuilder();
				try {
					reportable.report(out);
				}
				catch (IOException e) {
					return fallback;
				}
				return out.toString();
			}
			var type = component.getClass();
			if (type.isAnonymousClass() || type.isLocalClass() || type.isSynthetic()) {
				return fallback;
			}
			var name = type.getCanonicalName();
			return name != null ? name : fallback;
		}

		/**
		 * Writes this component's own description directly to the given appendable.
		 * @param out destination.
		 * @throws IOException if the appendable throws.
		 */
		void report(Appendable out) throws IOException;

	}

	/**
	 * A section of the report that can be independently turned on or off.
	 */
	enum Section {

		/**
		 * Rainbow Gum's own version, printed first if included.
		 */
		VERSION,
		/**
		 * The wired component tree: active global properties, then each router's
		 * publisher, appenders, outputs, and encoders.
		 */
		COMPONENTS,
		/**
		 * Metrics from {@link LogConfig#metrics()}.
		 */
		METRICS,
		/**
		 * Recent entries from {@link LogConfig#alerts()}, see
		 * {@link Options.Builder#maxAlerts(int)}.
		 */
		ALERTS,
		/**
		 * Logger names registered via
		 * {@link LogConfig.LoggerRegistry#registerLoggerName(LoggerAPI, String)}, each
		 * with its resolved level, see {@link Options.Builder#loggerLevels(boolean)}.
		 */
		LOGGERS,
		/**
		 * The distinct set of {@link LoggerAPI}s registered via
		 * {@link LogConfig.LoggerRegistry#registerLoggerName(LoggerAPI, String)}, e.g.
		 * seeing both {@code SLF4J} and {@code JUL} means both facades are in active use
		 * (not just on the classpath).
		 */
		FACADES;

	}

	/**
	 * Which sections a report includes and how they are rendered.
	 */
	final class Options {

		/*
		 * The default sections and settings.
		 */
		static final Options DEFAULT = builder().build();

		final Set<Section> sections;

		final int maxAlerts;

		final LogFormatter alertFormatter;

		final boolean loggerLevels;

		private Options(Set<Section> sections, int maxAlerts, LogFormatter alertFormatter, boolean loggerLevels) {
			this.sections = sections;
			this.maxAlerts = maxAlerts;
			this.alertFormatter = alertFormatter;
			this.loggerLevels = loggerLevels;
		}

		/**
		 * Creates a builder.
		 * @return builder.
		 */
		public static Builder builder() {
			return new Builder();
		}

		/**
		 * Options for a report of only the given section with otherwise default settings.
		 * @param section section to include.
		 * @return options.
		 */
		public static Options of(Section section) {
			return builder().section(section).build();
		}

		/**
		 * Builds {@link Options}.
		 */
		public static final class Builder {

			private static final Set<Section> DEFAULT_SECTIONS = EnumSet.of(Section.VERSION, Section.COMPONENTS);

			private static final LogFormatter DEFAULT_ALERT_FORMATTER = LogFormatter.builder()
				.text("[")
				.timeStamp()
				.text("] ")
				.level()
				.text(" ")
				.loggerName()
				.text(" - ")
				.message()
				.build();

			private final EnumSet<Section> sections = EnumSet.noneOf(Section.class);

			private int maxAlerts = 5;

			private LogFormatter alertFormatter = DEFAULT_ALERT_FORMATTER;

			private boolean loggerLevels = true;

			private Builder() {
			}

			/**
			 * Replaces the set of sections to print. An empty set is treated the same as
			 * never calling this method at all: the default sections
			 * ({@link Section#VERSION}, {@link Section#COMPONENTS}) are used.
			 * @param sections sections to print.
			 * @return this.
			 */
			public Builder sections(Set<Section> sections) {
				this.sections.clear();
				this.sections.addAll(sections);
				return this;
			}

			/**
			 * Adds a single section to print, in addition to any already added.
			 * @param section section to print.
			 * @return this.
			 */
			public Builder section(Section section) {
				this.sections.add(section);
				return this;
			}

			/**
			 * Maximum number of most recent alerts to print when {@link Section#ALERTS}
			 * is included. Oldest-first entries beyond this count are omitted, not
			 * truncated mid list. Default is 5.
			 * @param maxAlerts maximum alerts to print, zero or greater.
			 * @return this.
			 */
			public Builder maxAlerts(int maxAlerts) {
				if (maxAlerts < 0) {
					throw new IllegalArgumentException("maxAlerts must be zero or greater: " + maxAlerts);
				}
				this.maxAlerts = maxAlerts;
				return this;
			}

			/**
			 * Formatter used to render each alert entry when {@link Section#ALERTS} is
			 * included. Alerts are just {@link LogEvent}s (see {@link LogAlerts#dump()}),
			 * so any {@link LogFormatter} works here, e.g. one that omits the timestamp
			 * for a deterministic report. Default prints
			 * <code>[timestamp] LEVEL loggerName - message</code>.
			 * @param alertFormatter formatter for a single alert.
			 * @return this.
			 */
			public Builder alertFormatter(LogFormatter alertFormatter) {
				this.alertFormatter = Objects.requireNonNull(alertFormatter);
				return this;
			}

			/**
			 * Whether to resolve and print each logger's level, via
			 * {@link LogConfig#levelResolver()}, next to its name when
			 * {@link Section#LOGGERS} is included. Default is <code>true</code>.
			 * @param loggerLevels false to print just the bare logger names.
			 * @return this.
			 */
			public Builder loggerLevels(boolean loggerLevels) {
				this.loggerLevels = loggerLevels;
				return this;
			}

			/**
			 * Builds the options. With no sections added the default sections
			 * ({@link Section#VERSION}, {@link Section#COMPONENTS}) are used.
			 * @return options.
			 */
			public Options build() {
				Set<Section> resolved = sections.isEmpty() ? DEFAULT_SECTIONS : EnumSet.copyOf(sections);
				return new Options(resolved, maxAlerts, alertFormatter, loggerLevels);
			}

		}

	}

}

abstract sealed class DefaultLogReporter implements LogReporter permits DefaultLogConfig.Reporter {

	/*
	 * Written while the config and its RainbowGum are built, read by reports from any
	 * thread.
	 */
	private final ConcurrentSkipListMap<String, Set<String>> properties = new ConcurrentSkipListMap<>();

	private volatile LogRouter.@Nullable RootRouter router;

	abstract LogConfig config();

	/*
	 * Called once the RainbowGum built from the config has its router.
	 */
	void bind(LogRouter.RootRouter router) {
		this.router = router;
	}

	@Override
	public void addProperty(Class<?> source, String key) {
		Objects.requireNonNull(key);
		/*
		 * The package, not the module name, so the report is the same on the class path
		 * and the module path.
		 */
		properties.computeIfAbsent(key, k -> new ConcurrentSkipListSet<>()).add(source.getPackageName());
	}

	/*
	 * Every section is separated from the next by exactly one blank line, added here
	 * rather than left to each appendXxx method to manage its own leading/trailing
	 * newline: that previously meant whether a blank line actually showed up between two
	 * sections was an accident of which of the two happened to append one (VERSION and
	 * METRICS did, COMPONENTS/ALERTS/LOGGERS did not), so e.g. COMPONENTS immediately
	 * followed by METRICS printed with no separating blank line at all.
	 */
	@Override
	public void report(Options options, Appendable out) throws IOException {
		var sections = options.sections;
		boolean first = true;
		if (sections.contains(LogReporter.Section.VERSION)) {
			first = appendSeparator(out, first);
			appendVersion(out);
		}
		var config = config();
		if (sections.contains(LogReporter.Section.COMPONENTS)) {
			first = appendSeparator(out, first);
			appendComponents(out, config);
		}
		if (sections.contains(LogReporter.Section.METRICS)) {
			first = appendSeparator(out, first);
			appendMetrics(out, config.metrics());
		}
		if (sections.contains(LogReporter.Section.ALERTS)) {
			first = appendSeparator(out, first);
			appendAlerts(out, config.alerts(), options);
		}
		if (sections.contains(LogReporter.Section.LOGGERS)) {
			first = appendSeparator(out, first);
			appendLoggers(out, config, options);
		}
		if (sections.contains(LogReporter.Section.FACADES)) {
			first = appendSeparator(out, first);
			appendFacades(out, config);
		}
	}

	private static boolean appendSeparator(Appendable out, boolean first) throws IOException {
		if (!first) {
			out.append("\n");
		}
		return false;
	}

	private void appendVersion(Appendable out) throws IOException {
		out.append("Rainbow Gum ").append(RainbowGumVersion.VERSION).append("\n");
	}

	private void appendComponents(Appendable out, LogConfig config) throws IOException {
		appendProperties(out, config.properties());
		appendGlobals(out, config.globals());
		out.append("Debug mode: ").append(config.debugMode().toString()).append("\n");
		out.append("\n");
		var root = this.router;
		if (root == null) {
			out.append("(no RainbowGum built from this config yet)\n");
			return;
		}
		for (var router : routersOf(root)) {
			appendRouter(out, config, router);
		}
	}

	private void appendProperties(Appendable out, LogProperties properties) throws IOException {
		out.append("Properties: ");
		LogPropertiesReporting.describe(out, properties);
		out.append("\n");
		out.append("Reported Properties:\n");
		for (var entry : this.properties.entrySet()) {
			String key = entry.getKey();
			String value = properties.valueOrNull(key);
			out.append("  ")
				.append(key)
				.append(" = ")
				.append(value == null ? "(unset)" : value)
				.append(" (")
				.append(String.join(", ", entry.getValue()))
				.append(")\n");
		}
	}

	/*
	 * Resolved values, so a default set through LogConfig.Builder shows too.
	 */
	private static void appendGlobals(Appendable out, LogConfig.Globals globals) throws IOException {
		out.append("Globals:\n");
		appendGlobal(out, LogProperties.CHANGE_MODE_PROPERTY, globals.changeMode());
		appendGlobal(out, LogProperties.GLOBAL_ANSI_MODE_PROPERTY, globals.ansiMode());
		appendGlobal(out, LogProperties.GLOBAL_APPENDER_LOCK_PROPERTY, globals.appenderLock());
		appendGlobal(out, LogProperties.GLOBAL_THREAD_LOCAL_PROPERTY, globals.threadLocal());
		appendGlobal(out, LogProperties.GLOBAL_OPTIMIZE_PROPERTY, globals.optimize());
		var keyValues = new TreeSet<String>();
		globals.keyValuesDisabled().forEach(s -> keyValues.add(s.toString()));
		appendGlobal(out, LogProperties.KEY_VALUES_DISABLED_PROPERTY,
				keyValues.isEmpty() ? "(none)" : String.join(",", keyValues));
	}

	private static void appendGlobal(Appendable out, String key, Object value) throws IOException {
		out.append("  ").append(key).append(" = ").append(String.valueOf(value)).append("\n");
	}

	private static List<LogRouter.Router> routersOf(LogRouter.RootRouter root) {
		return switch (root) {
			case SingleRootRouter s -> List.of(s.router());
			case CompositeLogRouter c -> List.of(c.routers());
			/*
			 * Neither of these carries a real, fully bound set of named routers: both are
			 * pre-bind/queueing states (GlobalLogRouter.INSTANCE is the "nothing bound
			 * yet" default, delegating to a QueueEventsRouter until a real RainbowGum
			 * binds). report(RainbowGum, ...) is only ever called with an already-bound
			 * instance, so these are unreachable in practice, not a real gap.
			 */
			case QueueEventsRouter q -> List.of();
			case GlobalLogRouter g -> List.of();
		};
	}

	private void appendRouter(Appendable out, LogConfig config, LogRouter.Router router) throws IOException {
		String name = router instanceof SimpleRouter sr ? sr.name() : router.getClass().getSimpleName();
		out.append("Router: ").append(name).append("\n");
		appendPublisher(out, config, name, router.publisher());
	}

	private void appendPublisher(Appendable out, LogConfig config, String routeName, LogPublisher publisher)
			throws IOException {
		out.append("  Publisher: ")
			.append(publisher.getClass().getSimpleName())
			.append(publisher.synchronous() ? " (synchronous)" : " (asynchronous)")
			.append("\n");
		/*
		 * LogRouter.Router.Builder.build() registers the Appenders it hands to the
		 * publisher factory under this same route name (see there), so this works for any
		 * publisher, current or future, that uses Appenders.asList()/asSingle() the
		 * ordinary way, with no cooperation required from the publisher itself.
		 * resolvedOrNull() is null only if the publisher never called either one, which
		 * means it does not use the appenders this ordinary way at all.
		 */
		var apps = config.serviceRegistry().findOrNull(LogAppender.Appenders.class, routeName);
		var resolved = apps == null ? null : apps.resolvedOrNull();
		if (resolved == null) {
			/*
			 * Reportable is the escape hatch for a publisher that does something unusual
			 * and does not go through Appenders normally at all, same as
			 * appendOutput/appendEncoder.
			 */
			if (publisher instanceof Reportable r) {
				out.append("    ");
				r.report(out);
				out.append("\n");
			}
			else {
				out.append("    (no appender information available for ")
					.append(publisher.getClass().getSimpleName())
					.append(")\n");
			}
			return;
		}
		for (var appender : resolved) {
			for (var direct : directAppendersOf(appender)) {
				appendAppender(out, direct);
			}
		}
	}

	private static List<DirectLogAppender> directAppendersOf(LogAppender appender) {
		return switch (appender) {
			case CompositeLogAppender c -> List.of(c.appenders());
			case DirectLogAppender d -> List.of(d);
			default -> List.of();
		};
	}

	private void appendAppender(Appendable out, DirectLogAppender appender) throws IOException {
		out.append("    Appender: ").append(appender.name()).append("\n");
		out.append("      Type: ").append(appender.getClass().getSimpleName()).append("\n");
		if (appender instanceof AbstractLogAppender a) {
			out.append("      Flags: ").append(a.flags.toString()).append("\n");
		}
		appendOutput(out, appender.output());
		appendEncoder(out, appender.encoder());
	}

	private void appendOutput(Appendable out, LogOutput output) throws IOException {
		out.append("      Output: ")
			.append(output.getClass().getSimpleName())
			.append(" (type=")
			.append(output.type().toString());
		URI uri = uriOrNull(output);
		if (uri != null) {
			out.append(", uri=").append(LogRedactor.redactUri(uri));
		}
		if (output instanceof Reportable r) {
			out.append(", ");
			r.report(out);
		}
		out.append(")\n");
	}

	private static @Nullable URI uriOrNull(LogOutput output) {
		try {
			return output.uri();
		}
		catch (UnsupportedOperationException e) {
			return null;
		}
	}

	private void appendEncoder(Appendable out, LogEncoder encoder) throws IOException {
		/*
		 * No accessor on LogEncoder itself for describing its own configuration beyond
		 * its concrete class: Object#toString() is not a safe fallback since most
		 * implementations never override it. A component that wants to contribute more
		 * can implement Reportable instead, entirely opt-in: see FormatterEncoder for the
		 * one built-in example.
		 */
		out.append("      Encoder: ").append(encoder.getClass().getSimpleName());
		if (encoder instanceof Reportable r) {
			out.append(" (");
			r.report(out);
			out.append(")");
		}
		out.append("\n");
	}

	private void appendMetrics(Appendable out, LogMetrics metrics) throws IOException {
		out.append("Metrics:\n");
		for (var metric : metrics.snapshot()) {
			out.append("  ")
				.append(metric.name())
				.append(" = ")
				.append(Long.toString(metric.value()))
				.append(" (")
				.append(metric.level().toString())
				.append(")\n");
		}
	}

	private void appendAlerts(Appendable out, LogAlerts alerts, Options options) throws IOException {
		var stats = alerts.stats();
		out.append("Alerts (total=")
			.append(Long.toString(stats.total()))
			.append(", capacity=")
			.append(Integer.toString(stats.capacity()))
			.append("):\n");
		var dump = alerts.dump();
		int show = Math.min(dump.size(), options.maxAlerts);
		StringBuilder line = new StringBuilder();
		for (var event : dump.subList(dump.size() - show, dump.size())) {
			line.setLength(0);
			options.alertFormatter.format(line, event);
			out.append("  ").append(line).append("\n");
		}
	}

	private void appendLoggers(Appendable out, LogConfig config, Options options) throws IOException {
		out.append("Loggers:\n");
		/*
		 * DefaultLoggerRegistry is the only implementation (LogConfig.LoggerRegistry
		 * deliberately does not expose the accumulated names itself, see its javadoc);
		 * downcasting here is the same pattern DefaultLogConfig's own constructor uses to
		 * reach DefaultLogAlerts#addInternalListener.
		 */
		var registry = (DefaultLoggerRegistry) config.loggerRegistry();
		for (String name : new TreeSet<>(registry.loggerNames())) {
			out.append("  ").append(name);
			if (options.loggerLevels) {
				out.append(" = ").append(config.levelResolver().resolveLevel(name).toString());
			}
			out.append("\n");
		}
	}

	private void appendFacades(Appendable out, LogConfig config) throws IOException {
		out.append("Facades:\n");
		var registry = (DefaultLoggerRegistry) config.loggerRegistry();
		var apis = new TreeSet<LoggerAPI>(Comparator.comparing(Object::toString));
		apis.addAll(registry.loggerAPIs());
		for (var api : apis) {
			out.append("  ").append(api.toString()).append("\n");
		}
	}

}
