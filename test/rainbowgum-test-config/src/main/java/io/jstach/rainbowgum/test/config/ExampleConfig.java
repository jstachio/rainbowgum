package io.jstach.rainbowgum.test.config;

import java.net.URI;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.ConfigObject;

public record ExampleConfig(String name, Integer count, @Nullable URI uri) {

	@ConfigObject(name = "ExampleConfigBuilder", prefix = "logging.example.")
	public static ExampleConfig of(String name, Integer count, @Nullable URI uri) {
		return new ExampleConfig(name, count, uri);
	}
}
