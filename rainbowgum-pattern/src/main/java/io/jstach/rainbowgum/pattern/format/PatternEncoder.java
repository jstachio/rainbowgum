package io.jstach.rainbowgum.pattern.format;

import java.util.function.Consumer;

import io.jstach.rainbowgum.LogConfig.Provider;
import io.jstach.rainbowgum.LogEncoder;

/**
 * Factory for pattern encoders.
 *
 * @apiNote This class is for API discoverability. Pattern Encoders do not actually extend
 * or implement this class.
 */
public final class PatternEncoder {

	/**
	 * Pattern encoder URI provider scheme.
	 */
	public static final String PATTERN_SCHEME = "pattern";

	private PatternEncoder() {
	}

	/**
	 * Creates a pattern encoder with name of the parent component and will use the
	 * properties from the provided config.
	 * @param builder pattern builder
	 * @return provider of the encoder.
	 */
	public static Provider<LogEncoder> of(Consumer<PatternEncoderBuilder> builder) {
		return (name, config) -> {
			PatternEncoderBuilder b = new PatternEncoderBuilder(name);
			builder.accept(b);
			b.fromProperties(config.properties());
			return b.build().provide(name, config);
		};
	}

	/**
	 * Pattern encoder builder.
	 * @param name name to use for property resolution.
	 * @return builder.
	 * @apiNote {@link #of(Consumer)} is easier to use at will inherit the name and set
	 * the properties.
	 */
	public PatternEncoderBuilder builder(String name) {
		return new PatternEncoderBuilder(name);
	}

}
