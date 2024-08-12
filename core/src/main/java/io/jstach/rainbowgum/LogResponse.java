package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.List;
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
	 * Log component status check. Note that trees or aggregates can be created with
	 * {@link AggregateStatus}.
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

		/**
		 * A Status of Statuses. The severity is the status with the highest severity if
		 * severity is not provided.
		 *
		 * @param status a list of statuses.
		 * @param level severity.
		 */
		record AggregateStatus(List<Status> status, Level level) implements Status {

			/**
			 * A Status of Statuses.
			 * @param status a list of statuses.
			 * @param level severity.
			 */
			public AggregateStatus {
				status = List.copyOf(status);
			}

			/**
			 * A Status of Statuses. The severity is the status with the highest severity.
			 * @param status a list of statuses.
			 */
			public AggregateStatus(List<Status> status) {
				this(status, level(status));
			}

			private static Level level(List<Status> status) {
				Level level = Level.ALL;
				for (var stat : status) {
					var current = stat.level();
					if (current.getSeverity() > level.getSeverity()) {
						level = current;
					}
				}
				return level;
			}
		}

		/**
		 * A status that has metric information.
		 */
		sealed interface MetricStatus extends Status {

			@Override
			default Level level() {
				return Level.INFO;
			}

		}

		/**
		 * A queue status for publishers.
		 *
		 * @param count current amount in queue.
		 * @param max the maximum size of the queue.
		 * @param level severity
		 */
		record QueueStatus(long count, long max, Level level) implements MetricStatus {

			/**
			 * A queue status for publishers. The severity will be warning if queue count
			 * is greater or equal to max.
			 * @param count current amount in queue.
			 * @param max the maximum size of the queue.
			 */
			public QueueStatus(long count, long max) {
				this(count, max, level(count, max));
			}

			private static Level level(long count, long max) {
				if (count >= max) {
					return Level.WARNING;
				}
				return Level.INFO;
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
