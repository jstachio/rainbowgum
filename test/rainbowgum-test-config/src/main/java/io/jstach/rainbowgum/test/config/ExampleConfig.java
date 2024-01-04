package io.jstach.rainbowgum.test.config;

import java.net.URI;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.ConfigObject;

public record ExampleConfig(String name, Integer count, @Nullable URI uri) {

	/**
	 * Create config.
	 * @param name parameter name.
	 * @param count parameter count.
	 * @param uri parameter uri.
	 * @return config
	 */
	@ConfigObject(name = "ExampleConfigBuilder", prefix = "logging.example.{name}.")
	public static ExampleConfig of(@ConfigObject.PrefixParameter String name, Integer count, @Nullable URI uri) {
		return new ExampleConfig(name, count, uri);
	}
}
