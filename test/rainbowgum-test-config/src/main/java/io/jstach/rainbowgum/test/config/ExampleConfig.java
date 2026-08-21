package io.jstach.rainbowgum.test.config;

import java.net.URI;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.annotation.LogConfigurable;

public record ExampleConfig(String name, Integer count, @Nullable URI uri, @Nullable List<String> tags) {

	/**
	 * Create config.
	 * @param name parameter name.
	 * @param count parameter count.
	 * @param uri parameter uri.
	 * @param tags parameter tags.
	 * @return config
	 */
	@LogConfigurable(name = "ExampleConfigBuilder", prefix = "logging.example.{name}.")
	public static ExampleConfig of( //
			@LogConfigurable.KeyParameter String name, //
			@LogConfigurable.DefaultParameter("DEFAULT_COUNT") Integer count, //
			String message, //
			@Nullable URI uri, //
			@Nullable List<String> tags) {
		return new ExampleConfig(name, count, uri, tags);
	}

	public static final int DEFAULT_COUNT = 8080;

}
