package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * A container for the response of some sort of request or action. Logging is usually with
 * out a return value but there are certain scenarios where a return value is needed
 * particularly when doing health checks.
 *
 * @apiNote At the moment this class is largely an internal detail as extension points do
 * not create responses however {@link #status()} objects are created by extension points.
 * @see Status
 */
public sealed interface LogResponse {

	/**
	 * Configuration name of the component.
	 * @return name.
	 */
	public String name();

	/**
	 * Status of the response.
	 * @return status.
	 */
	public Status status();

	/**
	 * Component type.
	 * @return interface class.
	 */
	public Class<?> type();

	/**
	 * Log component status check.
	 */
	sealed interface Status {

		/**
		 * Level is used here to indicate the severity of the status.
		 * @return level.
		 */
		public System.Logger.Level level();

		/**
		 * Standard status.
		 */
		public enum StandardStatus implements Status {

			/**
			 * Nothing to report.
			 */
			IGNORED() {
				@Override
				public Level level() {
					return Level.DEBUG;
				}
			},
			/**
			 * OK.
			 */
			OK() {
				@Override
				public Level level() {
					return Level.INFO;
				}
			}

		}

		/**
		 * Creates an error status of a throwable.
		 * @param e throwable.
		 * @return error status.
		 */
		static ErrorStatus ofError(Throwable e) {
			return ErrorStatus.of(e);
		}

		/**
		 * Error status.
		 *
		 * @param message error message.
		 */
		record ErrorStatus(String message) implements Status {

			@Override
			public Level level() {
				return Level.ERROR;
			}

			static ErrorStatus of(ExecutionException e) {
				var cause = e.getCause();
				if (cause == null)
					cause = e;
				return of(cause);
			}

			static ErrorStatus of(Throwable e) {
				String message = Objects.requireNonNullElse(e.getMessage(), "unknown error");
				return new Status.ErrorStatus(message);
			}
		}

	}

}

/**
 * A marker interface for pluggable components in the logging system.
 *
 * @apiNote this interface is an internal detail.
 */
interface LogComponent {

}

record Response(Class<? extends LogComponent> type, String name, LogResponse.Status status) implements LogResponse {

}
