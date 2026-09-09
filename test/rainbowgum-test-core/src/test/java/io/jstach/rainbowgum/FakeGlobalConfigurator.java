package io.jstach.rainbowgum;

import java.util.List;

import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;

/**
 * Test-only configurator for {@link ConfigFailureTest} that deliberately does
 * <strong>not</strong> go through a generated builder the way {@link FakeEncoderBuilder}
 * does - it reads properties directly off {@link LogConfig#properties()}, the same style
 * {@code JULConfigurator} (see
 * {@code logging.jul.disable}/{@code logging.jul.level.disable} in rainbowgum-jul) uses
 * for its global on/off switches. Unlike those switches (always {@code Boolean}, which
 * can never itself fail to parse - {@link Boolean#parseBoolean(String)} just returns
 * {@code false} for anything unrecognized), this fake also has four required companion
 * properties, each demonstrating a different way a direct (no builder) read can fail:
 * <ul>
 * <li>{@value #MODE_PROPERTY} via plain {@link LogProperty.Result#value()}, so a
 * missing/bad value throws immediately and unwrapped - no "Validation failed for ...:"
 * collection in between.</li>
 * <li>{@value #MODE2_PROPERTY} via a hand-built
 * {@link LogProperty.Validator}/{@link LogProperty.Result#validate(LogProperty.Validator)
 * validate(Validator)}, which gets that richer "Validation failed for X:" message (naming
 * this class) the same way a generated builder's own {@code Validator} would.</li>
 * <li>{@value #MODE3_PROPERTY}, a single {@link String} value validated through
 * {@link LogProperty.Result#convert(LogProperties, LogProperty.PropertyFunction)
 * convert()} (not {@link LogProperty.Result#map(LogProperty.PropertyFunction) map()}, see
 * {@code FakeEncoderBuilder}'s {@code label}) so the failure goes through
 * {@code richError()}'s richer path instead of {@code map()}'s terse
 * {@code Error.of(key, e)}.</li>
 * <li>{@value #TAGS_PROPERTY}, the same idea as {@value #MODE3_PROPERTY} but a
 * {@code List<String>} - compare the two to see what, if anything, changes in the message
 * shape between a {@code convert()} failure on a single value versus a list.</li>
 * </ul>
 * See {@link ConfigFailureTest.ConfigFailure} for exactly how these compare, including
 * chained/{@code ListLogProperties} variants.
 */
final class FakeGlobalConfigurator implements Configurator {

	static final String DISABLE_PROPERTY = "logging.fakeGlobal.disable";

	static final String MODE_PROPERTY = "logging.fakeGlobal.mode";

	static final String MODE2_PROPERTY = "logging.fakeGlobal.mode2";

	static final String MODE3_PROPERTY = "logging.fakeGlobal.mode3";

	static final String TAGS_PROPERTY = "logging.fakeGlobal.tags";

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		var properties = config.properties();
		boolean disabled = properties.forKey(DISABLE_PROPERTY).ofBoolean().or(false).value();
		if (disabled) {
			return true;
		}
		properties.forKey(MODE_PROPERTY).ofString().value();
		var v = LogProperty.Validator.of(FakeGlobalConfigurator.class);
		var mode2 = properties.forKey(MODE2_PROPERTY).ofString().validate(v);
		v.validate();
		mode2.value();
		properties.forKey(MODE3_PROPERTY).ofString().convert(properties, FakeGlobalConfigurator::checkMode3).value();
		properties.forKey(TAGS_PROPERTY).ofList().convert(properties, FakeGlobalConfigurator::checkTags).value();
		return true;
	}

	private static String checkMode3(String value) {
		if (value.equals("bad")) {
			throw new IllegalArgumentException("mode3 must not be 'bad'");
		}
		return value;
	}

	private static List<String> checkTags(List<String> tags) {
		if (tags.contains("bad")) {
			throw new IllegalArgumentException("tags must not contain 'bad'");
		}
		return tags;
	}

}
