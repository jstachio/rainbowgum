package io.jstach.rainbowgum.jfr;

import org.eclipse.jdt.annotation.Nullable;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * Base JFR event committed by {@link JfrLogOutput}, one concrete subclass per SLF4J /
 * {@link java.lang.System.Logger.Level}, so each level can be independently
 * enabled/disabled and thresholded through a Flight Recorder configuration ({@code .jfc})
 * the same way any other JFR event type can.
 * <p>
 * {@link #throwable} is a plain formatted stack trace string rather than JFR's own
 * {@code @StackTrace} capture: that JFR feature records the stack at the point
 * {@link Event#commit()} is called (i.e. inside this module), which is not useful here,
 * so it is disabled ({@link StackTrace @StackTrace(false)}) and the logged
 * {@link Throwable}, if any, is captured as a field instead.
 * <p>
 * MDC / key values are not carried as fields: JFR custom event fields are limited to
 * primitives, {@link String}, {@link Class} and {@link Thread}, so there is no direct
 * representation for an arbitrary map. File an issue if you need this.
 */
@Category("Rainbow Gum")
@StackTrace(false)
public abstract sealed class RainbowGumLogEvent extends Event {

	/**
	 * The already-formatted log message, or null if there was none.
	 */
	@Label("Message")
	public @Nullable String message;

	/**
	 * The logger name, or null if unavailable.
	 */
	@Label("Logger")
	public @Nullable String logger;

	/**
	 * The logged throwable formatted as a stack trace string, or null if none was logged.
	 */
	@Label("Throwable")
	public @Nullable String throwable;

	RainbowGumLogEvent() {
	}

	/**
	 * TRACE level event. Disabled by default ({@link Enabled @Enabled(false)}) since
	 * TRACE logging is typically too high volume to want on by default in a recording.
	 */
	@Name("io.jstach.rainbowgum.Trace")
	@Label("Trace")
	@Description("A TRACE level Rainbow Gum log event.")
	@Enabled(false)
	public static final class TraceEvent extends RainbowGumLogEvent {

		/**
		 * Created by {@link JfrLogOutput}.
		 */
		TraceEvent() {
		}

	}

	/**
	 * DEBUG level event. Disabled by default ({@link Enabled @Enabled(false)}) since
	 * DEBUG logging is typically too high volume to want on by default in a recording.
	 */
	@Name("io.jstach.rainbowgum.Debug")
	@Label("Debug")
	@Description("A DEBUG level Rainbow Gum log event.")
	@Enabled(false)
	public static final class DebugEvent extends RainbowGumLogEvent {

		/**
		 * Created by {@link JfrLogOutput}.
		 */
		DebugEvent() {
		}

	}

	/**
	 * INFO level event.
	 */
	@Name("io.jstach.rainbowgum.Info")
	@Label("Info")
	@Description("An INFO level Rainbow Gum log event.")
	public static final class InfoEvent extends RainbowGumLogEvent {

		/**
		 * Created by {@link JfrLogOutput}.
		 */
		InfoEvent() {
		}

	}

	/**
	 * WARN level event.
	 */
	@Name("io.jstach.rainbowgum.Warn")
	@Label("Warn")
	@Description("A WARN level Rainbow Gum log event.")
	public static final class WarnEvent extends RainbowGumLogEvent {

		/**
		 * Created by {@link JfrLogOutput}.
		 */
		WarnEvent() {
		}

	}

	/**
	 * ERROR level event.
	 */
	@Name("io.jstach.rainbowgum.Error")
	@Label("Error")
	@Description("An ERROR level Rainbow Gum log event.")
	public static final class ErrorEvent extends RainbowGumLogEvent {

		/**
		 * Created by {@link JfrLogOutput}.
		 */
		ErrorEvent() {
		}

	}

}
