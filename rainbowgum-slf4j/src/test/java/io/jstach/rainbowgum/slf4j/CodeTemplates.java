package io.jstach.rainbowgum.slf4j;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import org.slf4j.event.Level;

import io.jstach.jstache.JStache;
import io.jstach.jstache.JStacheConfig;
import io.jstach.jstache.JStachePartial;
import io.jstach.jstache.JStachePartials;
import io.jstach.jstache.JStacheType;

@JStacheConfig(type = JStacheType.STACHE)
public class CodeTemplates {

	@JStache(
			template = """
					package io.jstach.rainbowgum.slf4j;

					import org.slf4j.Logger;
					import org.slf4j.Marker;
					import org.slf4j.event.Level;
					import org.slf4j.spi.LoggingEventBuilder;


					sealed interface LevelLogger extends BaseLogger, Logger {

						record OffLogger(String loggerName) implements LevelLogger {
							@Override
							public void handle(io.jstach.rainbowgum.LogEvent event) {
							}
						}

						public static LevelLogger of(Level level, String loggerName, io.jstach.rainbowgum.LogEventLogger appender ) {
							return switch(level) {
								{{#loggers}}
								case {{level.name}} -> new {{className}}(loggerName, appender);
								{{/loggers}}
							};
						}

						{{#loggers}}
						{{> logger }}
						{{/loggers}}
					}
					""")
	@JStachePartials(@JStachePartial(name = "logger", template = levelLoggerTemplate))
	public record ClassModel() {
		public List<LoggerModel> loggers() {
			return EnumSet.allOf(Level.class).stream().map(LoggerModel::of).toList();
		}
	}

	@JStache(template = levelLoggerTemplate)
	public record LoggerModel(LevelModel level) {
		public List<LevelModel> levels() {
			return EnumSet.allOf(Level.class).stream().map(lv -> new LevelModel(lv, level.level)).toList();
		}

		public String className() {
			return level.className();
		}

		public static LoggerModel of(Level level) {
			LevelModel m = new LevelModel(level, level);
			return new LoggerModel(m);
		}
	}

	public record LevelModel(Level level, Level selected) {
		public String enabledMethodName() {
			return "is" + capitalize(level) + "Enabled";
		}

		public String atName() {
			return "at" + capitalize(level);
		}

		static String capitalize(Level level) {
			return level.name().substring(0, 1).toUpperCase(Locale.ROOT)
					+ level.name().substring(1).toLowerCase(Locale.ROOT);
		}

		public String methodName() {
			return level.name().toLowerCase(Locale.ROOT);
		}

		public boolean isEnabled() {
			return level.toInt() >= selected.toInt();
		}

		public String className() {
			return capitalize(level) + "Logger";
		}

		public String name() {
			return level.name();
		}

	}

	public static final String levelLoggerTemplate = """
			record {{className}}(String loggerName, io.jstach.rainbowgum.LogEventLogger appender) implements LevelLogger {

				@Override
				public void handle(io.jstach.rainbowgum.LogEvent event) {
					appender.log(event);
				}

				@Override
				public LoggingEventBuilder {{level.atName}}() {
					return makeLoggingEventBuilder(Level.{{level.name}});
				}

				{{#levels}}
				@Override
				public boolean {{enabledMethodName}}() {
					{{#isEnabled}}
					return true;
					{{/isEnabled}}
					{{^isEnabled}}
					return false;
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(String msg) {
					{{#isEnabled}}
					handle(Level.{{name}}, msg);
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(String format, Object arg) {
					{{#isEnabled}}
					handle(Level.{{name}}, format, arg);
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(String format, Object arg1, Object arg2) {
					{{#isEnabled}}
					handle(Level.{{name}}, format, arg1, arg2);
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(String format, Object... arguments) {
					{{#isEnabled}}
					handleArray(Level.{{name}}, format, arguments);
					{{/isEnabled}}
				}

				@Override
				public boolean {{enabledMethodName}}(Marker marker) {
					{{#isEnabled}}
						return true;
					{{/isEnabled}}
					{{^isEnabled}}
						return false;
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(Marker marker, String msg) {
					{{#isEnabled}}
					handle(Level.{{name}}, msg);
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(Marker marker, String format, Object arg) {
					{{#isEnabled}}
					handle(Level.{{name}}, format, arg);
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(Marker marker, String format, Object arg1, Object arg2) {
					{{#isEnabled}}
					handle(Level.{{name}}, format, arg1, arg2);
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(Marker marker, String format, Object... argArray) {
					{{#isEnabled}}
					handleArray(Level.{{name}}, format, argArray);
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(Marker marker, String msg, Throwable t) {
					{{#isEnabled}}
					handle(Level.{{name}}, msg, t);
					{{/isEnabled}}
				}
				{{/levels}}
			}
			""";

	@JStache(template = changeLoggerTemplate)
	public record ChangeLoggerModel() {
		public List<LevelModel> levels() {
			return EnumSet.allOf(Level.class).stream().map(lv -> new LevelModel(lv, lv)).toList();
		}
	}

	public static final String changeLoggerTemplate = """

				{{#levels}}

				@Override
				public LoggingEventBuilder {{atName}}() {
					if ( {{enabledMethodName}}() ) {
						return makeLoggingEventBuilder(Level.{{level.name}});
					}
					else {
						return NOPLoggingEventBuilder.singleton();
					}
				}

				@Override
				public boolean {{enabledMethodName}}() {
					return this.level <= {{level.name}}_INT;
				}

				@Override
				public void {{methodName}}(String msg) {
					if ( {{enabledMethodName}}() ) {
						handle(Level.{{name}}, msg);
					}
				}

				@Override
				public void {{methodName}}(String format, Object arg) {
					if ( {{enabledMethodName}}() ) {
						handle(Level.{{name}}, format, arg);
					}
				}

				@Override
				public void {{methodName}}(String format, Object arg1, Object arg2) {
					if ( {{enabledMethodName}}() ) {
						handle(Level.{{name}}, format, arg1, arg2);
					}
				}

				@Override
				public void {{methodName}}(String format, Object... arguments) {
					if ( {{enabledMethodName}}() ) {
						handleArray(Level.{{name}}, format, arguments);
					}
				}

				@Override
				public boolean {{enabledMethodName}}(Marker marker) {
					return {{enabledMethodName}}();
				}

				@Override
				public void {{methodName}}(Marker marker, String msg) {
					if ( {{enabledMethodName}}() ) {
						handle(Level.{{name}}, msg);
					}
				}

				@Override
				public void {{methodName}}(Marker marker, String format, Object arg) {
					if ( {{enabledMethodName}}() ) {
						handle(Level.{{name}}, format, arg);
					}
				}

				@Override
				public void {{methodName}}(Marker marker, String format, Object arg1, Object arg2) {
					if ( {{enabledMethodName}}() ) {
						handle(Level.{{name}}, format, arg1, arg2);
					}
				}

				@Override
				public void {{methodName}}(Marker marker, String format, Object... argArray) {
					if ( {{enabledMethodName}}() ) {
						handleArray(Level.{{name}}, format, argArray);
					}
				}

				@Override
				public void {{methodName}}(Marker marker, String msg, Throwable t) {
					if ( {{enabledMethodName}}() ) {
						handle(Level.{{name}}, msg, t);
					}
				}
				{{/levels}}

			""";

	@JStache(template = forwardLoggerTemplate)
	public record ForwardLoggerModel() {
		public String delegate() {
			return "delegate()";
		}

		public List<LevelModel> levels() {
			return EnumSet.allOf(Level.class).stream().map(lv -> new LevelModel(lv, lv)).toList();
		}
	}

	public static final String forwardLoggerTemplate = """
			package io.jstach.rainbowgum.slf4j;

			import org.slf4j.Logger;
			import org.slf4j.Marker;

			/**
			 * A logger that forwards calls to the {@link #delegate()} logger.
			 */
			public interface ForwardingLogger extends Logger {

				/**
				 * The downstream logger to forward calls to.
				 * @return delegate.
				 */
				public Logger delegate();

				@Override
				default String getName() {
					return {{delegate}}.getName();
				}

				{{#levels}}

				@Override
				default boolean {{enabledMethodName}}() {
					return {{delegate}}.{{enabledMethodName}}();
				}

				@Override
				default void {{methodName}}(String msg) {
					{{delegate}}.{{methodName}}(msg);
				}

				@Override
				default void {{methodName}}(String format, Object arg) {
					{{delegate}}.{{methodName}}(format, arg);
				}

				@Override
				default void {{methodName}}(String format, Object arg1, Object arg2) {
					{{delegate}}.{{methodName}}(format, arg1, arg2);
				}

				@Override
				default void {{methodName}}(String format, Object... arguments) {
					{{delegate}}.{{methodName}}(format, arguments);
				}

				@Override
				default boolean {{enabledMethodName}}(Marker marker) {
					return {{delegate}}.{{enabledMethodName}}(marker);
				}

				@Override
				default void {{methodName}}(Marker marker, String msg) {
					{{delegate}}.{{methodName}}(marker, msg);
				}

				@Override
				default void {{methodName}}(Marker marker, String format, Object arg) {
					{{delegate}}.{{methodName}}(marker, format, arg);
				}

				@Override
				default void {{methodName}}(Marker marker, String format, Object arg1, Object arg2) {
					{{delegate}}.{{methodName}}(marker, format, arg1, arg2);
				}

				@Override
				default void {{methodName}}(Marker marker, String format, Object... argArray) {
					{{delegate}}.{{methodName}}(marker, format, argArray);
				}

				@Override
				default void {{methodName}}(Marker marker, String msg, Throwable t) {
					{{delegate}}.{{methodName}}(marker, msg, t);
				}
				{{/levels}}
			}
			""";

}
