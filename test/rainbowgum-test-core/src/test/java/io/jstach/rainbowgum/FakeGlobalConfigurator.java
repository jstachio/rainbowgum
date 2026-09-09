package io.jstach.rainbowgum;

import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;

/**
 * Test-only configurator for {@link ConfigFailureTest} that deliberately does
 * <strong>not</strong> go through a builder/{@link LogProperty.Validator} the way
 * {@link FakeEncoderBuilder} does - it reads properties directly off
 * {@link LogConfig#properties()}, the same style {@code JULConfigurator} (see
 * {@code logging.jul.disable}/{@code logging.jul.level.disable} in rainbowgum-jul) uses
 * for its global on/off switches. Unlike those switches (always {@code Boolean}, which
 * can never itself fail to parse - {@link Boolean#parseBoolean(String)} just returns
 * {@code false} for anything unrecognized), this fake also has two required companion
 * properties, read the same direct way with no {@code .or(...)} fallback:
 * {@value #MODE_PROPERTY} via plain {@link LogProperty.Result#value()}, so a missing/bad
 * value throws immediately and unwrapped - no "Validation failed for ...:" collection in
 * between - and {@value #MODE2_PROPERTY} via {@link LogProperty.Result#validate(Class)
 * value()'s validate(Class) sibling}, which gets that richer "Validation failed for X:"
 * message (naming this class) without a full builder-shaped {@link LogProperty.Validator}
 * of its own - see
 * {@link ConfigFailureTest.ConfigFailure#globalFlagReadWithValidateBuildsRicherMissingMessage}
 * for exactly how the two compare.
 */
final class FakeGlobalConfigurator implements Configurator {

	static final String DISABLE_PROPERTY = "logging.fakeGlobal.disable";

	static final String MODE_PROPERTY = "logging.fakeGlobal.mode";

	static final String MODE2_PROPERTY = "logging.fakeGlobal.mode2";

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		var properties = config.properties();
		boolean disabled = properties.forKey(DISABLE_PROPERTY).ofBoolean().or(false).value();
		if (disabled) {
			return true;
		}
		properties.forKey(MODE_PROPERTY).ofString().value();
		properties.forKey(MODE2_PROPERTY).ofString().validate(FakeGlobalConfigurator.class);
		return true;
	}

}
