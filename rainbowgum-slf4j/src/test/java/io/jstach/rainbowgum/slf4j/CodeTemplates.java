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

					@SuppressWarnings("exports")
					sealed interface LevelLogger extends BaseLogger, Logger {

						record OffLogger(String loggerName) implements LevelLogger {
							@Override
							public void handle(io.jstach.rainbowgum.LogEvent event) {
							}
							@Override
							public io.jstach.rainbowgum.slf4j.RainbowGumMDCAdapter mdc() {
								return null;
							}
						}

						public static LevelLogger of(Level level, String loggerName, io.jstach.rainbowgum.LogEventLogger appender, io.jstach.rainbowgum.slf4j.RainbowGumMDCAdapter mdc) {
							return switch(level) {
								{{#loggers}}
								case {{level.name}} -> new {{className}}(loggerName, appender, mdc);
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
			record {{className}}(String loggerName, io.jstach.rainbowgum.LogEventLogger appender, io.jstach.rainbowgum.slf4j.RainbowGumMDCAdapter mdc) implements LevelLogger {

				@Override
				public void handle(io.jstach.rainbowgum.LogEvent event) {
					appender.log(event);
				}

				{{#levels}}

				{{#isEnabled}}
				@Override
				public LoggingEventBuilder {{atName}}() {
					return makeLoggingEventBuilder(Level.{{level.name}});
				}
				{{/isEnabled}}

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
				public void {{methodName}}(String msg, Throwable t) {
					{{#isEnabled}}
					handle(Level.{{name}}, msg, t);
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
			package io.jstach.rainbowgum.slf4j;

			import static org.slf4j.event.EventConstants.DEBUG_INT;
			import static org.slf4j.event.EventConstants.ERROR_INT;
			import static org.slf4j.event.EventConstants.INFO_INT;
			import static org.slf4j.event.EventConstants.TRACE_INT;
			import static org.slf4j.event.EventConstants.WARN_INT;

			import java.lang.StackWalker.Option;

			import org.eclipse.jdt.annotation.Nullable;
			import org.slf4j.Marker;
			import org.slf4j.event.Level;
			import org.slf4j.spi.LoggingEventBuilder;
			import org.slf4j.spi.NOPLoggingEventBuilder;

			import io.jstach.rainbowgum.LogEvent;
			import io.jstach.rainbowgum.LogEvent.Caller;
			import io.jstach.rainbowgum.LogEventLogger;
			import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAware;

			class ChangeableLogger implements BaseLogger, DepthAware {

				private final String loggerName;

				private final LogEventLogger eventLogger;

				private final RainbowGumMDCAdapter mdc;

				private volatile int level;

				private volatile boolean callerInfo;

				private static final int DEPTH_DELTA = 7;

				private int depth = DEPTH_DELTA;

				ChangeableLogger(String loggerName, LogEventLogger eventLogger, RainbowGumMDCAdapter mdc, int level,
						boolean callerInfo) {
					super();
					this.loggerName = loggerName;
					this.eventLogger = eventLogger;
					this.mdc = mdc;
					this.level = level;
					this.callerInfo = callerInfo;
				}

				@Override
				public RainbowGumMDCAdapter mdc() {
					return mdc;
				}

				@Override
				public String loggerName() {
					return this.loggerName;
				}

				@Override
				public void handle(LogEvent event) {
					/*
					 * TODO perhaps we wrap callerInfo here instead.
					 */
					eventLogger.log(event);
				}

				@Override
				public void handle(LogEvent event, int depth) {
					var e = addCallerInfo(event, depth);
					handle(e);
				}

				void setLevel(int level) {
					this.level = level;
				}

				@Override
				public void setDepth(int index, int depth) {
					this.depth = index + DEPTH_DELTA;

				}

				@Override
				public String toString() {
					return "ChangeableLogger[loggerName=" + loggerName + ", level=" + level + "]";
				}

				private static final StackWalker stackWalker = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);

				private @Nullable Caller caller(int depth) {
					return stackWalker.walk(s -> s.skip(depth).limit(1).map(f -> Caller.of(f)).findFirst().orElse(null));

				}

				@Override
				public LogEvent event(Level level, String formattedMessage, @Nullable Throwable throwable) {
					return addCallerInfo(BaseLogger.super.event(level, formattedMessage, throwable));
				}

				LogEvent addCallerInfo(LogEvent e) {
					return addCallerInfo(e, this.depth);
				}

				LogEvent addCallerInfo(LogEvent e, int depth) {
					if (callerInfo) {
						var found = caller(depth);
						if (found != null) {
							return LogEvent.withCaller(e, found);
						}
					}
					return e;
				}

				@Override
				public LogEvent event0(Level level, String formattedMessage) {
					return addCallerInfo(BaseLogger.super.event0(level, formattedMessage));
				}

				@Override
				public LogEvent event1(Level level, String message, Object arg1) {
					return addCallerInfo(BaseLogger.super.event1(level, message, arg1));
				}

				@Override
				public LogEvent event2(Level level, String message, Object arg1, Object arg2) {
					return addCallerInfo(BaseLogger.super.event2(level, message, arg1, arg2));
				}

				@Override
				public LogEvent eventArray(Level level, String message, Object[] args) {
					return addCallerInfo(BaseLogger.super.eventArray(level, message, args));
				}

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
				public void {{methodName}}(String msg, Throwable t) {
					if ( {{enabledMethodName}}() ) {
						handle(Level.{{name}}, msg, t);
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
			}
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
				default void {{methodName}}(String msg, Throwable t) {
					{{delegate}}.{{methodName}}(msg, t);
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
