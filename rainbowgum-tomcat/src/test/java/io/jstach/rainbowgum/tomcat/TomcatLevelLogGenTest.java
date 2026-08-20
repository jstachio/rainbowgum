package io.jstach.rainbowgum.tomcat;

import java.lang.System.Logger.Level;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;

// This was used just to generate TomcatLevelLog
class TomcatLevelLogGenTest {

	@Test
	void test() {

		StringBuilder sb = new StringBuilder();

		sb.append("""
				package io.jstach.rainbowgum.tomcat;

				import java.lang.System.Logger.Level;

				import org.apache.juli.logging.Log;
				import org.eclipse.jdt.annotation.Nullable;

				import io.jstach.rainbowgum.LevelResolver;
				import io.jstach.rainbowgum.LogEvent;
				import io.jstach.rainbowgum.LogRouter;

				interface TomcatLevelLog extends Log {

					String loggerName();

					LogRouter router();

					default void log(Level level, @Nullable Object obj) {
						log(level, obj, null);
					}

					default void log(Level level, @Nullable Object obj, @Nullable Throwable t) {
						level = LevelResolver.normalizeLevel(level);
						String loggerName = loggerName();
						var route = router().route(loggerName, level);
						if (route.isEnabled()) {
							String formattedMessage = obj == null ? "" : obj.toString();
							LogEvent event = LogEvent.of(level, loggerName, formattedMessage, t);
							route.log(event);
						}
					}
				""");

		for (Level level : Level.values()) {
			if (level == Level.ALL) {
				continue;
			}
			sb.append("""

					record {{Level}}LevelLog(String loggerName, LogRouter router) implements TomcatLevelLog {
					""".replace("{{Level}}", levelCapitalName(level)));
			for (Level methodLevel : Level.values()) {
				if (methodLevel == Level.OFF || methodLevel == Level.ALL) {
					continue;
				}
				boolean enabled = methodLevel.compareTo(level) >= 0;
				sb.append("""
						@Override
						public boolean is{{Level}}Enabled() {
							return {{enabled}};
						}
						""".replace("{{Level}}", levelCapitalName(methodLevel)).replace("{{enabled}}", "" + enabled));
			}
			sb.append("""
					@Override
					public boolean isFatalEnabled() {
						return isErrorEnabled();
					}
					""");
			for (Level methodLevel : Level.values()) {
				if (methodLevel == Level.OFF || methodLevel == Level.ALL) {
					continue;
				}
				boolean enabled = methodLevel.compareTo(level) >= 0;
				if (enabled) {
					sb.append("""
							@Override
							public void {{level}}(@Nullable Object message) {
								log(Level.{{LEVEL}}, message);
							}

							@Override
							public void {{level}}(@Nullable Object message, @Nullable Throwable throwable) {
								log(Level.{{LEVEL}}, message);
							}

							""".replace("{{level}}", logMethodName(methodLevel))
						.replace("{{LEVEL}}", methodLevel.toString()));
				}
				else {
					sb.append("""
							@Override
							public void {{level}}(@Nullable Object message) {
							}

							@Override
							public void {{level}}(@Nullable Object message, @Nullable Throwable throwable) {
							}

							""".replace("{{level}}", logMethodName(methodLevel))
						.replace("{{LEVEL}}", methodLevel.toString()));
				}
			}
			sb.append("""
					@Override
					public void fatal(Object message) {
						error(message);
					}

					@Override
					public void fatal(Object message, Throwable t) {
						error(message, t);

					}

									""");
			sb.append("\n}");
		}
		sb.append("\n}");

		System.out.println(sb.toString());

	}

	static @Nullable String logMethodName(Level level) {
		return switch (level) {
			case DEBUG -> "debug";
			case ALL -> throw new UnsupportedOperationException("Unimplemented case: " + level);
			case ERROR -> "error";
			case INFO -> "info";
			case OFF -> "off";
			case TRACE -> "trace";
			case WARNING -> "warn";
			default -> throw new IllegalArgumentException("Unexpected value: " + level);

		};
	}

	static @Nullable String levelCapitalName(Level level) {
		return switch (level) {
			case DEBUG -> "Debug";
			case ALL -> throw new UnsupportedOperationException("Unimplemented case: " + level);
			case ERROR -> "Error";
			case INFO -> "Info";
			case OFF -> "Off";
			case TRACE -> "Trace";
			case WARNING -> "Warn";
			default -> throw new IllegalArgumentException("Unexpected value: " + level);

		};
	}

}
