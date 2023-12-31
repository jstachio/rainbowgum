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
	@JStachePartials(@JStachePartial(name = "logger", template = template))
	public record ClassModel() {
		public List<LoggerModel> loggers() {
			return EnumSet.allOf(Level.class).stream().map(LoggerModel::of).toList();
		}
	}

	@JStache(template = template)
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

	public static final String template = """
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

}
