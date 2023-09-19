package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

class LevelLoggerTest {

	@Test
	void test() {
		fail("Not yet implemented");
	}
	
	public record LoggerModel(LevelModel level) {
		public Set<Level> levels() {
			return EnumSet.allOf(Level.class);
		}
	}
	
	public record LevelModel(Level level, Level selected) {
		public String enabledMethodName() {
			return "is" + level.name().substring(0, 1).toUpperCase(Locale.ROOT) 
					+ level.name().substring(1) + "Enabled";
		}
		public String methodName() {
			return level.name().toLowerCase(Locale.ROOT);
		}
		public boolean isEnabled() {
			return level.toInt() >= selected.toInt();
		}
	}
	
	public static final String template = """
				{{#level}}
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
				public void {{methodName}}(
						String msg) {
					{{#isEnabled}}
					// handle(Level.{{name}}, msg);
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(
						String format,
						Object arg) {
					{{#isEnabled}}
					// handle(Level.{{name}}, format, arg);
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(
						String format,
						Object arg1,
						Object arg2) {
					{{#isEnabled}}
					// handle(Level.{{name}}, format, arg1, arg2);
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(
						String format,
						Object... arguments) {
					{{#isEnabled}}
					// handle(Level.{{name}}, format, arguments);
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(
						String msg,
						Throwable t) {
						Object... arguments) {
					{{#isEnabled}}
					// handle(Level.{{name}}, msg, t, arguments);
					{{/isEnabled}}
				}

				@Override
				public boolean {{enabledMethodName}}(
						Marker marker) {
					{{#isEnabled}}
						return true;
					{{/isEnabled}}
					{{^isEnabled}}
						return false;
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(
						Marker marker,
						String msg) {
					{{#isEnabled}}
					// handle(Level.{{name}}, msg);
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(
						Marker marker,
						String format,
						Object arg) {
					{{#isEnabled}}
					// handle(Level.{{name}}, format, arg);
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(
						Marker marker,
						String format,
						Object arg1,
						Object arg2) {
					{{#isEnabled}}
					// handle(Level.{{name}}, format, arg1, arg2);
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(
						Marker marker,
						String format,
						Object... argArray) {
					{{#isEnabled}}
					// handle(Level.{{name}}, format, argArray);
					{{/isEnabled}}
				}

				@Override
				public void {{methodName}}(
						Marker marker,
						String msg,
						Throwable t) {
					{{#isEnabled}}
					// handle(Level.{{name}}, msg, t);
					{{/isEnabled}}
				}
				{{/level}}
			""";

}
