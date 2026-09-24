package io.jstach.rainbowgum.jfr;

import io.jstach.rainbowgum.LogAlerts;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;

/**
 * A {@link LogAlerts.Listener} that commits each alert as a JFR event (see
 * {@link RainbowGumLogEvent}), reusing the same per-level event types
 * {@link JfrLogOutput} uses for ordinary log events, so alerts (an appender failing to
 * write, an async publisher's queue overflowing, etc.) show up in a Flight Recording the
 * same way.
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

	/**
	 * Creates a JFR alert listener.
	 */
	public JfrAlertListener() {
	}

	@Override
	public void onAlert(LogEvent event) {
		var jfrEvent = RainbowGumLogEvent.of(event.level());
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
