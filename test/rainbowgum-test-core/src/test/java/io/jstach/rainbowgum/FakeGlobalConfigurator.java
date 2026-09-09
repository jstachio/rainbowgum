package io.jstach.rainbowgum;

import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;

/**
 * Test-only configurator for {@link ConfigFailureTest} that deliberately does
 * <strong>not</strong> go through a generated builder the way {@link FakeEncoderBuilder}
 * does - it reads properties directly off {@link LogConfig#properties()}, the same style
 * {@code JULConfigurator} (see
 * {@code logging.jul.disable}/{@code logging.jul.level.disable} in rainbowgum-jul) uses
 * for its global on/off switches. Unlike those switches (always {@code Boolean}, which
 * can never itself fail to parse - {@link Boolean#parseBoolean(String)} just returns
 * {@code false} for anything unrecognized), this fake also has three required companion
 * properties, each read via {@link LogProperty.Result#map(LogProperty.PropertyFunction)
 * map()} (same as {@code FakeEncoderBuilder}'s {@code label}) but wired up differently so
 * each demonstrates a different way a direct (no builder) read can fail:
 * <ul>
 * <li>{@value #MODE_PROPERTY} - plain {@link LogProperty.Result#value()} after the
 * {@code map()} check, so a missing/bad value throws immediately and unwrapped - no
 * "Validation failed for ...:" collection in between.</li>
 * <li>{@value #MODE2_PROPERTY} - a hand-built
 * {@link LogProperty.Validator}/{@link LogProperty.Result#validate(LogProperty.Validator)
 * validate(Validator)} after the same kind of {@code map()} check, which gets that richer
 * "Validation failed for X:" message (naming this class) the same way a generated
 * builder's own {@code Validator} would.</li>
 * <li>{@value #MODE3_PROPERTY} - kept as a third, separately-named property purely so
 * {@link ConfigFailureTest.ConfigFailure}'s permutation matrix has a case per read style
 * to point at; its own {@code map()} check ({@link #checkMode3(String)}) is otherwise
 * identical in mechanism to {@value #MODE_PROPERTY}'s.</li>
 * </ul>
 * See {@link ConfigFailureTest.ConfigFailure} for exactly how these compare, including
 * chained/{@code ListLogProperties} variants.
 */
final class FakeGlobalConfigurator implements Configurator {

	static final String DISABLE_PROPERTY = "logging.fakeGlobal.disable";

	static final String MODE_PROPERTY = "logging.fakeGlobal.mode";

	static final String MODE2_PROPERTY = "logging.fakeGlobal.mode2";

	static final String MODE3_PROPERTY = "logging.fakeGlobal.mode3";

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		var properties = config.properties();
		boolean disabled = properties.forKey(DISABLE_PROPERTY).ofBoolean().or(false).value();
		if (disabled) {
			return true;
		}
		properties.forKey(MODE_PROPERTY).ofString().map(FakeGlobalConfigurator::checkMode).value();
		var v = LogProperty.Validator.of(FakeGlobalConfigurator.class);
		var mode2 = properties.forKey(MODE2_PROPERTY).ofString().map(FakeGlobalConfigurator::checkMode2).validate(v);
		v.validate();
		mode2.value();
		properties.forKey(MODE3_PROPERTY).ofString().map(FakeGlobalConfigurator::checkMode3).value();
		return true;
	}

	private static String checkMode(String value) {
		if (value.equals("bad")) {
			throw new IllegalArgumentException("mode must not be 'bad'");
		}
		return value;
	}

	private static String checkMode2(String value) {
		if (value.equals("bad")) {
			throw new IllegalArgumentException("mode2 must not be 'bad'");
		}
		return value;
	}

	private static String checkMode3(String value) {
		if (value.equals("bad")) {
			throw new IllegalArgumentException("mode3 must not be 'bad'");
		}
		return value;
	}

}
