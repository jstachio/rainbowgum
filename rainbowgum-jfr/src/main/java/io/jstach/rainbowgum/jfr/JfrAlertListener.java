package io.jstach.rainbowgum.jfr;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogAlerts;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.annotation.EnumAlias;
import io.jstach.rainbowgum.annotation.LogConfigurable;
import io.jstach.rainbowgum.annotation.LogConfigurable.ConvertParameter;

/**
 * A {@link LogAlerts.Listener} that commits each alert as its own
 * {@link RainbowGumAlertEvent} - deliberately a separate JFR event type family from
 * {@link JfrLogOutput}'s {@link RainbowGumLogEvent}, even though the shape (per-level
 * split, same fields) and rendering are the same; see {@link RainbowGumAlertEvent} for
 * why sharing one event type between alerts and application log lines was rejected.
 * <p>
 * Alerts are rare and already delivered synchronously one at a time (see
 * {@link LogAlerts#addListener(LogAlerts.Listener)}), so unlike {@link JfrLogOutput} this
 * renders the message directly with {@link JfrLogOutput#MESSAGE_FORMATTER} instead of
 * going through the buffered {@code LogEncoder}/{@code LogOutput} machinery meant for a
 * high volume appender.
 *
 * @see JfrAlertListenerBuilder
 * @see JfrAlertListenerConfigurator
 */
public final class JfrAlertListener implements LogAlerts.Listener {

	private final Mode mode;

	/**
	 * Whether alerts are committed as JFR events.
	 */
	public enum Mode {

		/**
		 * Alerts are not committed as JFR events (the default).
		 */
		@EnumAlias("false")
		OFF,
		/**
		 * Every alert is committed as a JFR event.
		 */
		@EnumAlias("true")
		ON;

		/**
		 * Parses a property value: {@code on}/{@code true} or {@code off}/{@code false},
		 * case insensitive.
		 * @param value property value.
		 * @return parsed value.
		 * @throws IllegalArgumentException if the value is not recognized.
		 */
		public static Mode parse(String value) {
			String v = value.strip();
			return switch (v.toLowerCase(Locale.ROOT)) {
				case "true" -> ON;
				case "false" -> OFF;
				default -> LogProperty.enumValue(Mode.class, v, "true", "false");
			};
		}

	}

	/**
	 * Creates a JFR alert listener. Building one does not register it; see
	 * {@link JfrAlertListenerConfigurator}.
	 * @param mode whether this listener should be registered; see
	 * {@link JfrAlertListener#mode()}. Default {@link JfrAlertListener.Mode#OFF}.
	 * @return listener.
	 */
	@LogConfigurable(name = "JfrAlertListenerBuilder", prefix = "logging.jfr.alerts.")
	static JfrAlertListener of(@ConvertParameter("parseMode") @Nullable Mode mode) {
		return new JfrAlertListener(mode == null ? Mode.OFF : mode);
	}

	static Mode parseMode(String value) {
		return Mode.parse(value);
	}

	private JfrAlertListener(Mode mode) {
		this.mode = mode;
	}

	/**
	 * Whether this listener should be registered with
	 * {@link LogAlerts#addListener(LogAlerts.Listener)}.
	 * @return mode.
	 */
	public Mode mode() {
		return mode;
	}

	@Override
	public void onAlert(LogEvent event) {
		switch (event.level()) {
			case TRACE -> emitTrace(event);
			case DEBUG -> emitDebug(event);
			case INFO -> emitInfo(event);
			case WARNING -> emitWarn(event);
			case ERROR -> emitError(event);
			case ALL, OFF -> {
			}
		}
	}

	/*
	 * Same shape as JfrLogOutput: one method per event type so a disabled event's
	 * allocation can be eliminated, and not on RainbowGumAlertEvent for the same class
	 * loading reason.
	 */
	private static void emitTrace(LogEvent event) {
		var e = new RainbowGumAlertEvent.TraceEvent();
		if (e.isEnabled()) {
			fill(e, event);
			e.commit();
		}
	}

	private static void emitDebug(LogEvent event) {
		var e = new RainbowGumAlertEvent.DebugEvent();
		if (e.isEnabled()) {
			fill(e, event);
			e.commit();
		}
	}

	private static void emitInfo(LogEvent event) {
		var e = new RainbowGumAlertEvent.InfoEvent();
		if (e.isEnabled()) {
			fill(e, event);
			e.commit();
		}
	}

	private static void emitWarn(LogEvent event) {
		var e = new RainbowGumAlertEvent.WarnEvent();
		if (e.isEnabled()) {
			fill(e, event);
			e.commit();
		}
	}

	private static void emitError(LogEvent event) {
		var e = new RainbowGumAlertEvent.ErrorEvent();
		if (e.isEnabled()) {
			fill(e, event);
			e.commit();
		}
	}

	private static void fill(RainbowGumAlertEvent e, LogEvent event) {
		var message = new StringBuilder();
		JfrLogOutput.MESSAGE_FORMATTER.format(message, event);
		e.message = message.toString();
		e.logger = event.loggerName();
		e.throwable = JfrLogOutput.stackTraceOrNull(event);
	}

}
