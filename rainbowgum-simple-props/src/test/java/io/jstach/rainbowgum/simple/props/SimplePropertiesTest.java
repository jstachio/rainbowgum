package io.jstach.rainbowgum.simple.props;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty.PropertyConvertException;
import io.jstach.rainbowgum.LogProperty.Result;
import io.jstach.rainbowgum.LogProperty.ValidationException;

/*
 * testSystemPropertyWinsOverEverything sets a real System property (JVM-wide global
 * state) - @Isolated keeps this from racing any other test class that reads/sets system
 * properties under the "fast" profile's parallel test execution (see
 * QueueRouterLevelPropertyInvalidTest in test/rainbowgum-test-core for the same pattern);
 * SAME_THREAD keeps this class's own methods from racing each other/that property.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class SimplePropertiesTest {

	@Test
	void testProfileSourcesWrapWithDuplicatedSystemProperties() {
		var simple = SimpleProperties.builder().profiles("fail").envLookup(k -> null).build();
		var properties = LogProperties
			.of(List.of(LogProperties.StandardProperties.SYSTEM_PROPERTIES, LogProperties.of(simple.properties())));
		String key = "logging.appender.console.encoder";
		assertEquals("""
				Sources:
				    SYSTEM_PROPERTIES[logging.appender.console.encoder],
				    SYSTEM_PROPERTIES[logging.appender.console.encoder],
				    ENV[RAINBOWGUM_appender_console_encoder],
				    SIMPLE_PROPS[classpath:/logging-fail.properties:2][logging.appender.console.encoder],
				    SIMPLE_PROPS[classpath:/logging.properties][logging.appender.console.encoder]""",
				"Sources:" + properties.description(key));
		var error = assertThrows(ValidationException.class, () -> properties.forKey(key).ofString().map(value -> {
			throw new IllegalArgumentException("Encoder failed");
		}).validateNow(SimplePropertiesTest.class));
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.simple.props.SimplePropertiesTest:
						Error for property. key: 'logging.appender.console.encoder' from SIMPLE_PROPS[classpath:/logging-fail.properties:2][logging.appender.console.encoder], Encoder failed
						Tried:
						    'logging.appender.console.encoder' from:
						        SYSTEM_PROPERTIES[logging.appender.console.encoder],
						        SYSTEM_PROPERTIES[logging.appender.console.encoder],
						        ENV[RAINBOWGUM_appender_console_encoder],
						        SIMPLE_PROPS[classpath:/logging-fail.properties:2][logging.appender.console.encoder],
						        SIMPLE_PROPS[classpath:/logging.properties][logging.appender.console.encoder]""",
				error.getMessage());
	}

	@Test
	void testDefaultProfileIsSelectedWhenNoProfilesAreSpecified() {
		var props = SimpleProperties.builder().resource("default-profile.properties").envLookup(k -> null).build();
		var composite = LogProperties.of(props.properties());
		assertEquals("default-pattern", composite.valueOrNull("logging.pattern.console"));
		assertEquals("default", composite.valueOrNull("logging.profile.test.shared"));
		assertEquals("base", composite.valueOrNull("logging.profile.test.base"));
	}

	@Test
	void testSelectedProfileReplacesDefaultProfile() {
		var props = SimpleProperties.builder()
			.resource("default-profile.properties")
			.profiles("selected")
			.envLookup(k -> null)
			.build();
		var composite = LogProperties.of(props.properties());
		assertEquals(null, composite.valueOrNull("logging.pattern.console"));
		assertEquals("selected", composite.valueOrNull("logging.profile.test.shared"));
		assertEquals("base", composite.valueOrNull("logging.profile.test.base"));
	}

	@Test
	void testEmptyEnvironmentProfilesSelectDefaultInsteadOfBuilderProfiles() {
		var props = SimpleProperties.builder()
			.resource("default-profile.properties")
			.profiles("selected")
			.envLookup(Map.of("RAINBOWGUM_profiles", "")::get)
			.build();
		assertEquals("default", LogProperties.of(props.properties()).valueOrNull("logging.profile.test.shared"));
	}

	@Test
	void testEmptySystemProfilesSelectDefaultInsteadOfEnvironmentAndBuilderProfiles() {
		String previous = System.getProperty(SimpleProperties.PROFILES_PROPERTY);
		try {
			System.setProperty(SimpleProperties.PROFILES_PROPERTY, "");
			var props = SimpleProperties.builder()
				.resource("default-profile.properties")
				.profiles("selected")
				.envLookup(Map.of("RAINBOWGUM_profiles", "selected")::get)
				.build();
			assertEquals("default", LogProperties.of(props.properties()).valueOrNull("logging.profile.test.shared"));
		}
		finally {
			if (previous == null) {
				System.getProperties().remove(SimpleProperties.PROFILES_PROPERTY);
			}
			else {
				System.setProperty(SimpleProperties.PROFILES_PROPERTY, previous);
			}
		}
	}

	@Test
	void testExplicitDefaultProfileMayBeMissing() {
		var props = SimpleProperties.builder().profiles("default", "profile-first").envLookup(k -> null).build();
		var composite = LogProperties.of(props.properties());
		assertEquals("first", composite.valueOrNull("logging.profile.test.shared"));
		assertEquals("WARN", composite.valueOrNull("logging.level.root"));
	}

	@Test
	void testEnvVarMappingDefaultPrefix() {
		var props = SimpleProperties.builder().envLookup(Map.of("RAINBOWGUM_level_root", "DEBUG")::get).build();
		var composite = LogProperties.of(props.properties());
		assertEquals("DEBUG",
				composite.forKey("logging.level.root").ofString().validateNow(SimplePropertiesTest.class));
	}

	@Test
	void testEnvVarMappingCustomPrefix() {
		var props = SimpleProperties.builder()
			.envPrefix("MYCO_")
			.envLookup(Map.of("MYCO_level_root", "TRACE")::get)
			.build();
		var composite = LogProperties.of(props.properties());
		assertEquals("TRACE",
				composite.forKey("logging.level.root").ofString().validateNow(SimplePropertiesTest.class));
	}

	@Test
	void testClasspathResourceFallback() {
		// default resource is src/test/resources/logging.properties
		// (logging.level.root=WARN);
		// no env value for the key, so the file layer should win.
		var props = SimpleProperties.builder().envLookup(k -> null).build();
		var composite = LogProperties.of(props.properties());
		assertEquals("WARN", composite.forKey("logging.level.root").ofString().validateNow(SimplePropertiesTest.class));
	}

	@Test
	void testResourceWithoutClasspathPrefixOrLeadingSlashStillResolves() {
		// no "classpath:" scheme and no leading "/" - the branch every other test's
		// "classpath:/..." resource skips (loadResource's startsWith("classpath:")
		// check is false, and the leading-slash-stripping loop never runs since there
		// is none to strip).
		var props = SimpleProperties.builder().resource("no-prefix.properties").envLookup(k -> null).build();
		var composite = LogProperties.of(props.properties());
		assertEquals("INFO", composite.forKey("logging.level.root").ofString().validateNow(SimplePropertiesTest.class));
	}

	private static String value(SimpleProperties props, String key) {
		return LogProperties.of(props.properties()).forKey(key).ofString().validateNow(SimplePropertiesTest.class);
	}

	@Test
	void shortKeysGetTheLoggingPrefix() {
		var props = SimpleProperties.builder().resource("short-keys.properties").envLookup(k -> null).build();
		assertEquals("debug", value(props, "logging.level.com.myco"));
		assertEquals("WARN", value(props, "logging.level.root"));
	}

	@Test
	void shortKeyGivenBothWaysKeepsTheLaterLine() {
		var props = SimpleProperties.builder().resource("short-keys.properties").envLookup(k -> null).build();
		assertEquals("ERROR", value(props, "logging.level.com.other"));
	}

	@Test
	void shortKeyDescriptionShowsTheKeyAsWritten() {
		var props = SimpleProperties.builder().resource("short-keys.properties").envLookup(k -> null).build();
		var file = props.properties().get(props.properties().size() - 1);
		assertEquals("SIMPLE_PROPS[short-keys.properties:2][level.com.myco]",
				file.description("logging.level.com.myco"));
	}

	@Test
	void shortKeysApplyToProfileResources() {
		var props = SimpleProperties.builder()
			.resource("short-keys.properties")
			.profiles("dev")
			.envLookup(k -> null)
			.build();
		assertEquals("trace", value(props, "logging.level.com.myco"));
	}

	@Test
	void shortIsTheDefault() {
		var props = SimpleProperties.builder().resource("short-default.properties").envLookup(k -> null).build();
		assertEquals("value", value(props, "logging.unqualified.setting"));
	}

	@Test
	void javaUtilLoggingFileFailsByDefault() {
		var props = SimpleProperties.builder().resource("jul.properties").envLookup(k -> null).build();
		var e = assertThrows(IllegalArgumentException.class, props::properties);
		String expected = """
				Invalid simple-props base resource:
				Property key looks like java.util.logging configuration, which Rainbow Gum does not read. \
				key: 'handlers' from SIMPLE_PROPS[jul.properties:1][handlers]
				Property key looks like java.util.logging configuration, which Rainbow Gum does not read. \
				key: '.level' from SIMPLE_PROPS[jul.properties:2][.level]
				Property key looks like java.util.logging configuration, which Rainbow Gum does not read. \
				key: 'java.util.logging.ConsoleHandler.level' from \
				SIMPLE_PROPS[jul.properties:3][java.util.logging.ConsoleHandler.level]""";
		assertEquals(expected, e.getMessage());
	}

	@Test
	void invalidStrictValueFails() {
		var e = assertThrows(ValidationException.class,
				() -> SimpleProperties.builder().resource("strict-invalid.properties").envLookup(k -> null).build());
		String expected = """
				Validation failed for io.jstach.rainbowgum.simple.props.SimpleProperties:
				Error for property. key: 'logging.simpleprops.strict' from \
				SIMPLE_PROPS[strict-invalid.properties:1][logging.simpleprops.strict], \
				'prefixed' is not a valid value for io.jstach.rainbowgum.simple.props.SimpleProperties.StrictType. \
				Valid values: 'off', 'alert', 'fail', 'short', 'true', 'false'""";
		assertEquals(expected, e.getMessage());
	}

	@Test
	void testMissingResourceSuppliesNoProperties() {
		var props = SimpleProperties.builder()
			.resource("classpath:/does-not-exist.properties")
			.envLookup(Map.of("RAINBOWGUM_level_root", "DEBUG")::get)
			.build();
		assertEquals(List.of(), props.properties());
	}

	@Test
	void testMissingResourceDoesNotSelectProfiles() {
		var props = SimpleProperties.builder()
			.resource("classpath:/does-not-exist.properties")
			.profiles("profile-first")
			.envLookup(k -> {
				throw new AssertionError("Environment should not be read without a base resource");
			})
			.build();
		assertEquals(List.of(), props.properties());
	}

	@Test
	void testEnvVarWinsOverClasspathFile() {
		var props = SimpleProperties.builder().envLookup(Map.of("RAINBOWGUM_level_root", "DEBUG")::get).build();
		var composite = LogProperties.of(props.properties());
		// file has WARN, env has DEBUG - env (order 300) beats the file layer (order
		// 100).
		assertEquals("DEBUG",
				composite.forKey("logging.level.root").ofString().validateNow(SimplePropertiesTest.class));
	}

	@Test
	void testEnvVarTranslateKeyDoesNotStripNonRootPrefixedKey() {
		// direct unit test of the branch composite lookups never reach, since every
		// real property key starts with LogProperties.ROOT_PREFIX ("logging.") - the
		// key is used as-is (only "." to "_" still applies) instead of having a
		// "logging." lead segment stripped that was never there. EnvVarProperties is
		// package-private so this can only be exercised from here.
		var env = new EnvVarProperties("MYCO_", k -> null);
		assertEquals("MYCO_not_rooted", env.translateKey("not.rooted"));
	}

	@Test
	void testEnvVarReportIncludesThePrefix() throws Exception {
		var env = new EnvVarProperties("MYCO_", k -> null);
		var sb = new StringBuilder();
		env.report(sb);
		assertEquals("ENV[MYCO_]", sb.toString());
	}

	@Test
	void testEnvVarBadIntValueFailsLoudlyWithEnvDescription() {
		var props = SimpleProperties.builder().envLookup(Map.of("RAINBOWGUM_threshold", "not-a-number")::get).build();
		var composite = LogProperties.of(props.properties());
		var e = assertThrows(PropertyConvertException.class,
				() -> ((Result.Error<Integer>) composite.forKey("logging.threshold").ofInt()).value());
		assertEquals(
				"""
						Error for property. key: 'logging.threshold' from ENV[RAINBOWGUM_threshold], java.lang.NumberFormatException For input string: "not-a-number"
						Tried:
						    'logging.threshold' from:
						        SYSTEM_PROPERTIES[logging.threshold],
						        ENV[RAINBOWGUM_threshold],
						        SIMPLE_PROPS[classpath:/logging.properties][logging.threshold]""",
				e.getMessage());
	}

	@Test
	void testClasspathFileBadIntValueFailsLoudlyWithFileDescription() {
		var props = SimpleProperties.builder().resource("classpath:/bad-int.properties").envLookup(k -> null).build();
		var composite = LogProperties.of(props.properties());
		var e = assertThrows(PropertyConvertException.class,
				() -> ((Result.Error<Integer>) composite.forKey("logging.threshold").ofInt()).value());
		assertEquals(
				"""
						Error for property. key: 'logging.threshold' from SIMPLE_PROPS[classpath:/bad-int.properties:4][logging.threshold], java.lang.NumberFormatException For input string: "not-a-number"
						Tried:
						    'logging.threshold' from:
						        SYSTEM_PROPERTIES[logging.threshold],
						        ENV[RAINBOWGUM_threshold],
						        SIMPLE_PROPS[classpath:/bad-int.properties:4][logging.threshold]""",
				e.getMessage());
	}

	@Test
	void testSystemPropertyWinsOverEverything() {
		System.setProperty("logging.level.root", "ERROR");
		try {
			var props = SimpleProperties.builder().envLookup(Map.of("RAINBOWGUM_level_root", "DEBUG")::get).build();
			var composite = LogProperties.of(props.properties());
			// sysprop (order 400) beats both env (DEBUG) and the file (WARN).
			assertEquals("ERROR",
					composite.forKey("logging.level.root").ofString().validateNow(SimplePropertiesTest.class));
		}
		finally {
			System.clearProperty("logging.level.root");
		}
	}

	@Test
	void testProfilesUseListedPriorityAndFallBackToBase() {
		var props = SimpleProperties.builder()
			.envLookup(Map.of("RAINBOWGUM_profiles", "profile-first,profile-second")::get)
			.build();
		var composite = LogProperties.of(props.properties());
		assertEquals("first", composite.valueOrNull("logging.profile.test.shared"));
		assertEquals("second-only", composite.valueOrNull("logging.profile.test.fallback"));
		assertEquals("WARN", composite.valueOrNull("logging.level.root"));
	}

	@Test
	void testInvalidPropertyFromSecondProfileReportsAllSources() {
		var props = SimpleProperties.builder().profiles("profile-first", "profile-second").envLookup(k -> null).build();
		var composite = LogProperties.of(props.properties());
		var error = assertThrows(ValidationException.class,
				() -> composite.forKey("logging.profile.test.fallback")
					.ofInt()
					.validateNow(SimplePropertiesTest.class));
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.simple.props.SimplePropertiesTest:
						Error for property. key: 'logging.profile.test.fallback' from SIMPLE_PROPS[classpath:/logging-profile-second.properties:2][logging.profile.test.fallback], java.lang.NumberFormatException For input string: "second-only"
						Tried:
						    'logging.profile.test.fallback' from:
						        SYSTEM_PROPERTIES[logging.profile.test.fallback],
						        ENV[RAINBOWGUM_profile_test_fallback],
						        SIMPLE_PROPS[classpath:/logging-profile-first.properties][logging.profile.test.fallback],
						        SIMPLE_PROPS[classpath:/logging-profile-second.properties:2][logging.profile.test.fallback],
						        SIMPLE_PROPS[classpath:/logging.properties][logging.profile.test.fallback]""",
				error.getMessage());
	}

	@Test
	void testMissingPropertyWithMultipleProfilesReportsAllSources() {
		var props = SimpleProperties.builder().profiles("profile-first", "profile-second").envLookup(k -> null).build();
		var composite = LogProperties.of(props.properties());
		var error = assertThrows(ValidationException.class,
				() -> composite.forKey("logging.profile.test.missing")
					.ofString()
					.validateNow(SimplePropertiesTest.class));
		assertEquals("""
				Validation failed for io.jstach.rainbowgum.simple.props.SimplePropertiesTest:
				Property missing. key:
				    'logging.profile.test.missing' from:
				        SYSTEM_PROPERTIES[logging.profile.test.missing],
				        ENV[RAINBOWGUM_profile_test_missing],
				        SIMPLE_PROPS[classpath:/logging-profile-first.properties][logging.profile.test.missing],
				        SIMPLE_PROPS[classpath:/logging-profile-second.properties][logging.profile.test.missing],
				        SIMPLE_PROPS[classpath:/logging.properties][logging.profile.test.missing]""",
				error.getMessage());
	}

	@Test
	void testBuilderProfilesAreFallbackAndKeepListedPriority() {
		var props = SimpleProperties.builder().profiles("profile-first", "profile-second").envLookup(k -> null).build();
		var composite = LogProperties.of(props.properties());
		assertEquals("first", composite.valueOrNull("logging.profile.test.shared"));
		assertEquals("second-only", composite.valueOrNull("logging.profile.test.fallback"));
	}

	@Test
	void testEnvironmentProfilesReplaceBuilderProfiles() {
		var props = SimpleProperties.builder()
			.profiles(java.util.List.of("profile-second"))
			.envLookup(Map.of("RAINBOWGUM_profiles", "profile-first")::get)
			.build();
		var composite = LogProperties.of(props.properties());
		assertEquals("first", composite.valueOrNull("logging.profile.test.shared"));
		assertEquals(null, composite.valueOrNull("logging.profile.test.fallback"));
	}

	@Test
	void testEmptyEnvironmentProfileListDisablesBuilderProfiles() {
		var props = SimpleProperties.builder()
			.profiles("profile-first")
			.envLookup(Map.of("RAINBOWGUM_profiles", "")::get)
			.build();
		var composite = LogProperties.of(props.properties());
		assertEquals(null, composite.valueOrNull("logging.profile.test.shared"));
		assertEquals("WARN", composite.valueOrNull("logging.level.root"));
	}

	@Test
	void testProfilesAreSelectedOnlyBeforeLoadingFiles() {
		var base = SimpleProperties.builder().resource("custom.properties").envLookup(k -> null).build();
		assertEquals(null, LogProperties.of(base.properties()).valueOrNull("logging.profile.test.shared"));
		var profile = SimpleProperties.builder().envLookup(Map.of("RAINBOWGUM_profiles", "profile-first")::get).build();
		assertEquals(null, LogProperties.of(profile.properties()).valueOrNull("logging.profile.test.fallback"));
	}

	@Test
	void testCustomResourceAndEnvironmentPrefix() {
		var props = SimpleProperties.builder()
			.resource("classpath:/custom.properties")
			.envPrefix("CUSTOM_")
			.envLookup(Map.of("CUSTOM_profiles", "profile-first")::get)
			.build();
		var composite = LogProperties.of(props.properties());
		assertEquals("custom", composite.valueOrNull("logging.profile.test.shared"));
		assertEquals("base", composite.valueOrNull("logging.profile.test.base"));
	}

	@Test
	void testSystemProfileSelectionAndExternalOverrides() {
		String previous = System.getProperty(SimpleProperties.PROFILES_PROPERTY);
		String key = "logging.profile.test.shared";
		String previousValue = System.getProperty(key);
		try {
			System.setProperty(SimpleProperties.PROFILES_PROPERTY, "profile-second");
			var builder = SimpleProperties.builder()
				.profiles("not-present")
				.envLookup(Map.of("RAINBOWGUM_profiles", "profile-first")::get);
			assertEquals("second", LogProperties.of(builder.build().properties()).valueOrNull(key));
			builder.envLookup(Map.of("RAINBOWGUM_profile_test_shared", "environment")::get);
			assertEquals("environment", LogProperties.of(builder.build().properties()).valueOrNull(key));
			System.setProperty(key, "system");
			assertEquals("system", LogProperties.of(builder.build().properties()).valueOrNull(key));
		}
		finally {
			if (previous == null) {
				System.getProperties().remove(SimpleProperties.PROFILES_PROPERTY);
			}
			else {
				System.setProperty(SimpleProperties.PROFILES_PROPERTY, previous);
			}
			if (previousValue == null) {
				System.getProperties().remove(key);
			}
			else {
				System.setProperty(key, previousValue);
			}
		}
	}

	@Test
	void testInvalidProfileReportsItsSource() {
		var builder = SimpleProperties.builder().envLookup(Map.of("RAINBOWGUM_profiles", "../secret")::get);
		var error = assertThrows(io.jstach.rainbowgum.LogProperty.ValidationException.class, builder::build);
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.simple.props.SimpleProperties:
						Error for property. key: 'logging.profiles' from ENV[RAINBOWGUM_profiles], Invalid profile name '../secret': use only ASCII letters, digits, underscores, and hyphens
						Tried: 'logging.profiles' from SYSTEM_PROPERTIES[logging.profiles], ENV[RAINBOWGUM_profiles]""",
				error.getMessage());
	}

	@Test
	void testMissingSelectedProfileReportsItsSource() {
		var builder = SimpleProperties.builder().envLookup(Map.of("RAINBOWGUM_profiles", "not-present")::get);
		var error = assertThrows(io.jstach.rainbowgum.LogProperty.ValidationException.class, builder::build);
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.simple.props.SimpleProperties:
						Error for property. key: 'logging.profiles' from ENV[RAINBOWGUM_profiles], Missing classpath resource 'classpath:/logging-not-present.properties' for selected profile 'not-present'
						Tried: 'logging.profiles' from SYSTEM_PROPERTIES[logging.profiles], ENV[RAINBOWGUM_profiles]""",
				error.getMessage());
	}

	@Test
	void testEnvironmentSelectedProfileWithoutBaseResourceSuppliesNoProperties() {
		var props = SimpleProperties.builder()
			.resource("classpath:/unconfigured.properties")
			.envLookup(Map.of("RAINBOWGUM_profiles", "profile-first", "RAINBOWGUM_level_root", "DEBUG")::get)
			.build();
		assertEquals(List.of(), props.properties());
	}

	@Test
	void testBuilderSelectedProfileWithoutBaseResourceSuppliesNoProperties() {
		var props = SimpleProperties.builder()
			.resource("classpath:/unconfigured.properties")
			.profiles("profile-first")
			.envLookup(k -> null)
			.build();
		assertEquals(List.of(), props.properties());
	}

}
