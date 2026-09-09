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
 * {@code false} for anything unrecognized), this fake also has a required companion
 * property, read the same direct way with no {@code .or(...)} fallback, so a missing/bad
 * value throws immediately and unwrapped - no "Validation failed for ...:" collection in
 * between.
 */
final class FakeGlobalConfigurator implements Configurator {

	static final String DISABLE_PROPERTY = "logging.fakeGlobal.disable";

	static final String MODE_PROPERTY = "logging.fakeGlobal.mode";

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		var properties = config.properties();
		boolean disabled = properties.forKey(DISABLE_PROPERTY).ofBoolean().or(false).value();
		if (disabled) {
			return true;
		}
		properties.forKey(MODE_PROPERTY).ofString().value();
		return true;
	}

}
