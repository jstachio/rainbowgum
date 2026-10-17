package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/*
 * A source that renames keys is set with its own names, so the suggestion for a mistyped
 * one says what to set in that source too.
 */
class UnusedKeySuggestionTest {

	enum Naming {

		/*
		 * Like environment variables with an application prefix: logging.X is read from
		 * APP_LOG_X with each . turned into _.
		 */
		PREFIXED_VARIABLES {
			@Override
			String name(String key) {
				return "APP_LOG_" + key.substring(LogProperties.ROOT_PREFIX.length()).replace('.', '_');
			}

			@Override
			String key(String name) {
				return LogProperties.ROOT_PREFIX + name.substring("APP_LOG_".length()).replace('_', '.');
			}

			@Override
			String description(String key) {
				return "environment variable " + name(key);
			}
		},
		/*
		 * Keys used as they are, the way a properties file holds them.
		 */
		KEYS {
			@Override
			String name(String key) {
				return key;
			}

			@Override
			String key(String name) {
				return name;
			}

			@Override
			String description(String key) {
				return "test[" + key + "]";
			}
		};

		abstract String name(String key);

		abstract String key(String name);

		abstract String description(String key);

	}

	record Source(Naming naming, Map<String, String> values) implements LogProperties.Listable {

		@Override
		public @Nullable String valueOrNull(String key) {
			return values.get(naming.name(key));
		}

		@Override
		public Collection<String> keys() {
			return values.keySet().stream().map(naming::key).toList();
		}

		@Override
		public String description(String key) {
			return naming.description(key);
		}

	}

	enum SuggestionCase {

		PREFIXED_VARIABLES(Naming.PREFIXED_VARIABLES, "APP_LOG_appendrs", """
				[WARNING] Property key 'logging.appendrs' from environment variable APP_LOG_appendrs \
				was set but not read during startup. \
				Did you mean 'logging.appenders' (environment variable APP_LOG_appenders)?"""),
		KEYS(Naming.KEYS, "logging.appendrs", """
				[WARNING] Property key 'logging.appendrs' from test[logging.appendrs] \
				was set but not read during startup. Did you mean 'logging.appenders'?"""),
		NO_SUGGESTION(Naming.PREFIXED_VARIABLES, "APP_LOG_made_up", """
				[WARNING] Property key 'logging.made.up' from environment variable APP_LOG_made_up \
				was set but not read during startup.""");

		final Naming naming;

		final String name;

		final String expected;

		SuggestionCase(Naming naming, String name, String expected) {
			this.naming = naming;
			this.name = name;
			this.expected = expected;
		}

	}

	@ParameterizedTest
	@EnumSource(SuggestionCase.class)
	void suggestionUsesTheSourceNames(SuggestionCase test) {
		var source = new Source(test.naming, Map.of(test.name, "x"));
		// Every unread key is a warning, so the one without a suggestion fails too.
		var config = LogConfig.builder()
			.properties(source)
			.alertsFail(LogAlerts.FailLevel.WARNING)
			.alertsUnusedKey(LogAlerts.UnusedKeyLevel.WARNING)
			.build();
		var gum = RainbowGum.builder(config).build();
		var e = assertThrows(IllegalStateException.class, gum::start);
		assertEquals("1 alert(s) at warning or above were recorded while starting and logging.alerts.fail=warning:\n"
				+ test.expected, e.getMessage());
	}

}
