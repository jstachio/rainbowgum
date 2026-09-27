package io.jstach.rainbowgum;

/**
 * Identifies which logging facade a logger name passed to
 * {@link LogConfig.LoggerRegistry#registerLoggerName(LoggerAPI, String)} came through.
 *
 * @apiNote currently informational only: {@link LogConfig.LoggerRegistry} does not yet do
 * anything different based on which {@link LoggerAPI} a name was registered with.
 */
public sealed interface LoggerAPI permits LoggerAPI.Standard {

	/**
	 * The logging facades RainbowGum ships an implementation for.
	 */
	enum Standard implements LoggerAPI {

		/**
		 * {@code rainbowgum-slf4j}.
		 */
		SLF4J,
		/**
		 * {@code rainbowgum-systemlogger} ({@link java.lang.System.Logger}).
		 */
		SYSTEM_LOGGER,
		/**
		 * {@code rainbowgum-log4j2} (Log4j2's own API, not an SLF4J binding).
		 */
		LOG4J2,
		/**
		 * {@code rainbowgum-jul-logmanager} ({@code java.util.logging}).
		 */
		JUL,
		/**
		 * {@code rainbowgum-jboss-logging}.
		 */
		JBOSS_LOGGING,
		/**
		 * {@code rainbowgum-avaje-config} ({@code io.avaje.applog.AppLog}).
		 */
		AVAJE_APPLOG,
		/**
		 * A facade not represented by another constant of this enum.
		 */
		UNSUPPORTED;

	}

}
