package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;

/**
 * End to end golden string tests: each {@link ConfigFailure} constant is a full
 * properties file that should make RainbowGum fail to start, and the exact exception
 * message it should fail with - being an enum, a new case just needs to be added as a new
 * constant and it is automatically picked up by {@link #test(ConfigFailure)}.
 * {@link FakeEncoderConfigurator} (host/label/port/endpoint/tags/headers, i.e.
 * String/Integer/URI/List/Map) is registered by default for every case, exercising a
 * component with a spread of property types failing conversion/validation through a
 * builder+{@link LogProperty.Validator} - the built-in scenarios (unregistered scheme)
 * need no such fixture, but registering it is harmless for them since none of their
 * properties overlap with FakeEncoderBuilder's. {@link FakeGlobalConfigurator} (opted
 * into per-case via {@link ConfigFailure#configurators()}) is the contrasting case: a
 * direct property read with no builder/Validator in between.
 */
class ConfigFailureTest {

	@ParameterizedTest
	@EnumSource(ConfigFailure.class)
	void test(ConfigFailure c) {
		var e = assertThrows(RuntimeException.class, () -> {
			var builder = LogConfig.builder().properties(c.properties());
			c.configurators().forEach(builder::configurator);
			var config = builder.build();
			RainbowGum.builder(config).build().start();
		});
		assertEquals(c.expectedMessage(), e.getMessage());
	}

	/*
	 * Not a ConfigFailure case, and no longer a "throws lazily" case either (see git
	 * history for that older version of this test - and the now-removed
	 * ConfigFailure.badLevelValue, which used to make a bad root "logging.level" fail
	 * RainbowGum...start() itself). Both were made obsolete by the same fix: a level
	 * property that fails to parse - root or per-logger - no longer throws anywhere.
	 * CachedLevelResolver/AlertingLevelConfig catch it, fall back to Level.INFO, and
	 * report exactly one alert per distinct logger name (not one per resolveLevel(name)
	 * call - resolution for a hot logger happens constantly, and ConcurrentHashMap's
	 * computeIfAbsent would otherwise leave a throwing lookup uncached, re-parsing and
	 * re-alerting every single time).
	 */
	@Test
	void testBadLevelMappingAlertsOnceAndFallsBackToInfoInsteadOfThrowing() {
		var props = LogProperties.builder().fromProperties("""
				logging.level.com.blah=BLAH
				""").build();
		var config = LogConfig.builder().properties(props).build();
		try (var gum = RainbowGum.builder(config).build().start()) {
			var alerts = gum.config().alerts();
			assertEquals(0, alerts.stats().total());

			assertEquals(Level.INFO, gum.router().levelResolver().resolveLevel("com.blah"));
			assertEquals(1, alerts.stats().total());

			// resolving the same bad name again must not alert a second time.
			assertEquals(Level.INFO, gum.router().levelResolver().resolveLevel("com.blah"));
			assertEquals(1, alerts.stats().total());
		}
	}

	enum ConfigFailure {

		unregisteredOutputScheme("""
				logging.appenders=myapp
				logging.appender.myapp.output=bogus:///
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error for property. key: 'logging.appender.myapp.output' from PROPERTIES_STRING[logging.appender.myapp.output], NotFoundException No output found. Scheme not registered. scheme: 'bogus', URI: 'bogus:///'
						Tried: 'logging.appender.myapp.output' from PROPERTIES_STRING[logging.appender.myapp.output]"""),

		unregisteredPublisherScheme("""
				logging.route.default.publisher=bogus:///
				""",
				"""
						Error for property. key: 'logging.route.default.publisher' from PROPERTIES_STRING[logging.route.default.publisher], NotFoundException No publisher found. Scheme not registered. scheme: 'bogus', URI: 'bogus:///'
						Tried: 'logging.route.default.publisher' from PROPERTIES_STRING[logging.route.default.publisher]"""),

		unregisteredEncoderScheme("""
				logging.appenders=myapp
				logging.appender.myapp.output=list:///
				logging.appender.myapp.encoder=bogus:///
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error for property. key: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], NotFoundException No encoder found. Scheme not registered. scheme: 'bogus', URI: 'bogus:///'
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]"""),

		encoderMissingRequiredStringProperty("""
				logging.appenders=myapp
				logging.appender.myapp.output=list:///
				logging.appender.myapp.encoder=fake:///
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error converting property. key: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], value: 'fake:///' cause:
						Validation failed for io.jstach.rainbowgum.FakeEncoderBuilder:
						Property missing. keys: ['logging.encoder.myapp.host' from PROPERTIES_STRING[logging.encoder.myapp.host], [logging.appender.myapp.encoder]->URI(fake:///)[host]]
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]"""),

		encoderMalformedIntProperty("""
				logging.appenders=myapp
				logging.appender.myapp.output=list:///
				logging.appender.myapp.encoder=fake:///
				logging.encoder.myapp.host=h
				logging.encoder.myapp.port=notanumber
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error converting property. key: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], value: 'fake:///' cause:
						Validation failed for io.jstach.rainbowgum.FakeEncoderBuilder:
						Error for property. key: 'logging.encoder.myapp.port' from PROPERTIES_STRING[logging.encoder.myapp.port], java.lang.NumberFormatException For input string: "notanumber"
						Tried: 'logging.encoder.myapp.port' from PROPERTIES_STRING[logging.encoder.myapp.port], [logging.appender.myapp.encoder]->URI(fake:///)[port]
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]"""),

		encoderMalformedUriProperty("""
				logging.appenders=myapp
				logging.appender.myapp.output=list:///
				logging.appender.myapp.encoder=fake:///
				logging.encoder.myapp.host=h
				logging.encoder.myapp.endpoint=not a uri with spaces
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error converting property. key: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], value: 'fake:///' cause:
						Validation failed for io.jstach.rainbowgum.FakeEncoderBuilder:
						Error for property. key: 'logging.encoder.myapp.endpoint' from PROPERTIES_STRING[logging.encoder.myapp.endpoint], java.net.URISyntaxException Illegal character in path at index 3: not a uri with spaces
						Tried: 'logging.encoder.myapp.endpoint' from PROPERTIES_STRING[logging.encoder.myapp.endpoint], [logging.appender.myapp.encoder]->URI(fake:///)[endpoint]
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]"""),

		/*
		 * Hits Result.map() at the end of the fluent chain (FakeEncoderBuilder's "label"
		 * field: properties.forKey(...).ofString().map(...)) instead of
		 * LogProperty.ofXxx()/Result.convert() - see the comment on FakeEncoderBuilder's
		 * _label. map() builds the exact same rich error message convert()'s richError()
		 * does, including the "Tried:" line: since the "properties" this builder was
		 * given is itself a composite (PROPERTIES_STRING plus the
		 * [logging.appender.myapp.encoder]->URI(fake:///) query-parameter layer), the
		 * first "Tried:" line names both, same as any convert()-based property here would
		 * (see encoderMalformedIntProperty above). The second "Tried:" line is the outer
		 * encoder-URI-to-builder conversion's own richError(), nested via "cause:".
		 */
		encoderCustomStringValidationFailure("""
				logging.appenders=myapp
				logging.appender.myapp.output=list:///
				logging.appender.myapp.encoder=fake:///
				logging.encoder.myapp.host=h
				logging.encoder.myapp.label=bad
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error converting property. key: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], value: 'fake:///' cause:
						Validation failed for io.jstach.rainbowgum.FakeEncoderBuilder:
						Error for property. key: 'logging.encoder.myapp.label' from PROPERTIES_STRING[logging.encoder.myapp.label], java.lang.IllegalArgumentException label must not be 'bad'
						Tried: 'logging.encoder.myapp.label' from PROPERTIES_STRING[logging.encoder.myapp.label], [logging.appender.myapp.encoder]->URI(fake:///)[label]
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]"""),

		// same Result.map() (not convert()) path as encoderCustomStringValidationFailure
		// above, but on a List.
		encoderCustomListValidationFailure("""
				logging.appenders=myapp
				logging.appender.myapp.output=list:///
				logging.appender.myapp.encoder=fake:///
				logging.encoder.myapp.host=h
				logging.encoder.myapp.tags=good,bad
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error converting property. key: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], value: 'fake:///' cause:
						Validation failed for io.jstach.rainbowgum.FakeEncoderBuilder:
						Error for property. key: 'logging.encoder.myapp.tags' from PROPERTIES_STRING[logging.encoder.myapp.tags], java.lang.IllegalArgumentException tags must not contain 'bad'
						Tried: 'logging.encoder.myapp.tags' from PROPERTIES_STRING[logging.encoder.myapp.tags], [logging.appender.myapp.encoder]->URI(fake:///)[tags]
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]"""),

		// same Result.map() (not convert()) path as encoderCustomStringValidationFailure
		// above, but on a Map.
		encoderCustomMapValidationFailure("""
				logging.appenders=myapp
				logging.appender.myapp.output=list:///
				logging.appender.myapp.encoder=fake:///
				logging.encoder.myapp.host=h
				logging.encoder.myapp.headers=bad=1
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error converting property. key: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], value: 'fake:///' cause:
						Validation failed for io.jstach.rainbowgum.FakeEncoderBuilder:
						Error for property. key: 'logging.encoder.myapp.headers' from PROPERTIES_STRING[logging.encoder.myapp.headers], java.lang.IllegalArgumentException headers must not contain key 'bad'
						Tried: 'logging.encoder.myapp.headers' from PROPERTIES_STRING[logging.encoder.myapp.headers], [logging.appender.myapp.encoder]->URI(fake:///)[headers]
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]"""),

		/*
		 * The 12 FakeGlobalConfigurator cases below cover the full [chained|not chained]
		 * x [error|missing] x [convert|value|validator] matrix - see
		 * FakeGlobalConfigurator's javadoc for what each of the three properties
		 * (mode/mode2/mode3) demonstrates. Sorted by chained (not chained group first,
		 * then chained), then by missing/error within each, so every chained case (and
		 * every chained *error* case in particular) sits next to the others like it.
		 */

		/*
		 * permutation: not chained,missing,value
		 *
		 * Contrast with the encoder cases above, which all go through a builder +
		 * Validator (so a bad value gets collected and reported as "Validation failed for
		 * X:\n..."). FakeGlobalConfigurator instead reads its required property directly
		 * off LogConfig.properties() - the same style JULConfigurator uses for its global
		 * on/off switches - so a missing value here throws immediately and unwrapped: a
		 * plain "Property missing. keys: [...]" with none of the Validator/builder
		 * machinery in between.
		 */
		globalFlagReadWithoutValidatorThrowsDirectly("",
				"""
						Property missing. keys: ['logging.fakeGlobal.mode' from PROPERTIES_STRING[logging.fakeGlobal.mode]]""") {
			@Override
			LogProperties properties() {
				return LogProperties.builder().fromProperties("").build();
			}

			@Override
			List<Configurator> configurators() {
				return List.of(new FakeGlobalConfigurator());
			}
		},

		/*
		 * permutation: not chained,missing,validator
		 *
		 * Same idea as globalFlagReadWithoutValidatorThrowsDirectly above (a direct
		 * property read, no builder), but via a hand-built Validator +
		 * Result.validate(Validator) instead of plain Result.value() - mode is set so
		 * that first direct read succeeds and execution reaches mode2's read. Compare
		 * this message to the plain "Property missing. keys: [...]" above: routing
		 * through a Validator gets the same "Validation failed for X:" wrapper a
		 * generated builder's own Validator would produce, naming FakeGlobalConfigurator
		 * as the thing that wanted the property.
		 */
		globalFlagReadWithValidateBuildsRicherMissingMessage("""
				logging.fakeGlobal.mode=x
				""",
				"""
						Validation failed for io.jstach.rainbowgum.FakeGlobalConfigurator:
						Property missing. keys: ['logging.fakeGlobal.mode2' from PROPERTIES_STRING[logging.fakeGlobal.mode2]]""") {
			@Override
			List<Configurator> configurators() {
				return List.of(new FakeGlobalConfigurator());
			}
		},

		/*
		 * permutation: not chained,missing,convert
		 *
		 * Direct (no builder) convert() read, missing vs error (malformed). This
		 * "Missing" case proves convert() adds nothing when the property is simply absent
		 * - Result.convert()'s own switch passes a Missing straight through (case
		 * Missing<T> m -> m.convert();) without ever reaching richError(), so the message
		 * is the exact same plain "Property missing. keys: [...]" shape as
		 * globalFlagReadWithoutValidatorThrowsDirectly above, just naming a different
		 * key.
		 */
		globalConvertValueMissing("""
				logging.fakeGlobal.mode=x
				logging.fakeGlobal.mode2=y
				""",
				"""
						Property missing. keys: ['logging.fakeGlobal.mode3' from PROPERTIES_STRING[logging.fakeGlobal.mode3]]""") {
			@Override
			List<Configurator> configurators() {
				return List.of(new FakeGlobalConfigurator());
			}
		},

		/*
		 * permutation: not chained,error,convert
		 *
		 * The genuine convert() failure counterpart to globalConvertValueMissing above -
		 * goes through richError() - compare its shape to
		 * encoderMalformedIntProperty/encoderMalformedUriProperty above, which are also
		 * convert() failures but inside a builder + Validator ("Validation failed for
		 * X:\n..." wrapper); here there is no such wrapper, same as
		 * globalFlagReadWithoutValidatorThrowsDirectly.
		 */
		globalConvertValueError("""
				logging.fakeGlobal.mode=x
				logging.fakeGlobal.mode2=y
				logging.fakeGlobal.mode3=bad
				""",
				"""
						Error for property. key: 'logging.fakeGlobal.mode3' from PROPERTIES_STRING[logging.fakeGlobal.mode3], java.lang.IllegalArgumentException mode3 must not be 'bad'
						Tried: 'logging.fakeGlobal.mode3' from PROPERTIES_STRING[logging.fakeGlobal.mode3]""") {
			@Override
			List<Configurator> configurators() {
				return List.of(new FakeGlobalConfigurator());
			}
		},

		/*
		 * permutation: not chained,error,value
		 *
		 * The genuine-error counterpart to globalFlagReadWithoutValidatorThrowsDirectly
		 * above: mode itself fails its map() check, going through the same richError()
		 * formatting convert() uses (quoted key, "from X", exception class name, "Tried:"
		 * line) - see the comment on encoderCustomStringValidationFailure above. Not
		 * chained, so "from X" and "Tried:" both just name PROPERTIES_STRING here; see
		 * globalFlagValueErrorAcrossChainedProperties below for the chained case, where
		 * they differ.
		 */
		globalFlagValueError("""
				logging.fakeGlobal.mode=bad
				""",
				"""
						Error for property. key: 'logging.fakeGlobal.mode' from PROPERTIES_STRING[logging.fakeGlobal.mode], java.lang.IllegalArgumentException mode must not be 'bad'
						Tried: 'logging.fakeGlobal.mode' from PROPERTIES_STRING[logging.fakeGlobal.mode]""") {
			@Override
			List<Configurator> configurators() {
				return List.of(new FakeGlobalConfigurator());
			}
		},

		/*
		 * permutation: not chained,error,validator
		 *
		 * The genuine-error counterpart to
		 * globalFlagReadWithValidateBuildsRicherMissingMessage above: mode2 fails its
		 * map() check instead of being absent, so the Validator collects a Result.Error
		 * (via Validator#add, same as Missing) instead of a Result.Missing - same
		 * "Validation failed for X:" wrapper, but the inner line is mode2's richError()
		 * message (see globalFlagValueError above) instead of
		 * "Property missing. keys: [...]".
		 */
		globalValidateError("""
				logging.fakeGlobal.mode=x
				logging.fakeGlobal.mode2=bad
				""",
				"""
						Validation failed for io.jstach.rainbowgum.FakeGlobalConfigurator:
						Error for property. key: 'logging.fakeGlobal.mode2' from PROPERTIES_STRING[logging.fakeGlobal.mode2], java.lang.IllegalArgumentException mode2 must not be 'bad'
						Tried: 'logging.fakeGlobal.mode2' from PROPERTIES_STRING[logging.fakeGlobal.mode2]""") {
			@Override
			List<Configurator> configurators() {
				return List.of(new FakeGlobalConfigurator());
			}
		},

		/*
		 * permutation: chained,missing,convert
		 *
		 * Chained-composite variant of globalConvertValueMissing above: mode3 is absent
		 * from both chained members, so - same as
		 * globalFlagReadWithoutValidatorThrowsDirectlyAcrossChainedProperties below - the
		 * "keys:" line lists both members' descriptions since nothing was found anywhere
		 * to be exact about.
		 */
		globalConvertValueMissingAcrossChainedProperties("",
				"""
						Property missing. keys: ['logging.fakeGlobal.mode3' from A_PROPS[logging.fakeGlobal.mode3], B_PROPS[logging.fakeGlobal.mode3]]""") {
			@Override
			LogProperties properties() {
				var a = LogProperties.builder().description("A_PROPS").fromProperties("""
						logging.fakeGlobal.mode=x
						logging.fakeGlobal.mode2=y
						""").build();
				var b = LogProperties.builder().description("B_PROPS").fromProperties("").build();
				return LogProperties.of(List.of(a, b));
			}

			@Override
			List<Configurator> configurators() {
				return List.of(new FakeGlobalConfigurator());
			}
		},

		// permutation: chained,missing,value
		// chained-composite variant of globalFlagReadWithoutValidatorThrowsDirectly - see
		// unregisteredOutputSchemeAcrossChainedProperties below for what this proves.
		globalFlagReadWithoutValidatorThrowsDirectlyAcrossChainedProperties("",
				"""
						Property missing. keys: ['logging.fakeGlobal.mode' from A_PROPS[logging.fakeGlobal.mode], B_PROPS[logging.fakeGlobal.mode]]""") {
			@Override
			LogProperties properties() {
				var a = LogProperties.builder().description("A_PROPS").fromProperties("").build();
				var b = LogProperties.builder().description("B_PROPS").fromProperties("").build();
				return LogProperties.of(List.of(a, b));
			}

			@Override
			List<Configurator> configurators() {
				return List.of(new FakeGlobalConfigurator());
			}
		},

		// permutation: chained,missing,validator
		// chained-composite variant of
		// globalFlagReadWithValidateBuildsRicherMissingMessage.
		globalFlagReadWithValidateBuildsRicherMissingMessageAcrossChainedProperties("",
				"""
						Validation failed for io.jstach.rainbowgum.FakeGlobalConfigurator:
						Property missing. keys: ['logging.fakeGlobal.mode2' from A_PROPS[logging.fakeGlobal.mode2], B_PROPS[logging.fakeGlobal.mode2]]""") {
			@Override
			LogProperties properties() {
				var a = LogProperties.builder().description("A_PROPS").fromProperties("""
						logging.fakeGlobal.mode=x
						""").build();
				var b = LogProperties.builder().description("B_PROPS").fromProperties("").build();
				return LogProperties.of(List.of(a, b));
			}

			@Override
			List<Configurator> configurators() {
				return List.of(new FakeGlobalConfigurator());
			}
		},

		/*
		 * permutation: chained,error,convert
		 *
		 * The composite/chained missing cases above
		 * (globalFlagReadWith...AcrossChainedProperties) are both Missing, where a "key
		 * from X" can't be exact - nothing was found anywhere, so
		 * ListLogProperties.description() just joins every member for the "keys:" line.
		 * This one is a genuine convert() Error found on one specific chained member
		 * (mode3 lives only in the second LogProperties, "b" below) - the
		 * "key: ... from X" line stays exact (only "b", not doubled) since it comes from
		 * PropertySuccess.properties() (the exact source), while "Tried: ... from X" is
		 * still the full aggregate (both members, doubled) since that comes from the
		 * outer/composite properties passed into convert(). Same exact-vs-aggregate split
		 * unregisteredOutputSchemeAcrossChainedProperties above shows for the encoder
		 * side - this is the FakeGlobalConfigurator/convert() equivalent.
		 */
		globalConvertValueErrorAcrossChainedProperties("",
				"""
						Error for property. key: 'logging.fakeGlobal.mode3' from B_PROPS[logging.fakeGlobal.mode3], java.lang.IllegalArgumentException mode3 must not be 'bad'
						Tried: 'logging.fakeGlobal.mode3' from A_PROPS[logging.fakeGlobal.mode3], B_PROPS[logging.fakeGlobal.mode3]""") {
			@Override
			LogProperties properties() {
				var a = LogProperties.builder().description("A_PROPS").fromProperties("""
						logging.fakeGlobal.mode=x
						logging.fakeGlobal.mode2=y
						""").build();
				var b = LogProperties.builder().description("B_PROPS").fromProperties("""
						logging.fakeGlobal.mode3=bad
						""").build();
				return LogProperties.of(List.of(a, b));
			}

			@Override
			List<Configurator> configurators() {
				return List.of(new FakeGlobalConfigurator());
			}
		},

		/*
		 * permutation: chained,error,value
		 *
		 * mode (the plain, no-Validator "value" property, see
		 * globalFlagReadWithoutValidatorThrowsDirectlyAcrossChainedProperties above)
		 * genuinely failing to convert (not just being absent), inside a chained
		 * composite. mode is read via Result.map() (not convert()), which builds the
		 * exact same richError() message convert() does, including the "Tried:" line:
		 * "key: ... from X" stays exact (only "b"/B_PROPS, not doubled) since it comes
		 * from PropertySuccess.properties() (the exact source the value was found at),
		 * while "Tried: ... from X" is the full aggregate (both members, doubled) since
		 * it now comes from PropertySuccess.topProperties() - the composite the very
		 * first forKey() lookup in this chain was made against, carried on the result
		 * itself rather than passed in by the caller. Same exact-vs-aggregate split
		 * globalConvertValueErrorAcrossChainedProperties above shows for convert() - the
		 * two are indistinguishable now.
		 */
		globalFlagValueErrorAcrossChainedProperties("",
				"""
						Error for property. key: 'logging.fakeGlobal.mode' from B_PROPS[logging.fakeGlobal.mode], java.lang.IllegalArgumentException mode must not be 'bad'
						Tried: 'logging.fakeGlobal.mode' from A_PROPS[logging.fakeGlobal.mode], B_PROPS[logging.fakeGlobal.mode]""") {
			@Override
			LogProperties properties() {
				var a = LogProperties.builder().description("A_PROPS").fromProperties("").build();
				var b = LogProperties.builder().description("B_PROPS").fromProperties("""
						logging.fakeGlobal.mode=bad
						""").build();
				return LogProperties.of(List.of(a, b));
			}

			@Override
			List<Configurator> configurators() {
				return List.of(new FakeGlobalConfigurator());
			}
		},

		/*
		 * permutation: chained,error,validator
		 *
		 * Chained-composite variant of globalValidateError above: mode2 (in "b") fails
		 * its map() check while mode (in "a") is valid, so the Validator collects the
		 * same richError() message globalValidateError does, naming B_PROPS as the exact
		 * source in "from X" and the full A_PROPS+B_PROPS aggregate in "Tried:" - same as
		 * globalFlagValueErrorAcrossChainedProperties above.
		 */
		globalValidateErrorAcrossChainedProperties("",
				"""
						Validation failed for io.jstach.rainbowgum.FakeGlobalConfigurator:
						Error for property. key: 'logging.fakeGlobal.mode2' from B_PROPS[logging.fakeGlobal.mode2], java.lang.IllegalArgumentException mode2 must not be 'bad'
						Tried: 'logging.fakeGlobal.mode2' from A_PROPS[logging.fakeGlobal.mode2], B_PROPS[logging.fakeGlobal.mode2]""") {
			@Override
			LogProperties properties() {
				var a = LogProperties.builder().description("A_PROPS").fromProperties("""
						logging.fakeGlobal.mode=x
						""").build();
				var b = LogProperties.builder().description("B_PROPS").fromProperties("""
						logging.fakeGlobal.mode2=bad
						""").build();
				return LogProperties.of(List.of(a, b));
			}

			@Override
			List<Configurator> configurators() {
				return List.of(new FakeGlobalConfigurator());
			}
		},

		/*
		 * Chained-source case: exercises ListLogProperties/CompositeLogProperties by
		 * overriding properties() to combine two separately-built LogProperties via
		 * LogProperties.of(List.of(...)) instead of parsing one string.
		 * ListLogProperties.description(key) joins every member's own description for the
		 * key regardless of which member actually had it, so the "Tried:" line below
		 * lists PROPERTIES_STRING twice - once per chained member - proving the composite
		 * is really being walked, not just one of its members.
		 */
		unregisteredOutputSchemeAcrossChainedProperties("",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error for property. key: 'logging.appender.myapp.output' from PROPERTIES_STRING[logging.appender.myapp.output], NotFoundException No output found. Scheme not registered. scheme: 'bogus', URI: 'bogus:///'
						Tried: 'logging.appender.myapp.output' from PROPERTIES_STRING[logging.appender.myapp.output], PROPERTIES_STRING[logging.appender.myapp.output]""") {
			@Override
			LogProperties properties() {
				var appenders = LogProperties.builder().fromProperties("""
						logging.appenders=myapp
						""").build();
				var output = LogProperties.builder().fromProperties("""
						logging.appender.myapp.output=bogus:///
						""").build();
				return LogProperties.of(List.of(appenders, output));
			}
		},

		// same chained-composite idea as unregisteredOutputSchemeAcrossChainedProperties
		// above, but for a missing (rather than found-but-invalid) property - the
		// "keys: [...]" list below shows the missing key's chained description too.
		encoderMissingRequiredStringPropertyAcrossChainedProperties("",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error converting property. key: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], value: 'fake:///' cause:
						Validation failed for io.jstach.rainbowgum.FakeEncoderBuilder:
						Property missing. keys: ['logging.encoder.myapp.host' from PROPERTIES_STRING[logging.encoder.myapp.host], PROPERTIES_STRING[logging.encoder.myapp.host], [logging.appender.myapp.encoder]->URI(fake:///)[host]]
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], PROPERTIES_STRING[logging.appender.myapp.encoder]""") {
			@Override
			LogProperties properties() {
				var appenders = LogProperties.builder().fromProperties("""
						logging.appenders=myapp
						logging.appender.myapp.output=list:///
						""").build();
				var encoder = LogProperties.builder().fromProperties("""
						logging.appender.myapp.encoder=fake:///
						""").build();
				return LogProperties.of(List.of(appenders, encoder));
			}
		};

		private final String propertiesString;

		private final String expectedMessage;

		ConfigFailure(String propertiesString, String expectedMessage) {
			this.propertiesString = propertiesString;
			this.expectedMessage = expectedMessage;
		}

		ConfigFailure() {
			this("", "");
		}

		LogProperties properties() {
			return LogProperties.builder().fromProperties(propertiesString).build();
		}

		String expectedMessage() {
			return expectedMessage;
		}

		List<Configurator> configurators() {
			return List.of(new FakeEncoderConfigurator());
		}

	}

}
