package io.jstach.rainbowgum;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * A container for the response of some sort of request or action. Logging is usually with
 * out a return value but there are certain scenarios where a return value is needed.
 *
 * @apiNote At the moment this class is largely an internal detail.
 * @see Status
 */
public interface LogResponse {

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
	 * Output Status check.
	 */
	sealed interface Status {

		/**
		 * Standard status.
		 */
		public enum StandardStatus implements Status {

			/**
			 * Nothing to report.
			 */
			IGNORED,
			/**
			 * OK.
			 */
			OK;

		}

		/**
		 * Error status.
		 *
		 * @param message error message.
		 */
		record ErrorStatus(String message) implements Status {

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

record Response(String name, LogResponse.Status status) implements LogResponse {

}
