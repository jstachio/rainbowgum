package io.jstach.rainbowgum.systemlogger;

import static java.util.Objects.requireNonNullElse;

import java.lang.System.Logger;
import java.util.ResourceBundle;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogRouter;
import io.jstach.rainbowgum.jul.SystemLoggerQueueJULHandler;
import io.jstach.svc.ServiceProvider;

@ServiceProvider(System.LoggerFinder.class)
public class SystemLoggingFactory extends System.LoggerFinder {

	public SystemLoggingFactory() {
		SystemLoggerQueueJULHandler.install();
	}

	@Override
	public Logger getLogger(String name, Module module) {
		return new TinyLogger(name);
	}

	private static class TinyLogger implements System.Logger {

		private final String name;

		public TinyLogger(String name) {
			super();
			this.name = name;
		}

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public boolean isLoggable(Level level) {
			return LogRouter.global().isEnabled(name, level);
		}

		@Override
		public void log(Level level, @Nullable ResourceBundle bundle, @Nullable String msg,
				@Nullable Throwable thrown) {
			String message = requireNonNullElse(msg, "");
			LogRouter.global().log(name, level, message, thrown);
			;
		}

		@Override
		public void log(Level level, @Nullable ResourceBundle bundle, @Nullable String format,
				@Nullable Object... params) {
			String message = requireNonNullElse(format, "");
			LogRouter.global().log(name, level, message, null);
		}

	}

}
