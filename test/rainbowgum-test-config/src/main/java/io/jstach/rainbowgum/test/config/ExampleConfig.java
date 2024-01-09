package io.jstach.rainbowgum.test.config;

import java.net.URI;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.annotation.LogConfigurable;

public record ExampleConfig(String name, Integer count, @Nullable URI uri) {

	/**
	 * Create config.
	 * @param name parameter name.
	 * @param count parameter count.
	 * @param uri parameter uri.
	 * @return config
	 */
	@LogConfigurable(name = "ExampleConfigBuilder", prefix = "logging.example.{name}.")
	public static ExampleConfig of( //
			@LogConfigurable.PrefixParameter String name, //
			@LogConfigurable.DefaultParameter("DEFAULT_COUNT") Integer count, //
			@Nullable URI uri) {
		return new ExampleConfig(name, count, uri);
	}

	public static final int DEFAULT_COUNT = 8080;

}
