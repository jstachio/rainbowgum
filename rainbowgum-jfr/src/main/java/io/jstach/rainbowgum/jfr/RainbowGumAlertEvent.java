package io.jstach.rainbowgum.jfr;

import org.jspecify.annotations.Nullable;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * Base JFR event committed by {@link JfrAlertListener}, one concrete subclass per SLF4J /
 * {@link java.lang.System.Logger.Level}, deliberately a <strong>separate</strong> JFR
 * event type family from {@link RainbowGumLogEvent} (same field shape and per-level
 * split, but its own {@code @Name}s) even though both are committed the same way and
 * carry the same fields: an alert (an appender failing to write, an async publisher's
 * queue overflowing, etc.) is not an application log line, and merging the two under one
 * event name would make it impossible to tell them apart in a recording - e.g. filtering
 * {@code jfr print --events Error} would return both genuine application errors and
 * RainbowGum's own internal alerts with no way to distinguish them.
 * <p>
 * See {@link RainbowGumLogEvent} for the field/annotation rationale - identical here.
 */
@Category({ "Rainbow Gum", "Alert" })
@StackTrace(false)
public abstract sealed class RainbowGumAlertEvent extends Event {

	/**
	 * The already-formatted alert message, or null if there was none.
	 */
	@Label("Message")
	public @Nullable String message;

	/**
	 * The logger name, or null if unavailable.
	 */
	@Label("Logger")
	public @Nullable String logger;

	/**
	 * The alert's throwable formatted as a stack trace string, or null if none was
	 * logged.
	 */
	@Label("Throwable")
	public @Nullable String throwable;

	RainbowGumAlertEvent() {
	}

	/**
	 * TRACE level alert. Disabled by default ({@link Enabled @Enabled(false)}), same
	 * volume reasoning as {@link RainbowGumLogEvent.TraceEvent}.
	 */
	@Name("io.jstach.rainbowgum.alert.AlertTrace")
	@Label("Alert (Trace)")
	@Description("A TRACE level Rainbow Gum alert.")
	@Enabled(false)
	public static final class TraceEvent extends RainbowGumAlertEvent {

		/**
		 * Created by {@link JfrAlertListener}.
		 */
		TraceEvent() {
		}

	}

	/**
	 * DEBUG level alert. Disabled by default ({@link Enabled @Enabled(false)}), same
	 * volume reasoning as {@link RainbowGumLogEvent.DebugEvent}.
	 */
	@Name("io.jstach.rainbowgum.alert.AlertDebug")
	@Label("Alert (Debug)")
	@Description("A DEBUG level Rainbow Gum alert.")
	@Enabled(false)
	public static final class DebugEvent extends RainbowGumAlertEvent {

		/**
		 * Created by {@link JfrAlertListener}.
		 */
		DebugEvent() {
		}

	}

	/**
	 * INFO level alert.
	 */
	@Name("io.jstach.rainbowgum.alert.AlertInfo")
	@Label("Alert (Info)")
	@Description("An INFO level Rainbow Gum alert.")
	public static final class InfoEvent extends RainbowGumAlertEvent {

		/**
		 * Created by {@link JfrAlertListener}.
		 */
		InfoEvent() {
		}

	}

	/**
	 * WARN level alert.
	 */
	@Name("io.jstach.rainbowgum.alert.AlertWarn")
	@Label("Alert (Warn)")
	@Description("A WARN level Rainbow Gum alert.")
	public static final class WarnEvent extends RainbowGumAlertEvent {

		/**
		 * Created by {@link JfrAlertListener}.
		 */
		WarnEvent() {
		}

	}

	/**
	 * ERROR level alert.
	 */
	@Name("io.jstach.rainbowgum.alert.AlertError")
	@Label("Alert (Error)")
	@Description("An ERROR level Rainbow Gum alert.")
	public static final class ErrorEvent extends RainbowGumAlertEvent {

		/**
		 * Created by {@link JfrAlertListener}.
		 */
		ErrorEvent() {
		}

	}

}
