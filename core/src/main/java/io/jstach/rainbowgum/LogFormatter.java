package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.time.Instant;

import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;

public interface LogFormatter {

	public void format(LogOutput output, LogEvent event);
	
	public interface LevelFormatter {
		String format(Level level);
		public static LevelFormatter of() {
			return DefaultLevelFormatter.INSTANT;
		}
	}
	
	public interface InstantFormatter {
		String format(Instant instant);
	}
	
	public interface ThrowableFormatter {
		void format(LogOutput output, Throwable throwable);
	}
	
}
enum DefaultThrowableFormatter implements ThrowableFormatter {
	INSTANT;
	@Override
	public void format(
			LogOutput output,
			Throwable throwable) {
		throwable.printStackTrace(output.asWriter());
	}
}
enum DefaultLevelFormatter implements LevelFormatter {
	INSTANT;

	@Override
	public String format(
			Level level) {
		return switch(level) {
		case DEBUG -> "DEBUG";
		case ALL -> "ERROR";
		case ERROR -> "ERROR";
		case INFO -> "INFO";
		case OFF -> "TRACE";
		case TRACE -> "TRACE";
		case WARNING -> "WARN";
		};
	}
	
}
