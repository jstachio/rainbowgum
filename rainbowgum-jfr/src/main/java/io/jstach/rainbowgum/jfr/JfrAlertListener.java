package io.jstach.rainbowgum.jfr;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogAlerts;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;
import io.jstach.rainbowgum.annotation.LogConfigurable;

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

	private final boolean enabled;

	/**
	 * Creates a JFR alert listener. Not gated on {@code enabled} itself - see
	 * {@link JfrAlertListener#enabled()} and {@link JfrAlertListenerConfigurator}, which
	 * builds this unconditionally and only then decides whether to register it.
	 * @param enabled whether this listener should actually be registered; see
	 * {@link JfrAlertListener#enabled()}. Default {@code false}.
	 * @return listener.
	 */
	@LogConfigurable(name = "JfrAlertListenerBuilder", prefix = "logging.jfr.alerts.")
	static JfrAlertListener of(@Nullable Boolean enabled) {
		return new JfrAlertListener(enabled != null && enabled);
	}

	private JfrAlertListener(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * Whether this listener should actually be registered with
	 * {@link LogAlerts#addListener(LogAlerts.Listener)}.
	 * @return enabled.
	 */
	public boolean enabled() {
		return enabled;
	}

	@Override
	public void onAlert(LogEvent event) {
		var jfrEvent = RainbowGumAlertEvent.of(event.level());
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

}
