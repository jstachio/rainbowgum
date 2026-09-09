package io.jstach.rainbowgum.simple.props;

import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperties;

/**
 * Environment variable backed {@link LogProperties} with a configurable prefix instead of
 * the {@code "logging."} lead segment, modeled on
 * {@link LogProperties.StandardProperties#ENVIRONMENT_VARIABLES} but with a configurable
 * prefix in place of a plain <code>.</code>-to-<code>_</code> replace of the whole key.
 */
final class EnvVarProperties implements LogProperties {

	/**
	 * higher than {@link LogProperties.StandardProperties#ENVIRONMENT_VARIABLES}'s 300
	 * would matter only if both were registered together, which should not happen - kept
	 * at 300 to match the standard convention (same as MicroProfile config's env var
	 * ordinal).
	 */
	static final int ORDER = 300;

	private final String prefix;

	private final Function<String, @Nullable String> envLookup;

	EnvVarProperties(String prefix, Function<String, @Nullable String> envLookup) {
		this.prefix = prefix;
		this.envLookup = envLookup;
	}

	@Override
	public @Nullable String valueOrNull(String key) {
		return envLookup.apply(translateKey(key));
	}

	@Override
	public String description(String key) {
		return "ENV[" + translateKey(key) + "]";
	}

	@Override
	public int order() {
		return ORDER;
	}

	/*
	 * Deliberately no case-folding - see SimpleProperties.Builder#envPrefix(String)
	 * javadoc for why.
	 */
	String translateKey(String key) {
		String k = key.startsWith(LogProperties.ROOT_PREFIX) ? key.substring(LogProperties.ROOT_PREFIX.length()) : key;
		return (prefix + k).replace(".", "_");
	}

}
