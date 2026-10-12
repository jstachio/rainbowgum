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
		var jfrEvent = newEvent(event.level());
		if (jfrEvent == null || !jfrEvent.isEnabled()) {
			return;
		}
		var message = new StringBuilder();
		JfrLogOutput.MESSAGE_FORMATTER.format(message, event);
		jfrEvent.message = message.toString();
		jfrEvent.logger = event.loggerName();
		var t = event.throwableOrNull();
		if (t != null) {
			var stackTrace = new StringBuilder();
			ThrowableFormatter.appendThrowable(stackTrace, t);
			jfrEvent.throwable = stackTrace.toString();
		}
		jfrEvent.commit();
	}

	/*
	 * Not a factory on RainbowGumAlertEvent itself: verifying a method of the event base
	 * class that returns its subclasses loads them while the base class is still being
	 * linked, and JFR fails to instrument an event class loaded then when a recording is
	 * already running (-XX:StartFlightRecording), leaving it uninstrumented or failing
	 * with a duplicate class definition.
	 */
	private static @Nullable RainbowGumAlertEvent newEvent(java.lang.System.Logger.Level level) {
		return switch (level) {
			case TRACE -> new RainbowGumAlertEvent.TraceEvent();
			case DEBUG -> new RainbowGumAlertEvent.DebugEvent();
			case INFO -> new RainbowGumAlertEvent.InfoEvent();
			case WARNING -> new RainbowGumAlertEvent.WarnEvent();
			case ERROR -> new RainbowGumAlertEvent.ErrorEvent();
			case ALL, OFF -> null;
		};
	}

}
