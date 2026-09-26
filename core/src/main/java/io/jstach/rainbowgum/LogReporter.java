package io.jstach.rainbowgum;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Renders a diagnostic report of a bound {@link RainbowGum}: which sections are included
 * ({@link Section#VERSION}, {@link Section#COMPONENTS}, {@link Section#METRICS},
 * {@link Section#ALERTS}) depends on how the {@link Builder} that created this reporter
 * was configured.
 *
 * @apiNote The rendered output's format carries <strong>no compatibility guarantee
 * whatsoever</strong>. It is meant to be read by a human, or an agent, while setting
 * things up or debugging an incident, not parsed by anything expecting a stable schema,
 * and may change between any two releases including patch releases.
 */
public sealed interface LogReporter permits DefaultLogReporter {

	/**
	 * Writes the report for the given RainbowGum directly to the given appendable, rather
	 * than building the whole report in memory first, so a caller can stream straight
	 * into a {@link java.io.Writer}/response even while the system is in a degraded
	 * state.
	 * @param gum bound RainbowGum instance to report on.
	 * @param appendable destination.
	 * @throws IOException if the appendable throws.
	 */
	void report(RainbowGum gum, Appendable appendable) throws IOException;

	/**
	 * Writes the report to a {@link StringBuilder}, which never actually throws
	 * {@link IOException} in practice, so callers do not need to handle one.
	 * @param gum bound RainbowGum instance to report on.
	 * @param sb destination.
	 */
	default void report(RainbowGum gum, StringBuilder sb) {
		try {
			report(gum, (Appendable) sb);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Renders the report as a String.
	 * @param gum bound RainbowGum instance to report on.
	 * @return report.
	 */
	default String report(RainbowGum gum) {
		var sb = new StringBuilder();
		report(gum, sb);
		return sb.toString();
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
		 * Counters from {@link LogConfig#metrics()}.
		 */
		METRICS,
		/**
		 * Recent entries from {@link LogConfig#alerts()}, see
		 * {@link Builder#maxAlerts(int)}.
		 */
		ALERTS;

	}

	/**
	 * Creates a builder.
	 * @return builder.
	 */
	static Builder builder() {
		return new Builder();
	}

	/**
	 * Builds a {@link LogReporter}.
	 */
	final class Builder {

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
		 * Maximum number of most recent alerts to print when {@link Section#ALERTS} is
		 * included. Oldest-first entries beyond this count are omitted, not truncated mid
		 * list. Default is 5.
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
		 * included. Alerts are just {@link LogEvent}s (see {@link LogAlerts#dump()}), so
		 * any {@link LogFormatter} works here, e.g. one that omits the timestamp for a
		 * deterministic report. Default prints
		 * <code>[timestamp] LEVEL loggerName - message</code>.
		 * @param alertFormatter formatter for a single alert.
		 * @return this.
		 */
		public Builder alertFormatter(LogFormatter alertFormatter) {
			this.alertFormatter = Objects.requireNonNull(alertFormatter);
			return this;
		}

		/**
		 * Builds the reporter.
		 * @return reporter.
		 */
		public LogReporter build() {
			Set<Section> resolved = sections.isEmpty() ? DEFAULT_SECTIONS : EnumSet.copyOf(sections);
			return new DefaultLogReporter(resolved, maxAlerts, alertFormatter);
		}

	}

}

record DefaultLogReporter(Set<LogReporter.Section> sections, int maxAlerts,
		LogFormatter alertFormatter) implements LogReporter {

	/*
	 * Every one of these is already a named constant in LogProperties, printed as
	 * "key = value" rather than modeled as a new type, since none of these is a leaf on
	 * the component tree below: they are process wide context that colors how to read
	 * everything that follows.
	 */
	private static final List<String> GLOBAL_PROPERTY_KEYS = List.of(LogProperties.GLOBAL_CHANGE_PROPERTY,
			LogProperties.GLOBAL_QUEUE_LEVEL_PROPERTY, LogProperties.GLOBAL_QUEUE_ERROR_PROPERTY,
			LogProperties.GLOBAL_ANSI_DISABLE_PROPERTY, LogProperties.GLOBAL_APPENDER_REENTRANT_LOCK_PROPERTY,
			LogProperties.GLOBAL_THREADLOCAL_DISABLED_PROPERTY, LogProperties.GLOBAL_OPTIMIZE_PROPERTY);

	@Override
	public void report(RainbowGum gum, Appendable out) throws IOException {
		if (sections.contains(LogReporter.Section.VERSION)) {
			appendVersion(out);
		}
		var config = gum.config();
		if (sections.contains(LogReporter.Section.COMPONENTS)) {
			appendComponents(out, gum, config);
		}
		if (sections.contains(LogReporter.Section.METRICS)) {
			appendMetrics(out, config.metrics());
		}
		if (sections.contains(LogReporter.Section.ALERTS)) {
			appendAlerts(out, config.alerts());
		}
	}

	private void appendVersion(Appendable out) throws IOException {
		out.append("Rainbow Gum ").append(RainbowGumVersion.VERSION).append("\n\n");
	}

	private void appendComponents(Appendable out, RainbowGum gum, LogConfig config) throws IOException {
		appendGlobalProperties(out, config.properties());
		out.append("\n");
		for (var router : routersOf(gum.router())) {
			appendRouter(out, config, router);
		}
	}

	private void appendGlobalProperties(Appendable out, LogProperties properties) throws IOException {
		out.append("Global Properties:\n");
		for (String key : GLOBAL_PROPERTY_KEYS) {
			String value = properties.valueOrNull(key);
			out.append("  ").append(key).append(" = ").append(value == null ? "(unset)" : value).append("\n");
		}
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
			out.append(", uri=").append(uri.toString());
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
		for (var counter : metrics.counters()) {
			out.append("  ")
				.append(counter.name())
				.append(" = ")
				.append(Long.toString(counter.count()))
				.append(" (")
				.append(counter.level().toString())
				.append(")\n");
		}
		out.append("\n");
	}

	private void appendAlerts(Appendable out, LogAlerts alerts) throws IOException {
		var stats = alerts.stats();
		out.append("Alerts (total=")
			.append(Long.toString(stats.total()))
			.append(", capacity=")
			.append(Integer.toString(stats.capacity()))
			.append("):\n");
		var dump = alerts.dump();
		int show = Math.min(dump.size(), maxAlerts);
		StringBuilder line = new StringBuilder();
		for (var event : dump.subList(dump.size() - show, dump.size())) {
			line.setLength(0);
			alertFormatter.format(line, event);
			out.append("  ").append(line).append("\n");
		}
	}

}
