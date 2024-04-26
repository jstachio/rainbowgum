package io.jstach.rainbowgum;

import io.jstach.rainbowgum.annotation.LogConfigurable;

/**
 * A marker interface for custom or generated builders.
 *
 * @param <B> builder
 * @param <O> created on build
 * @apiNote This interface is for discovery and documentation so that one can find all the
 * builders supported by Rainbow Gum.
 * @see LogConfigurable
 */
public interface LogBuilder<B, O> {

	/**
	 * Will try to convert string key values to parameters needed by the builder.
	 * @param properties log properties
	 * @return this.
	 */
	public B fromProperties(LogProperties properties);

	/**
	 * Will try to convert string key values to parameters needed by the builder.
	 * @param properties log properties.
	 * @param ref provider reference.
	 * @return this.
	 */
	default B fromProperties(LogProperties properties, LogProviderRef ref) {
		String prefix = propertyPrefix();
		var uri = ref.uri();
		var uriKey = ref.keyOrNull();
		LogProperties combined = LogProperties.of(uri, prefix, properties, uriKey);
		return fromProperties(combined);
	}

	/**
	 * The property prefix used to resolve which properties to extract from LogProperties.
	 * @return prefix which should ideally end in {@value LogProperties#SEP}.
	 * @see LogConfigurable#prefix()
	 */
	public String propertyPrefix();

}
